package jlab.firewall.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.support.v4.app.NotificationManagerCompat;
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
        ApplicationDbManager appMgr = new ApplicationDbManager(context);
        PackageManager packMgr = context.getPackageManager();
        String action = (intent == null ? null : intent.getAction());
        if (action == null)
            return;
        int uid = intent.getIntExtra(Intent.EXTRA_UID, 0);
        if (uid > 0) {
            switch (action) {
                case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                    if (appMgr.deleteAplicationData(uid) > 0) {
                        removeFromMapsIfExist(uid);
                        NotificationManagerCompat.from(context).cancel(uid); // installed notification
                        NotificationManagerCompat.from(context).cancel(uid + 10000); // access notification
                    }
                    break;
                case Intent.ACTION_PACKAGE_ADDED:
                    ApplicationDetails appDetails = getOnlyInternetApps(uid, packMgr, context);
                    if (appDetails != null)
                        appMgr.addApplicationData(appDetails);
                    break;
                case Intent.ACTION_PACKAGE_REPLACED:
                    appDetails = appMgr.getApplicationForId(uid);
                    ApplicationDetails appDetails2 = getOnlyInternetApps(uid, packMgr, context);
                    if(appDetails != null && appDetails2 != null) {
                        appDetails2.setInteract(appDetails.interact());
                        appDetails2.setInternet(appDetails.hasInternet());
                        appDetails2.setNotified(appDetails.notified());
                        appMgr.updateApplicationData(uid, appDetails2);
                    }
                    else if(appDetails == null && appDetails2 != null)
                        appMgr.addApplicationData(appDetails2);
                    break;
                default:
                    break;
            }
        }
    }

    private ApplicationDetails getOnlyInternetApps(int uid, PackageManager packMgr,
                                     Context context) {
        String names = "", pPackName = "", packNames = "";
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
                            pPackName = packName;
                        names += (names.length() != 0 ? ", " : "")
                                + packMgr.getApplicationLabel(appInfo);
                        packNames += (packNames.length() != 0 ? ", " : "") + packName;
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
            return new ApplicationDetails(uid, count, pPackName,
                    packNames, names, false, false, false);
        }
        return null;
    }
}
