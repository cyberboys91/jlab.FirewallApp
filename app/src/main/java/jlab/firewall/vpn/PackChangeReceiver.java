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
        switch (action) {
            case Intent.ACTION_PACKAGE_REMOVED:
                if (uid > 0) {
                    if (appMgr.deleteAplicationData(uid) > 0) {
                        NotificationManagerCompat.from(context).cancel(uid); // installed notification
                        NotificationManagerCompat.from(context).cancel(uid + 10000); // access notification
                    }
                }
                break;
            case Intent.ACTION_PACKAGE_ADDED:
            case Intent.ACTION_PACKAGE_REPLACED:
                uid = intent.getIntExtra(Intent.EXTRA_UID, 0);
                ApplicationDetails appDetails = getOnlyInternetApps(uid, packMgr, context);
                if(appDetails != null) {
                    if (Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction()))
                        appMgr.updateApplicationData(uid, appDetails);
                    else
                        appMgr.addApplicationData(appDetails);
                }
                break;
        }
    }

    private ApplicationDetails getOnlyInternetApps(int uid, PackageManager packMgr,
                                     Context context) {
        String names = null, pPackName = null, packNames = null;
        int count = 0;
        String[] packagesForUid = packMgr.getPackagesForUid(uid);
        if(packagesForUid != null) {
            for (String packName : packagesForUid) {
                if (hasInternet(packName, context)) {
                    try {
                        count++;
                        ApplicationInfo appInfo = packMgr.getApplicationInfo(packName,
                                PackageManager.GET_META_DATA);
                        if (pPackName == null)
                            pPackName = packName;
                        names += (names != null ? ", " : "") + packMgr.getApplicationLabel(appInfo);
                        packNames += (packNames != null ? ", " : "") + packName;
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
