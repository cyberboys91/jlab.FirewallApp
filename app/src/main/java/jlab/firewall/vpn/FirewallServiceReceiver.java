package jlab.firewall.vpn;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;

/**
 * Created by Javier on 26/11/2023.
 */

public class FirewallServiceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null && intent.getAction().equals(FirewallService.RETRY_START_VPN_ACTION)) {
            Intent intentService = new Intent(context, FirewallService.class);
            intentService.setAction(FirewallService.RETRY_START_VPN_ACTION);
            context.startService(intentService);
        }
    }
}