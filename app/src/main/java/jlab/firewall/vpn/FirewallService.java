package jlab.firewall.vpn;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.collection.ArrayMap;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.Process;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jlab.firewall.R;
import jlab.firewall.activity.MainActivity;
import jlab.firewall.db.ApplicationDbManager;
import jlab.firewall.db.ApplicationDetails;
import lecho.lib.hellocharts.model.PointValue;
import static java.util.Collections.binarySearch;
import static java.util.Collections.sort;
import static jlab.firewall.activity.MainActivity.SELECTED_TAB_KEY;
import static jlab.firewall.activity.MainActivity.SHOW_NOTIFIED_APPS_REQUEST_CODE;
import static jlab.firewall.activity.MainActivity.VPN_REQUEST_CODE;
import static jlab.firewall.vpn.Utils.getPackagesInternetPermission;
import static jlab.firewall.vpn.Utils.getSizeString;
import static jlab.firewall.vpn.Utils.isBlocked;
import static jlab.firewall.vpn.Utils.showCountBadger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FirewallService extends VpnService {

    public static ArrayList<Integer> mapPackageAllowed = new ArrayList<>();
    public static ArrayList<Integer> mapPackageNotified = new ArrayList<>();
    public static ArrayList<Integer> mapPackageInteract = new ArrayList<>();
    public static ArrayList<PointValue> trafficDataUpSpeedPoints = new ArrayList<>();
    public static ArrayList<PointValue> trafficDataDownSpeedPoints = new ArrayList<>();
    public static final String TAG = FirewallService.class.getSimpleName();
    public static final String VPN_ROUTE = "0.0.0.0",
            APP_DETAILS_NOTIFICATION_KEY = "APP_DETAILS_NOTIFICATION_KEY",
            APP_DETAILS_NAME_NOTIFICATION_KEY = "APP_DETAILS_NAME_NOTIFICATION_KEY",
            REFRESH_COUNT_NOTIFIED_APPS_ACTION = "jlab.action.REFRESH_COUNT_NOTIFIED_APPS",
            REFRESH_TRAFFIC_DATA = "jlab.action.REFRESH_TRAFFIC_DATA",
            START_VPN_ACTION = "jlab.action.START_VPN",
            RETRY_START_VPN_ACTION = "jlab.action.RETRY_START_VPN",
            STARTED_VPN_ACTION = "jlab.action.STARTED_VPN_ACTION",
            STOPPED_VPN_ACTION = "jlab.action.STOPPED_VPN_ACTION",
            STOP_VPN_ACTION = "jlab.action.STOP_VPN_ACTION",
            NOT_PREPARED_VPN_ACTION = "jlab.action.NOT_PREPARED_VPN_ACTION",
            CHANGE_STATUS_FLOATING_MONITOR_SPPED_ACTION =
                    "jlab.action.CHANGE_STATUS_FLOATING_MONITOR_SPPED_ACTION",
            SHOW_FLOATING_SPEED_MONITOR_KEY = "SHOW_FLOATING_MONITOR_SPEED_KEY",
            X_KEY = "X_KEY",
            UP_BYTES_IN_START_KEY = "UP_BYTES_IN_START_KEY",
            DOWN_BYTES_IN_START_KEY = "DOWN_BYTES_IN_START_KEY",
            UP_BYTES_TOTAL_KEY = "UP_BYTES_TOTAL_KEY",
            DOWN_BYTES_TOTAL_KEY = "DOWN_BYTES_TOTAL_KEY",
            TRAFFIC_DATA_UP_SPEED_POINTS_KEY = "TRAFFIC_DATA_UP_SPEED_POINTS_KEY",
            TRAFFIC_DATA_DOWN_SPEED_POINTS_KEY = "TRAFFIC_DATA_DOWN_SPEED_POINTS_KEY";
    public static final int myUid = Process.myUid();
    public static int notificationMessageUid;
    public static Message notificationMessage;
    public static Semaphore mutexNotificator = new Semaphore(1),
            mutexLoadAppData = new Semaphore(1),
            mutexRefreshTxRxBytesForAllApps = new Semaphore(1);
    private static Map<String, Integer> mapAddress = new ArrayMap<>();
    private static final int REQUEST_INTERNET_NOTIFICATION = 9200,
            REFRESH_TRAFFIC_DATA_FLOATING_VIEW = 9201, NOTIFY_INTERNET_REQUEST_ACCESS = 9203,
            RUNNING_NOTIFICATION = 9204, MAX_COUNT_POINTS = 100;
    private static NotificationManager notMgr;
    private static ApplicationDbManager dbManager;
    public static final AtomicLong downByteTotal = new AtomicLong(0), upByteTotal = new AtomicLong(0),
            downByteSpeed = new AtomicLong(0), upByteSpeed = new AtomicLong(0);
    private long downBytesInStart, upBytesInStart, x;
    private static boolean isRunning, isWaiting;
    private ParcelFileDescriptor vpnInterface = null;
    private ExecutorService executorService = Executors.newFixedThreadPool(100);
    private View floatingTrafficDataView;
    private TextView tvFloatingTrafficSpeed, tvFloatingTrafficTotal;
    private WindowManager windowMgr;
    private PackageManager packageManager;
    private boolean refreshTrafficDataAux = false;
    private String trafficTotalText = "↑0B↓0B", trafficSpeedText = "↑0Bps↓0Bps", CHANNEL_ID;
    private BroadcastReceiver EventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent != null && intent.getAction() != null)
                    switch (intent.getAction()) {
                        case CHANGE_STATUS_FLOATING_MONITOR_SPPED_ACTION:
                            boolean show = intent.getBooleanExtra(SHOW_FLOATING_SPEED_MONITOR_KEY, false);
                            if (!show && floatingTrafficDataView != null)
                                windowMgr.removeViewImmediate(floatingTrafficDataView);
                            else if (show && floatingTrafficDataView != null)
                                windowMgr.addView(floatingTrafficDataView, floatingTrafficDataViewParams);
                            break;
                        case START_VPN_ACTION:
                            startServiceNow(false);
                            break;
                        case STOP_VPN_ACTION:
                            try {
                                stopServiceNow(false);
                                stopSelf();
                            } catch (Exception | OutOfMemoryError ignored) { }
                            break;
                        default:
                            break;
                    }
            } catch (Exception ignored) { }
        }
    };
    private static int lastUidNotified, lastCountNotified;
    private Handler handler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH_TRAFFIC_DATA_FLOATING_VIEW:
                    if(preferences.getBoolean(SHOW_FLOATING_SPEED_MONITOR_KEY, false)) {
                        if (tvFloatingTrafficTotal != null)
                            tvFloatingTrafficTotal.setText(trafficTotalText);
                        if (tvFloatingTrafficSpeed != null)
                            tvFloatingTrafficSpeed.setText(trafficSpeedText);
                    }
                    handler.removeMessages(REFRESH_TRAFFIC_DATA_FLOATING_VIEW);
                    return true;
                case NOTIFY_INTERNET_REQUEST_ACCESS:
                    ApplicationDetails notifiedApp = msg.getData().getParcelable(APP_DETAILS_NOTIFICATION_KEY);
                    int countNotified = mapPackageNotified.size();
                    if (notifiedApp != null && (lastUidNotified != notifiedApp.getUid()
                            || countNotified != lastCountNotified)) {
                        lastUidNotified = notifiedApp.getUid();
                        lastCountNotified = countNotified;
                        String name = msg.getData().getString(APP_DETAILS_NAME_NOTIFICATION_KEY);

                        Notification notification = new NotificationCompat.Builder(getBaseContext(), CHANNEL_ID)
                                .setContentText(getBaseContext().getString(R.string.apps_req_internet_access))
                                .setContentTitle(name)
                                .setAutoCancel(true)
                                .setSmallIcon(R.drawable.img_req_internet_not)
                                .setNumber(countNotified)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setDefaults(NotificationCompat.DEFAULT_ALL)
                                .setLargeIcon(Utils.getIconForApp(notifiedApp.getPrincipalPackName(),
                                        FirewallService.this))
                                .setContentIntent(getPendingIntentNotificationClicked(1))
                                .setFullScreenIntent(getPendingIntentNotificationClicked(1),
                                        true)
                                .build();
                        notMgr.notify(REQUEST_INTERNET_NOTIFICATION, notification);
                        showCountBadger(getBaseContext(), notification, countNotified);
                    }

                    handler.removeMessages(NOTIFY_INTERNET_REQUEST_ACCESS);
                    LocalBroadcastManager.getInstance(getBaseContext())
                            .sendBroadcast(new Intent(REFRESH_COUNT_NOTIFIED_APPS_ACTION));
                    return true;
                default:
                    break;
            }
            return false;
        }
    });

    static {
        try {
            System.loadLibrary("netguard");
        } catch (Exception|Error ignored) { }
    }

    private long jni_context;
    private SharedPreferences preferences;
    private WindowManager.LayoutParams floatingTrafficDataViewParams;

    private native long jni_init(int sdk);

    private native void jni_start(long context, int loglevel);

    private native void jni_run(long context, int tun, boolean fwd53, int rcode);

    private native void jni_stop(long context);

    private native void jni_clear(long context);

    private void loadAddress () {
        mapAddress.clear();
        mapAddress.put("10.0.0.0", 8);
        mapAddress.put("172.16.0.0", 12);
        mapAddress.put("192.168.0.0", 16);
    }

    // Called from native code
    private void nativeExit(String reason) {

    }

    // Called from native code
    private void nativeError(int error, String message) {

    }

    // Called from native code
    private void logPacket(Packet packet) {

    }

    // Called from native code
    private void dnsResolved(ResourceRecord rr) {

    }

    // Called from native code
    private boolean isDomainBlocked(String name) {
        return false;
    }

    // Called from native code
    @TargetApi(Build.VERSION_CODES.Q)
    private int getUidQ(int version, int protocol, String saddr, int sport, String daddr, int dport) {
        if (protocol != Protocols.TCP && protocol != Protocols.UDP)
            return Process.INVALID_UID;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null)
            return Process.INVALID_UID;

        InetSocketAddress local = new InetSocketAddress(saddr, sport);
        InetSocketAddress remote = new InetSocketAddress(daddr, dport);

        return cm.getConnectionOwnerUid(protocol, local, remote);
    }

    private boolean isSupported(int protocol) {
        return (protocol == Protocols.ICMPv4 ||
                protocol == Protocols.ICMPv6 ||
                protocol == Protocols.TCP ||
                protocol == Protocols.UDP);
    }

    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    // Called from native code
    private Allowed isAddressAllowed(Packet packet) {
        lock.readLock().lock();
        packet.allowed = !isBlockedUid(packet.uid);
        lock.readLock().unlock();
        return packet.allowed ? new Allowed() : null;
    }

    // Called from native code
    private void accountUsage(Usage usage) {
        //TODO: Save Tx and Rx bytes
        /*try {
            mutexRefreshTxRxBytesForAllApps.acquire();
            ApplicationDetails item = dbManager.getApplicationForId(usage.Uid);
            if (item != null) {
                long txBytes = item.getTxBytes(), rxBytes = item.getRxBytes();
                item.setTxBytes(txBytes + usage.Sent);
                item.setRxBytes(rxBytes + usage.Received);
                if (txBytes != item.getTxBytes() || rxBytes != item.getRxBytes()) {
                    dbManager.updateApplicationData(item.getUid(), item);
                }
            }
        } catch (Exception | OutOfMemoryError e) {
            System.gc();
        } finally {
            mutexRefreshTxRxBytesForAllApps.release();
        }*/
    }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(String.format("%s_preferences", getPackageName()), Context.MODE_PRIVATE);
        loadAddress();
        CHANNEL_ID = String.format("%s.%s", getString(R.string.app_name),
                getString(R.string.app_list_request));
        packageManager = getBaseContext().getPackageManager();
        dbManager = new ApplicationDbManager(getBaseContext());
        notMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notified), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                channel.setAllowBubbles(true);
            notMgr.createNotificationChannel(channel);
        }
        IntentFilter intentFilter = new IntentFilter(START_VPN_ACTION);
        intentFilter.addAction(STOP_VPN_ACTION);
        intentFilter.addAction(CHANGE_STATUS_FLOATING_MONITOR_SPPED_ACTION);
        LocalBroadcastManager.getInstance(getBaseContext()).registerReceiver(EventReceiver,
                intentFilter);
    }

    @SuppressLint("ForegroundServiceType")
    public void startServiceNow(boolean retry) {
        if (!isRunning) {
            isWaiting = !setupVPN();
            if (!isWaiting)
                try {
                    isRunning = true;
                    executorService.submit(() -> {
                        try {
                            loadAppData(getBaseContext());
                            executorService.submit(new VPNRunnable(vpnInterface, retry));
                        } catch (Exception | OutOfMemoryError ignored) {
                            stopServiceNow(true);
                        }
                    });

                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(STARTED_VPN_ACTION));
                    loadTrafficDataView();
                    startNotificatorThread();
                    startForeground(RUNNING_NOTIFICATION,
                            new NotificationCompat.Builder(getBaseContext(), CHANNEL_ID)
                                    .setContentText(getBaseContext().getString(R.string.started_vpn_service))
                                    .setContentTitle(getString(R.string.app_name))
                                    .setAutoCancel(false)
                                    .setSmallIcon(R.drawable.img_running_not)
                                    .setLargeIcon(BitmapFactory.decodeResource(getResources(),
                                            R.drawable.icon))
                                    .setContentIntent(getPendingIntentNotificationClicked(0)).build());
                }  catch (Exception | OutOfMemoryError ignored) {
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(STOPPED_VPN_ACTION));
                    cleanup();
                }
            else
                LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(new Intent(NOT_PREPARED_VPN_ACTION));
        }
    }

    private ArrayList<String> getStringPoints (ArrayList<PointValue> points) {
        ArrayList<String> result = new ArrayList<>();
        for(PointValue point : points) {
            result.add(String.format("{x:%s, y:%s}", point.getX(), point.getY()));
        }
        return result;
    }

    private void stopServiceNow(boolean retry) {
        try {
            cleanup();
            isRunning = false;
            if (retry) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putLong(UP_BYTES_IN_START_KEY, upBytesInStart);
                editor.putLong(DOWN_BYTES_IN_START_KEY, downBytesInStart);
                editor.putLong(UP_BYTES_TOTAL_KEY, upByteTotal.get());
                editor.putLong(DOWN_BYTES_TOTAL_KEY, downByteTotal.get());
                editor.putString(TRAFFIC_DATA_UP_SPEED_POINTS_KEY, String.format("[%s]", TextUtils.join(",",
                        getStringPoints(trafficDataUpSpeedPoints))));
                editor.putString(TRAFFIC_DATA_DOWN_SPEED_POINTS_KEY, String.format("[%s]", TextUtils.join(",",
                        getStringPoints(trafficDataDownSpeedPoints))));
                editor.putLong(X_KEY, x);
                editor.apply();
                editor.commit();

                sendBroadcast(new Intent(RETRY_START_VPN_ACTION));
            }
            else {
                LocalBroadcastManager.getInstance(getBaseContext())
                        .unregisterReceiver(EventReceiver);
                LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(new Intent(STOPPED_VPN_ACTION));
                try {
                    if (floatingTrafficDataView != null)
                        windowMgr.removeViewImmediate(floatingTrafficDataView);
                } catch (Exception | OutOfMemoryError ignored) { }
                NetConnections.freeCache();
            }
        }
        catch (Exception | OutOfMemoryError ignored) { }
        finally {
            if (notMgr != null)
                notMgr.cancel(RUNNING_NOTIFICATION);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopServiceNow(true);
    }

    private void startNotificatorThread() {
        executorService.submit(() -> {
            while (!Thread.interrupted() && isRunning()) {
                try {
                    Thread.sleep(1000);
                    mutexNotificator.acquire();
                    if (notificationMessage != null) {
                        handler.sendMessage(notificationMessage);
                        notificationMessage = null;
                        notificationMessageUid = -1;
                    }
                }  catch (Exception | OutOfMemoryError ignored) {
                    System.gc();
                } finally {
                    mutexNotificator.release();
                }
            }
        });
    }

    @SuppressLint({"InflateParams", "RtlHardcoded"})
    private void loadTrafficDataView() {
        try {
            if (!Thread.interrupted() && isRunning()) {
                floatingTrafficDataView = LayoutInflater.from(this)
                        .inflate(R.layout.floating_traffic_data, null);
                tvFloatingTrafficSpeed = floatingTrafficDataView.findViewById(R.id.tvTrafficDataSpeed);
                tvFloatingTrafficTotal = floatingTrafficDataView.findViewById(R.id.tvTrafficDataTotal);
                final LinearLayout llFloatingTrafficTotal = floatingTrafficDataView.findViewById(R.id.llTrafficDataTotal);
                floatingTrafficDataViewParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSPARENT);
                floatingTrafficDataViewParams.gravity = Gravity.TOP | Gravity.LEFT;
                windowMgr = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (windowMgr != null) {
                    if (preferences.getBoolean(SHOW_FLOATING_SPEED_MONITOR_KEY, false))
                        windowMgr.addView(floatingTrafficDataView, floatingTrafficDataViewParams);
                    floatingTrafficDataView.setOnTouchListener(new View.OnTouchListener() {
                        private int initialX, initialY;
                        float initialTouchX, initialTouchY;
                        boolean totalViewWaitForGone = false, cancelTotalViewGone = false;

                        @SuppressLint("ClickableViewAccessibility")
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            switch (event.getAction()) {
                                case MotionEvent.ACTION_DOWN:
                                    initialX = floatingTrafficDataViewParams.x;
                                    initialY = floatingTrafficDataViewParams.y;
                                    initialTouchX = event.getRawX();
                                    initialTouchY = event.getRawY();
                                    llFloatingTrafficTotal.setVisibility(View.VISIBLE);
                                    cancelTotalViewGone = true;
                                    return true;
                                case MotionEvent.ACTION_MOVE:
                                    floatingTrafficDataViewParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                                    floatingTrafficDataViewParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                                    try {
                                        windowMgr.updateViewLayout(floatingTrafficDataView, floatingTrafficDataViewParams);
                                    }
                                    catch (Exception ignored) { }
                                    return true;
                                case MotionEvent.ACTION_UP:
                                    cancelTotalViewGone = false;
                                    if (!totalViewWaitForGone) {
                                        totalViewWaitForGone = true;
                                        llFloatingTrafficTotal.postDelayed(() -> {
                                            if (!cancelTotalViewGone)
                                                llFloatingTrafficTotal.setVisibility(View.GONE);
                                            totalViewWaitForGone = false;
                                        }, 5000);
                                    }
                                    return true;
                                default:
                                    break;
                            }
                            return false;
                        }
                    });
                }
            }
        } catch (Exception | OutOfMemoryError ignored) { }
    }

    private long getDownBytesTotalForService () {
        return TrafficStats.getUidRxBytes(myUid);
    }

    private long getUpBytesTotalForService () {
        return TrafficStats.getUidTxBytes(myUid);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Utils.freeMemory();
    }

    private boolean setupVPN() {
        if (vpnInterface == null) {
            try {
                Intent configure = new Intent(this, MainActivity.class);
                PendingIntent pi = PendingIntent.getService(this, VPN_REQUEST_CODE, configure,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                ? PendingIntent.FLAG_IMMUTABLE
                                : PendingIntent.FLAG_MUTABLE);
                Builder builder = addAllInetAddressToBuilder(new Builder())
                        .setConfigureIntent(pi)
                        .addRoute(VPN_ROUTE, 0)
                        .addRoute("2000::", 3)
                        .addDisallowedApplication(getPackageName())
                        .setSession(getPackageName());
                vpnInterface = builder.establish();
                return vpnInterface != null;
            }  catch (Exception | OutOfMemoryError ignored) { }
        }
        return false;
    }

    private Builder addAllInetAddressToBuilder(Builder builder)
            throws NullPointerException, ClassCastException {
        for (String address : mapAddress.keySet()) {
            Integer prefix = mapAddress.get(address);
            if (prefix != null)
                builder.addAddress(address, prefix);
        }
        return builder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startServiceNow(intent.getAction() != null && intent.getAction().equals(RETRY_START_VPN_ACTION));
        return START_STICKY;
    }

    public static boolean isRunning() {
        return isRunning;
    }

    public static boolean isWaiting () {
        return isWaiting;
    }

    private void cleanup() {
        closeResources(vpnInterface);
        executorService.shutdown();
    }

    private static void closeResources(Closeable... resources) {
        for (Closeable resource : resources) {
            try {
                resource.close();
            }  catch (Exception | OutOfMemoryError e) {
                // Ignore
            }
        }
    }

    public static void cancelNotification (int uid) {
        if (notMgr != null && lastUidNotified == uid)
            notMgr.cancel(REQUEST_INTERNET_NOTIFICATION);
    }

    public static void loadAppData (Context context) {
        try {
            mutexLoadAppData.acquire();
            mapPackageNotified = new ArrayList<>();
            mapPackageAllowed = new ArrayList<>();
            mapPackageInteract = new ArrayList<>();
            ArrayList<Integer> allUid = new ArrayList<>();
            List<ApplicationDetails> allApps = getPackagesInternetPermission(context, allUid);
            if (dbManager == null) {
                dbManager = new ApplicationDbManager(context);
            }
            List<ApplicationDetails> appsDetails = dbManager.getAllAppDetails(null);
            int countUid = allUid.size();
            for (ApplicationDetails app : appsDetails) {
                int indexSearch = binarySearch(allUid, app.getUid());
                if (indexSearch < 0 || indexSearch >= countUid)
                    dbManager.deleteApplicationData(app.getUid());
                else {
                    allUid.remove(indexSearch);
                    allApps.remove(indexSearch);

                    if (app.hasInternet() && !mapPackageAllowed.contains(app.getUid()))
                        mapPackageAllowed.add(app.getUid());
                    if (app.notified() && !mapPackageNotified.contains(app.getUid()))
                        mapPackageNotified.add(app.getUid());
                    if (app.interact() && !mapPackageInteract.contains(app.getUid()))
                        mapPackageInteract.add(app.getUid());
                }
            }
            dbManager.addApps(allApps);
            sort(mapPackageAllowed);
            sort(mapPackageNotified);
            sort(mapPackageInteract);
        }
        catch (Exception | OutOfMemoryError ignored) { }
        finally {
            mutexLoadAppData.release();
        }
    }

    private boolean isBlockedUid(int uid) {
        if (uid == myUid)
            return false;
        if (uid > 0) {
            boolean blocked = isBlocked(uid);
            if(blocked)
                notifyUid(uid);
            return blocked;
        }
        return uid != 0;
    }

    private void notifyUid(final int uid) {
        executorService.submit(() -> {
            try {
                mutexNotificator.acquire();
                if (!Utils.isInteract(uid)) {
                    ApplicationDetails appDetails = dbManager.getApplicationForId(uid);
                    if (appDetails != null && appDetails.getPrincipalPackName() != null) {
                        appDetails.setNotified(true);
                        dbManager.updateApplicationData(appDetails.getUid(), appDetails);

                        ApplicationInfo appInfo = null;
                        try {
                            appInfo = packageManager.getApplicationInfo(appDetails
                                    .getPrincipalPackName(), PackageManager.GET_META_DATA);
                        }
                        catch (Exception | OutOfMemoryError ignored) { }
                        notificationMessage = new Message();
                        notificationMessageUid = uid;
                        notificationMessage.what = NOTIFY_INTERNET_REQUEST_ACCESS;
                        Bundle bundle = new Bundle();
                        bundle.putString(APP_DETAILS_NAME_NOTIFICATION_KEY, appInfo != null
                                ? packageManager.getApplicationLabel(appInfo).toString()
                                : appDetails.getPrincipalPackName());
                        bundle.putParcelable(APP_DETAILS_NOTIFICATION_KEY, appDetails);
                        notificationMessage.setData(bundle);
                    }
                }
            }
            catch (Exception | OutOfMemoryError ignored) { }
            finally {
                mutexNotificator.release();
            }
        });
    }

    private PendingIntent getPendingIntentNotificationClicked(int selectedTab) {
        Intent intent = new Intent(getBaseContext(), MainActivity.class);
        intent.putExtra(SELECTED_TAB_KEY, selectedTab);
        return PendingIntent.getActivity(getBaseContext(), SHOW_NOTIFIED_APPS_REQUEST_CODE
                , intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_MUTABLE));
    }

    private final Runnable refreshTrafficData = new Runnable() {
        @Override
        public void run() {
            boolean refreshSpeed = false;
            long lastUpByteTotal = upByteTotal.get(),
                    lastDownByteTotal = downByteTotal.get();
            while (!Thread.interrupted() && isRunning()) {
                try {
                    Thread.sleep(500);
                    if (Thread.interrupted() || !isRunning()) {
                        handler.removeMessages(REFRESH_TRAFFIC_DATA_FLOATING_VIEW);
                        handler.removeMessages(NOTIFY_INTERNET_REQUEST_ACCESS);
                        break;
                    }
                    if (refreshSpeed) {
                        upByteSpeed.set(upByteTotal.get() - lastUpByteTotal);
                        lastUpByteTotal = upByteTotal.get();
                        downByteSpeed.set(downByteTotal.get() - lastDownByteTotal);
                        lastDownByteTotal = downByteTotal.get();

                        if (trafficDataDownSpeedPoints.size() >= MAX_COUNT_POINTS) {
                            trafficDataDownSpeedPoints.remove(0);
                            trafficDataUpSpeedPoints.remove(0);
                        }

                        trafficDataUpSpeedPoints.add(new PointValue(x, upByteSpeed.get()));
                        trafficDataDownSpeedPoints.add(new PointValue(x++, downByteSpeed.get()));

                        if (refreshTrafficDataAux)
                            LocalBroadcastManager.getInstance(getBaseContext())
                                    .sendBroadcast(new Intent(REFRESH_TRAFFIC_DATA));

                        refreshTrafficDataAux = !refreshTrafficDataAux;
                    }

                    downByteTotal.set(getDownBytesTotalForService() - downBytesInStart);
                    upByteTotal.set(getUpBytesTotalForService() - upBytesInStart);
                    trafficTotalText = String.format("↑%s↓%s",
                            getSizeString(upByteTotal.get(), 1), getSizeString(downByteTotal.get(), 1));
                    trafficSpeedText = String.format("↑%sps↓%sps",
                            getSizeString(upByteSpeed.get(), 0), getSizeString(downByteSpeed.get(), 0));
                    handler.sendEmptyMessage(REFRESH_TRAFFIC_DATA_FLOATING_VIEW);
                    refreshSpeed = !refreshSpeed;
                }  catch (Exception | OutOfMemoryError ignored) {
                    System.gc();
                }
            }
        }
    };

    private class VPNRunnable implements Runnable {
        private final String TAG = VPNRunnable.class.getSimpleName();

        private ParcelFileDescriptor vpnFileDescriptor;

        private boolean retry;

        public VPNRunnable(ParcelFileDescriptor vpnFileDescriptor, boolean retry) {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.retry = retry;
        }

        @Override
        public void run() {

            if (retry) {
                preferences = getSharedPreferences(String.format("%s_preferences", getPackageName()), Context.MODE_PRIVATE);
                try {
                    JSONArray jsonArray = new JSONArray(preferences.getString(TRAFFIC_DATA_UP_SPEED_POINTS_KEY, "[]"));
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonCurrent = new JSONObject(jsonArray.get(i).toString());
                        trafficDataUpSpeedPoints.add(new PointValue((float) jsonCurrent.getDouble("x"), (float) jsonCurrent.getDouble("y")));
                    }

                    jsonArray = new JSONArray(preferences.getString(TRAFFIC_DATA_DOWN_SPEED_POINTS_KEY, "[]"));
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonCurrent = new JSONObject(jsonArray.get(i).toString());
                        trafficDataDownSpeedPoints.add(new PointValue((float) jsonCurrent.getDouble("x"), (float) jsonCurrent.getDouble("y")));
                    }
                }
                catch (JSONException ignored) { }
                finally {
                    upBytesInStart = preferences.getLong(UP_BYTES_IN_START_KEY, 0);
                    downBytesInStart = preferences.getLong(DOWN_BYTES_IN_START_KEY, 0);
                    upByteTotal.set(preferences.getLong(UP_BYTES_TOTAL_KEY, 0));
                    downByteTotal.set(preferences.getLong(DOWN_BYTES_TOTAL_KEY, 0));
                    x = preferences.getInt(X_KEY, 0);
                }
            }
            else {
                upBytesInStart = getUpBytesTotalForService();
                downBytesInStart = getDownBytesTotalForService();
                upByteTotal.set(0);
                downByteTotal.set(0);
                upByteSpeed.set(0);
                downByteSpeed.set(0);
                trafficDataDownSpeedPoints.clear();
                trafficDataUpSpeedPoints.clear();
                x = 0;
            }

            NetConnections.freeCache();
            jni_context = jni_init(Build.VERSION.SDK_INT);
            jni_start(jni_context, Log.ASSERT);

            executorService.submit(() -> {
                executorService.submit(refreshTrafficData);
                jni_run(jni_context, vpnFileDescriptor.getFd(), true, 3);
            });
        }
    }
}