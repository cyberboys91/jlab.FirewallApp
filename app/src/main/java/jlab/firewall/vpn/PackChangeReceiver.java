package jlab.firewall.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import androidx.core.app.NotificationManagerCompat;
import jlab.firewall.db.ApplicationDbManager;
import jlab.firewall.db.ApplicationDetails;
import static jlab.firewall.vpn.Utils.hasInternet;
import static jlab.firewall.vpn.Utils.removeFromMapsIfExist;

/**
 * Created by Javier on 5/1/2021.
 */

public class PackChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PackageManager packMgr = context.getPackageManager();
        String action = (intent == null ? null : intent.getAction());
        if (action == null)
            return;
        int uid = intent.getIntExtra(Intent.EXTRA_UID, 0);
        if (uid > 0) {
            ApplicationDbManager dbManager = new ApplicationDbManager(context);
            ApplicationDetails appDetails;
            switch (action) {
                case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                    if (dbManager.deleteApplicationData(uid) > 0) {
                        removeFromMapsIfExist(uid);
                        NotificationManagerCompat.from(context).cancel(uid); // installed notification
                        NotificationManagerCompat.from(context).cancel(uid + 10000); // access notification
                    }
                    break;
                case Intent.ACTION_PACKAGE_ADDED:
                    appDetails = getOnlyInternetApps(uid, packMgr, context);
                    if (appDetails != null)
                        dbManager.addApplicationData(appDetails);
                    break;
                case Intent.ACTION_PACKAGE_REPLACED:
                    appDetails = dbManager.getApplicationForId(uid);
                    ApplicationDetails appDetails2 = getOnlyInternetApps(uid, packMgr, context);
                    if(appDetails != null && appDetails2 != null) {
                        appDetails2.setInteract(appDetails.interact());
                        appDetails2.setInternet(appDetails.hasInternet());
                        appDetails2.setNotified(appDetails.notified());
                        appDetails2.setTxBytes(appDetails.getTxBytes());
                        appDetails2.setRxBytes(appDetails.getRxBytes());
                        dbManager.updateApplicationData(uid, appDetails2);
                    }
                    else if(appDetails == null && appDetails2 != null)
                        dbManager.addApplicationData(appDetails2);
                    break;
                default:
                    break;
            }
        }
    }

    private ApplicationDetails getOnlyInternetApps(int uid, PackageManager packMgr,
                                     Context context) {
        StringBuilder names = new StringBuilder(),
                pPackName = new StringBuilder(),
                packNames = new StringBuilder();
        int count = 0;
        String[] packagesForUid = packMgr.getPackagesForUid(uid);
        if(packagesForUid != null) {
            for (String packName : packagesForUid) {
                if (hasInternet(packName, context)) {
                    try {
                        count++;
                        ApplicationInfo appInfo = packMgr.getApplicationInfo(packName,
                                PackageManager.GET_META_DATA);
                        if (pPackName.length() == 0)
                            pPackName = new StringBuilder(packName);
                        names.append(names.length() != 0 ? ", " : "")
                                .append(packMgr.getApplicationLabel(appInfo));
                        packNames.append(packNames.length() != 0 ? ", " : "")
                                .append(packName);
                    } catch (PackageManager.NameNotFoundException e) {
                        //TODO: disable log
                        //e.printStackTrace();
                    }
                }
            }
            return new ApplicationDetails(uid, count, pPackName.toString(),
                    packNames.toString(), names.toString(), false, false, false, 0, 0);
        }
        return null;
    }
}
