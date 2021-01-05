package jlab.firewall.vpn;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.TrafficStats;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
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
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import jlab.firewall.R;
import jlab.firewall.activity.MainActivity;
import jlab.firewall.db.ApplicationDbManager;
import jlab.firewall.db.ApplicationDetails;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.PointValue;

import static java.util.Collections.binarySearch;
import static java.util.Collections.sort;
import static jlab.firewall.activity.MainActivity.SELECTED_TAB_KEY;
import static jlab.firewall.activity.MainActivity.SHOW_NOTIFIED_APPS_REQUEST_CODE;
import static jlab.firewall.activity.MainActivity.VPN_REQUEST_CODE;
import static jlab.firewall.vpn.Utils.getPackagesInternetPermission;
import static jlab.firewall.vpn.Utils.getSizeString;
import static jlab.firewall.vpn.Utils.isBlocked;

public class FirewallService extends VpnService {
    public static ArrayList<Integer> mapPackageAllowed = new ArrayList<>();
    public static ArrayList<Integer> mapPackageNotified = new ArrayList<>();
    public static ArrayList<Integer> mapPackageInteract = new ArrayList<>();
    public static ArrayList<Integer> allUid = new ArrayList<>();
    public static ArrayList<PointValue> trafficDataUpSpeedPoints = new ArrayList<>();
    public static ArrayList<PointValue> trafficDataDownSpeedPoints = new ArrayList<>();
    public static final String TAG = FirewallService.class.getSimpleName();
    public static final String VPN_ROUTE = "0.0.0.0",
            APP_DETAILS_NOTIFICATION_KEY = "APP_DETAILS_NOTIFICATION_KEY",
            APP_DETAILS_NAME_NOTIFICATION_KEY = "APP_DETAILS_NAME_NOTIFICATION_KEY",
            DOWN_BYTE_SPEED_KEY = "DOWN_BYTE_SPEED_KEY", UP_BYTE_SPEED_KEY = "UP_BYTE_SPEED_KEY",
            REFRESH_COUNT_NOTIFIED_APPS_ACTION = "jlab.action.SHOW_NOTIFICATION",
            REFRESH_TRAFFIC_DATA = "jlab.action.REFRESH_TRAFFIC_DATA";
    private static final int REQUEST_INTERNET_NOTIFICATION = 9200,
            REFRESH_TRAFFIC_DATA_FLOATING_VIEW = 9201, NOTIFY_INTERNET_REQUEST_ACCESS = 9403,
            MAX_COUNT_POINTS = 100, myUid = Process.myUid();
    private static NotificationManager notMgr;
    private ApplicationDbManager appMgr;
    public static AtomicLong downByteTotal = new AtomicLong(0), upByteTotal = new AtomicLong(0),
        downByteSpeed = new AtomicLong(0), upByteSpeed = new AtomicLong(0);
    private long downBytesInStart, upBytesInStart, x;
    private static IPostRunningListener postRunning = new IPostRunningListener() {
        @Override
        public void run(boolean running) {

        }
    };
    private static boolean isRunning = false;
    private ParcelFileDescriptor vpnInterface = null;
    private BlockingQueue<Packet> deviceToNetworkUDPQueue;
    private BlockingQueue<Packet> deviceToNetworkTCPQueue;
    private BlockingQueue<ByteBuffer> networkToDeviceQueue;
    private ExecutorService executorService;
    private View floatingTrafficDataView;
    private TextView tvFloatingTrafficSpeed, tvFloatingTrafficTotal;
    private WindowManager windowMgr;
    private PackageManager packageManager;
    private Semaphore semaphoreNotificator = new Semaphore(1);
    private boolean refreshTrafficDataAux = false;
    private String trafficTotalText = "↑0B↓0B", trafficSpeedText = "↑0Bps↓0Bps";
    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH_TRAFFIC_DATA_FLOATING_VIEW:
                    if (tvFloatingTrafficTotal != null)
                        tvFloatingTrafficTotal.setText(trafficTotalText);
                    if (tvFloatingTrafficSpeed != null)
                        tvFloatingTrafficSpeed.setText(trafficSpeedText);
                    handler.removeMessages(REFRESH_TRAFFIC_DATA_FLOATING_VIEW);
                    return true;
                case NOTIFY_INTERNET_REQUEST_ACCESS:
                    ApplicationDetails app = msg.getData().getParcelable(APP_DETAILS_NOTIFICATION_KEY);
                    String name = msg.getData().getString(APP_DETAILS_NAME_NOTIFICATION_KEY);
                    notMgr.notify(REQUEST_INTERNET_NOTIFICATION, new NotificationCompat.Builder(getBaseContext())
                            .setContentText(getBaseContext().getString(R.string.apps_req_internet_access))
                            .setContentTitle(name)
                            .setAutoCancel(true)
                            .setSmallIcon(R.drawable.img_not)
                            .setNumber(mapPackageNotified.size())
                            .setColor(getResources().getColor(R.color.gray))
                            .setLargeIcon(Utils.getIconForApp(app.getPrincipalPackName(),
                                    getBaseContext()))
                            .setContentIntent(getPendintIntent())
                            .build());
                    handler.removeMessages(NOTIFY_INTERNET_REQUEST_ACCESS);
                    LocalBroadcastManager.getInstance(getBaseContext())
                            .sendBroadcast(new Intent(REFRESH_COUNT_NOTIFIED_APPS_ACTION));
                    return true;
            }
            return false;
        }
    });

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = false;
        if (setupVPN())
            try {
                appMgr = new ApplicationDbManager(this);
                packageManager = getBaseContext().getPackageManager();
                notMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                deviceToNetworkUDPQueue = new ArrayBlockingQueue<>(1000);
                deviceToNetworkTCPQueue = new ArrayBlockingQueue<>(1000);
                networkToDeviceQueue = new ArrayBlockingQueue<>(1000);

                executorService = Executors.newFixedThreadPool(10);
                executorService.submit(new UdpHandler(deviceToNetworkUDPQueue, networkToDeviceQueue, this));
                executorService.submit(new TcpHandler(deviceToNetworkTCPQueue, networkToDeviceQueue, this));
                executorService.submit(new VPNRunnable(vpnInterface.getFileDescriptor(),
                        deviceToNetworkUDPQueue, deviceToNetworkTCPQueue, networkToDeviceQueue));
                isRunning = true;
                FirewallService.postRunning.run(true);
                loadTrafficDataView();
                Log.i(TAG, "Started");
            } catch (Exception e) {
                Log.e(TAG, "Error starting service", e);
                FirewallService.postRunning.run(false);
                cleanup();
            }
        else
            FirewallService.postRunning.run(false);
    }

    private void loadTrafficDataView() {
        try {
            if (!Thread.interrupted() && isRunning()) {
                floatingTrafficDataView = LayoutInflater.from(this)
                        .inflate(R.layout.floating_traffic_data_layout, null);
                tvFloatingTrafficSpeed = floatingTrafficDataView.findViewById(R.id.tvTrafficDataSpeed);
                tvFloatingTrafficTotal = floatingTrafficDataView.findViewById(R.id.tvTrafficDataTotal);
                final LinearLayout llFloatingTrafficTotal = floatingTrafficDataView.findViewById(R.id.llTrafficDataTotal);
                final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSPARENT);
                params.gravity = Gravity.TOP | Gravity.LEFT;
                windowMgr = (WindowManager) getSystemService(WINDOW_SERVICE);
                windowMgr.addView(floatingTrafficDataView, params);
                floatingTrafficDataView.setOnTouchListener(new View.OnTouchListener() {
                    private int initialX, initialY;
                    float initialTouchX, initialTouchY;
                    boolean totalViewWaitForGone = false, cancelTotalViewGone = false;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                initialX = params.x;
                                initialY = params.y;
                                initialTouchX = event.getRawX();
                                initialTouchY = event.getRawY();
                                llFloatingTrafficTotal.setVisibility(View.VISIBLE);
                                cancelTotalViewGone = true;
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                params.x = initialX + (int) (event.getRawX() - initialTouchX);
                                params.y = initialY + (int) (event.getRawY() - initialTouchY);
                                windowMgr.updateViewLayout(floatingTrafficDataView, params);
                                return true;
                            case MotionEvent.ACTION_UP:
                                cancelTotalViewGone = false;
                                if (!totalViewWaitForGone) {
                                    totalViewWaitForGone = true;
                                    llFloatingTrafficTotal.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!cancelTotalViewGone)
                                                llFloatingTrafficTotal.setVisibility(View.GONE);
                                            totalViewWaitForGone = false;
                                        }
                                    }, 5000);
                                }
                                return true;
                        }
                        return false;
                    }
                });
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    private long getDownBytesTotalForService () {
        return TrafficStats.getUidRxBytes(myUid);
    }

    private long getUpBytesTotalForService () {
        return TrafficStats.getUidTxBytes(myUid);
    }

    public static void setPostRunning(IPostRunningListener postRunning) {
        FirewallService.postRunning = postRunning;
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
                PendingIntent pi = PendingIntent.getActivity(this, VPN_REQUEST_CODE, configure, 0);
                Builder builder = addAllInetAddressToBuilder(new Builder())
                        .addRoute(VPN_ROUTE, 0)
                        .setConfigureIntent(pi)
                        .setSession(getPackageName());
                vpnInterface = builder.establish();
                return vpnInterface != null;
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
        }
        return false;
    }

    private Builder addAllInetAddressToBuilder(Builder builder) {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4)
                        //TODO: Tiene que ser /24 o no inicia el servicio vpn
                        builder.addAddress(inetAddress, 24);
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return builder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        postRunning.run(false);
        downBytesInStart = upBytesInStart = 0;
        upByteTotal.set(0);
        downByteTotal.set(0);
        upByteSpeed.set(0);
        downByteSpeed.set(0);
        if (floatingTrafficDataView != null)
            windowMgr.removeView(floatingTrafficDataView);
        cleanup();
        Log.i(TAG, "Stopped");
    }

    private void cleanup() {
        try {
            deviceToNetworkTCPQueue = null;
            deviceToNetworkUDPQueue = null;
            networkToDeviceQueue = null;
            closeResources(vpnInterface);
        }catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    private static void closeResources(Closeable... resources) {
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public static void cancelNotification () {
        if(notMgr != null)
            notMgr.cancel(REQUEST_INTERNET_NOTIFICATION);
    }

    public static void loadAppData (Context context, Runnable onFinish) {
        //TODO: Crear un servicio q escuche cuando se instalen y desintalen las app para optimizar esto
        mapPackageNotified = new ArrayList<>();
        mapPackageAllowed = new ArrayList<>();
        mapPackageInteract = new ArrayList<>();
        List<ApplicationDetails> allApps = getPackagesInternetPermission(context);
        ApplicationDbManager appDbMgr = new ApplicationDbManager(context);
        List<ApplicationDetails> appsDetails = appDbMgr.getAllAppDetails();
        int countUid = allUid.size();
        for (ApplicationDetails app : appsDetails) {
            int indexSearch = binarySearch(allUid, app.getUid());
            if (indexSearch < 0 || indexSearch >= countUid)
                appDbMgr.deleteAplicationData(app.getUid());
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
        appDbMgr.addApps(allApps);
        sort(mapPackageAllowed);
        sort(mapPackageNotified);
        sort(mapPackageInteract);
        onFinish.run();
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

    private void notifyUid(int uid) {
        if(!Utils.isInteract(uid))
            showIndeterminateNotification(uid);
    }

    private void showIndeterminateNotification(final int uid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    semaphoreNotificator.acquire();
                    ApplicationDetails appDetails = appMgr.getApplicationForId(uid);
                    if (appDetails != null && appDetails.getPrincipalPackName() != null) {
                        appDetails.setNotified(true);
                        appMgr.updateApplicationData(appDetails.getUid(), appDetails);

                        ApplicationInfo appInfo = null;
                        try {
                            appInfo = packageManager.getApplicationInfo(appDetails
                                    .getPrincipalPackName(), PackageManager.GET_META_DATA);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if(!semaphoreNotificator.hasQueuedThreads()) {
                            Message message = new Message();
                            message.what = NOTIFY_INTERNET_REQUEST_ACCESS;
                            Bundle bundle = new Bundle();
                            bundle.putString(APP_DETAILS_NAME_NOTIFICATION_KEY, appInfo != null
                                    ? packageManager.getApplicationLabel(appInfo).toString()
                                    : appDetails.getPrincipalPackName());
                            bundle.putParcelable(APP_DETAILS_NOTIFICATION_KEY, appDetails);
                            message.setData(bundle);
                            handler.sendMessage(message);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    semaphoreNotificator.release();
                }
            }
        }).start();
    }

    private PendingIntent getPendintIntent() {
        Intent intent = new Intent(getBaseContext(), MainActivity.class);
        intent.putExtra(SELECTED_TAB_KEY, 1);
        return PendingIntent.getActivity(getBaseContext(), SHOW_NOTIFIED_APPS_REQUEST_CODE
                , intent, PendingIntent.FLAG_ONE_SHOT);
    }

    private Thread refreshTrafficData = new Thread(new Runnable() {
        @Override
        public void run() {
            boolean refreshSpeed = false;
            long lastUpByteTotal = upByteTotal.get(),
                    lastDownByteTotal = downByteTotal.get();
            while (!Thread.interrupted() && isRunning()) {
                try {
                    Thread.sleep(500);
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
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TrafficStats.getUidTxBytes(Process.myUid());
        }
    });

    private class VPNRunnable implements Runnable {
        private final String TAG = VPNRunnable.class.getSimpleName();

        private FileDescriptor vpnFileDescriptor;

        private BlockingQueue<Packet> deviceToNetworkUDPQueue;
        private BlockingQueue<Packet> deviceToNetworkTCPQueue;
        private BlockingQueue<ByteBuffer> networkToDeviceQueue;

        public VPNRunnable(FileDescriptor vpnFileDescriptor,
                           BlockingQueue<Packet> deviceToNetworkUDPQueue,
                           BlockingQueue<Packet> deviceToNetworkTCPQueue,
                           BlockingQueue<ByteBuffer> networkToDeviceQueue) {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
            this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
            this.networkToDeviceQueue = networkToDeviceQueue;
        }

        @Override
        public void run() {
            Log.i(TAG, "Started");

            downBytesInStart = getDownBytesTotalForService();
            upBytesInStart = getUpBytesTotalForService();
            upByteTotal.set(0);
            downByteTotal.set(0);
            upByteSpeed.set(0);
            downByteSpeed.set(0);
            refreshTrafficData.start();

            FileChannel vpnInput = new FileInputStream(vpnFileDescriptor).getChannel();
            FileChannel vpnOutput = new FileOutputStream(vpnFileDescriptor).getChannel();
            new Thread(new WriteVpnThread(vpnOutput, networkToDeviceQueue)).start();

            try {
                ByteBuffer bufferToNetwork;
                while (!Thread.interrupted() && isRunning()) {
                    bufferToNetwork = ByteBufferPool.acquire();
                    int readBytes = vpnInput.read(bufferToNetwork);
                    if (readBytes > 0) {
                        bufferToNetwork.flip();
                        Packet packet = new Packet(bufferToNetwork);
                        if (packet.isUDP() &&  !isBlockedUid(NetConnections.getUid(getBaseContext(),
                                NetConnections.Protocol.udp, packet.ip4Header.sourceAddress
                                , packet.udpHeader.sourcePort, packet.ip4Header.destinationAddress
                                , packet.udpHeader.destinationPort))) {
                            deviceToNetworkUDPQueue.offer(packet);
                        } else if (packet.isTCP() && !isBlockedUid(NetConnections.getUid(getBaseContext(),
                                NetConnections.Protocol.tcp, packet.ip4Header.sourceAddress
                                , packet.tcpHeader.sourcePort, packet.ip4Header.destinationAddress
                                , packet.tcpHeader.destinationPort))) {
                            deviceToNetworkTCPQueue.offer(packet);
                        }
                    } else {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, e.toString(), e);
            } finally {
                closeResources(vpnInput, vpnOutput);
            }
        }
    }

    public interface IPostRunningListener {
        void run (boolean running);
    }
}
