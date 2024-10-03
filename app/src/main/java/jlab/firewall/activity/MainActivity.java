package jlab.firewall.activity;

import android.Manifest;
import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import java.util.ArrayList;
import jlab.firewall.R;
import jlab.firewall.view.AppListFragment;
import jlab.firewall.view.HomeFragment;
import jlab.firewall.view.NotifiedAppListFragment;
import jlab.firewall.view.OnRunOnUiThread;
import jlab.firewall.view.TabsAdapter;
import jlab.firewall.vpn.FirewallService;

import static jlab.firewall.vpn.FirewallService.SHOW_FLOATING_SPEED_MONITOR_KEY;
import static jlab.firewall.vpn.FirewallService.START_VPN_ACTION;
import static jlab.firewall.vpn.FirewallService.isWaiting;
import static jlab.firewall.vpn.Utils.rateApp;
import static jlab.firewall.vpn.Utils.showAboutDialog;
import static jlab.firewall.vpn.Utils.showSnackBar;

public class MainActivity extends FragmentActivity implements OnRunOnUiThread {

    public static final String SELECTED_TAB_KEY = "SELECTED_TAB_KEY";
    public static final int ALL_PERMISSION_REQUEST_CODE = 9100,
            SHOW_NOTIFIED_APPS_REQUEST_CODE = 9101, CAN_DRAW_OVERLAY = 9102,
            VPN_REQUEST_CODE = 0x0F;
    public static final String USER_DEFINE_CAN_DRAW_OVERLAY_KEY = "CAN_DRAW_OVERLAY_KEY";
    private ViewPager tabHost;
    private TabsAdapter tabsAdapter;
    private SharedPreferences preferences;
    private BroadcastReceiver onFirewallChangeStatusReceiver = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                switch (intent.getAction()) {
                    case FirewallService.REFRESH_COUNT_NOTIFIED_APPS_ACTION:
                        tabsAdapter.refreshCountNotified(1);
                        break;
                    case FirewallService.STARTED_VPN_ACTION:
                        showSnackBar(R.string.started_vpn_service, tabHost);
                        break;
                    case FirewallService.STOPPED_VPN_ACTION:
                        showSnackBar(R.string.stopped_vpn_service, tabHost);
                        break;
                    case FirewallService.NOT_PREPARED_VPN_ACTION:
                        showSnackBar(R.string.not_prepared_vpn_service, tabHost);
                        break;
                    default:
                        break;
                }
            } catch (Exception ignored) {
                //TODO: disable log
                //ignored.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tabHost = findViewById(R.id.vpContent);
        HomeFragment.startFirewall = this::startFirewall;

        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setDisplayShowTitleEnabled(true);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(!preferences.contains(SHOW_FLOATING_SPEED_MONITOR_KEY)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(USER_DEFINE_CAN_DRAW_OVERLAY_KEY, false);
            editor.putBoolean(SHOW_FLOATING_SPEED_MONITOR_KEY, true);
            editor.apply();
            editor.commit();
        }
        tabsAdapter = new TabsAdapter(this, tabHost);
        tabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.home)),
                HomeFragment.class, null);
        tabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.app_list_request)),
                NotifiedAppListFragment.class, null);
        tabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.app_list)),
                AppListFragment.class, null);
        tabHost.setCurrentItem(getIntent().getIntExtra(SELECTED_TAB_KEY, 0));
        requestPermission();
        AppListFragment.setOnRunOnUiThread(this);
        IntentFilter intentFilter = new IntentFilter(FirewallService.REFRESH_COUNT_NOTIFIED_APPS_ACTION);
        intentFilter.addAction(FirewallService.STARTED_VPN_ACTION);
        intentFilter.addAction(FirewallService.STOPPED_VPN_ACTION);
        intentFilter.addAction(FirewallService.NOT_PREPARED_VPN_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(onFirewallChangeStatusReceiver,
                new IntentFilter(intentFilter));

        this.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (tabHost.getCurrentItem() != 0)
                    tabHost.setCurrentItem(0);
                else {
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                }
            }
        });
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        MobileAds.initialize(this, initializationStatus -> {
            try {
                AdView adView = findViewById(R.id.adView);
                adView.loadAd(new AdRequest.Builder().build());
            } catch (Exception ignored) {
                //TODO: disable log
                //ignored.printStackTrace();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onFirewallChangeStatusReceiver);
    }

    public void requestPermission() {
        boolean request = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> requestPermissions = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions.add(Manifest.permission.INTERNET);
                request = true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions.add(Manifest.permission.FOREGROUND_SERVICE);
                request = true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
                request = true;
            }
            if (request)
                requestAllPermission(requestPermissions);
        }
    }

    private void requestAllPermission(ArrayList<String> requestPermissions) {
        String[] permission = new String[requestPermissions.size()];
        ActivityCompat.requestPermissions(this, requestPermissions.toArray(permission), ALL_PERMISSION_REQUEST_CODE);
    }

    private void startFirewall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)
                && !preferences.getBoolean(USER_DEFINE_CAN_DRAW_OVERLAY_KEY, false)
                && preferences.getBoolean(SHOW_FLOATING_SPEED_MONITOR_KEY, false)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, CAN_DRAW_OVERLAY);
            } catch (Exception ignored) {
                //TODO: disable log
                //ignored.printStackTrace();
            }
        } else
            startVPN();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK)
            startService(new Intent(this, FirewallService.class));
        else if (requestCode == CAN_DRAW_OVERLAY) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(USER_DEFINE_CAN_DRAW_OVERLAY_KEY, true);
            editor.apply();
            editor.commit();
            startVPN();
        }
    }

    private void startVPN () {
        new Thread(() -> {
            try {
                Intent vpnIntent = VpnService.prepare(MainActivity.this);
                if (vpnIntent != null)
                    startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
                else if (!isWaiting())
                    onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
                else
                    LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(new
                            Intent(START_VPN_ACTION));
            } catch (Exception ignored) {
                //TODO: disable log
                //ignored.printStackTrace();
            }
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.mnRateApp)
            //rate
            rateApp(this);
        else if (id == R.id.mnAbout)
            //about
            showAboutDialog(this, this.tabHost);
        else if (id == R.id.mnClose)
            //close
            finish();
        return super.onOptionsItemSelected(item);
    }
}