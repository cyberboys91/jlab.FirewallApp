package jlab.firewall.activity;

import android.Manifest;
import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;
import jlab.firewall.R;
import jlab.firewall.view.AppListFragment;
import jlab.firewall.view.HomeFragment;
import jlab.firewall.view.NotifiedAppListFragment;
import jlab.firewall.view.OnRunOnUiThread;
import jlab.firewall.view.TabsAdapter;
import jlab.firewall.vpn.FirewallService;
import static jlab.firewall.vpn.Utils.rateApp;
import static jlab.firewall.vpn.Utils.showAboutDialog;
import static jlab.firewall.vpn.Utils.showSnackBar;

public class MainActivity extends FragmentActivity implements OnRunOnUiThread{

    public static final String SELECTED_TAB_KEY = "SELECTED_TAB_KEY";
    public static final int ALL_PERMISSION_REQUEST_CODE = 9100,
            DRAW_OVERLAY_PERMISSION_REQUEST_CODE = 9101,
            SHOW_NOTIFIED_APPS_REQUEST_CODE = 9102,
            VPN_REQUEST_CODE = 0x0F;
    private ViewPager tabHost;
    private ActionBar actionBar;
    private TabsAdapter tabsAdapter;
    private BroadcastReceiver onFirewallChangeStatusReceiver = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                switch (intent.getAction()) {
                    case FirewallService.REFRESH_COUNT_NOTIFIED_APPS_ACTION:
                        tabsAdapter.reloadListFragment(1);
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
                ignored.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tabHost = findViewById(R.id.vpContent);
        HomeFragment.startVPN = new Runnable() {
            @Override
            public void run() {
                startVPN();
            }
        };

        actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setDisplayShowTitleEnabled(true);

        tabsAdapter = new TabsAdapter(this, tabHost);
        tabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.home)),
                HomeFragment.class, savedInstanceState);

        tabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.app_list_request)),
                NotifiedAppListFragment.class, savedInstanceState);
        tabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.app_list)),
                AppListFragment.class, savedInstanceState);
        tabHost.setCurrentItem(getIntent().getIntExtra(SELECTED_TAB_KEY, 0));
        requestPermission();
        AppListFragment.setOnRunOnUiThread(this);
        IntentFilter intentFilter = new IntentFilter(FirewallService.REFRESH_COUNT_NOTIFIED_APPS_ACTION);
        intentFilter.addAction(FirewallService.STARTED_VPN_ACTION);
        intentFilter.addAction(FirewallService.STOPPED_VPN_ACTION);
        intentFilter.addAction(FirewallService.NOT_PREPARED_VPN_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(onFirewallChangeStatusReceiver,
                new IntentFilter(intentFilter));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onFirewallChangeStatusReceiver);
    }

    public boolean requestPermission() {
        boolean request = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> requestPermissions = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions.add(Manifest.permission.INTERNET);
                request = true;
            }
            if (request)
                requestAllPermission(requestPermissions);
        }
        return request;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestAllPermission(ArrayList<String> requestPermissions) {
        String[] permission = new String[requestPermissions.size()];
        ActivityCompat.requestPermissions(this, permission, ALL_PERMISSION_REQUEST_CODE);
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, DRAW_OVERLAY_PERMISSION_REQUEST_CODE);
        }
    }

    private void startVPN() {
        Intent vpnIntent = FirewallService.prepare(this);
        if (vpnIntent != null)
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        else
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK)
            startService(new Intent(this, FirewallService.class));
    }

    @Override
    public void onBackPressed() {
        if (tabHost.getCurrentItem() != 0)
            tabHost.setCurrentItem(0);
        else {
            super.onBackPressed();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.mnRateApp:
                //rate
                rateApp(this);
                break;
            case R.id.mnAbout:
                //about
                showAboutDialog(this, this.tabHost);
                break;
            case R.id.mnClose:
                //close
                finish();
                break;
            default:
                return false;
        }
        return true;
    }
}