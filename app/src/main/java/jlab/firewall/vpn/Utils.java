package jlab.firewall.vpn;

/*
 * Created by Javier on 27/12/2020.
 */

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import androidx.collection.LruCache;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.TextView;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.google.android.material.snackbar.Snackbar;
import jlab.firewall.R;
import jlab.firewall.db.ApplicationDetails;
import static java.util.Collections.binarySearch;
import static java.util.Collections.sort;
import static jlab.firewall.vpn.FirewallService.mapPackageAllowed;
import static jlab.firewall.vpn.FirewallService.mapPackageInteract;
import static jlab.firewall.vpn.FirewallService.mapPackageNotified;
import static jlab.firewall.vpn.FirewallService.myUid;

public class Utils {

    private static final LruCache<String, Bitmap> iconsCache = new LruCache<>(500);

    public static boolean hasInternet(String packageName, Context context) {
        PackageManager pm = context.getPackageManager();
        return (pm.checkPermission("android.permission.INTERNET", packageName)
                == PackageManager.PERMISSION_GRANTED);
    }

    public static boolean isBlocked (int uid) {
        return !hasAccess(uid);
    }

    public static boolean isNotified (int uid) {
        int indexSearch = binarySearch(mapPackageNotified, uid);
        return indexSearch >= 0 && indexSearch < mapPackageNotified.size();
    }

    public static boolean isInteract (int uid) {
        int indexSearch = binarySearch(mapPackageInteract, uid);
        return indexSearch >= 0 && indexSearch < mapPackageInteract.size();
    }

    public static boolean hasAccess (int uid) {
        int indexSearch = binarySearch(mapPackageAllowed, uid);
        return indexSearch >= 0 && indexSearch < mapPackageAllowed.size();
    }

    public static void removeFromMapsIfExist (int uid) {
        int indexSearch = binarySearch(mapPackageNotified, uid);
        if (indexSearch >= 0 && indexSearch < mapPackageNotified.size())
            mapPackageNotified.remove(indexSearch);
        indexSearch = binarySearch(mapPackageInteract, uid);
        if (indexSearch >= 0 && indexSearch < mapPackageInteract.size())
            mapPackageInteract.remove(indexSearch);
        indexSearch = binarySearch(mapPackageAllowed, uid);
        if (indexSearch >= 0 && indexSearch < mapPackageAllowed.size())
            mapPackageAllowed.remove(indexSearch);
    }

    public static List<ApplicationDetails> getPackagesInternetPermission (Context context,
                                                                          ArrayList<Integer> allUid) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> appsInfo = pm.getInstalledApplications(0);
        ArrayList<ApplicationDetails> result = new ArrayList<>();
        for (int i = 0; i < appsInfo.size(); i++) {
            ApplicationInfo current = appsInfo.get(i);
            if (current.uid != myUid && hasInternet(current.packageName, context)) {
                CharSequence name = pm.getApplicationLabel(current);
                if (name != null)
                    current.name = name.toString();
                int index = allUid.indexOf(current.uid);
                if (index < 0) {
                    allUid.add(current.uid);
                    result.add(new ApplicationDetails(current.uid, current.packageName,
                            current.packageName, current.name, false, false, false));
                } else
                    result.get(index).addPackageAndName(current.name, current.packageName);
            }
        }
        sort(allUid);
        sort(result, new Comparator<ApplicationDetails>() {
            @Override
            public int compare(ApplicationDetails o1, ApplicationDetails o2) {
                return Integer.valueOf(o1.getUid()).compareTo(o2.getUid());
            }
        });
        return result;
    }

    public static Bitmap getIconForApp (String packageName, Context context) {
        Bitmap result = getIconForAppInCache(packageName);
        boolean inCache = result != null;
        if (!inCache)
            try {
                PackageManager pm = context.getPackageManager();
                Drawable icon = pm.getApplicationIcon(packageName);
                if (icon != null)
                    result = getBitmapFromDrawable(icon);
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
        if (result == null)
            result = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);

        if (!inCache)
            iconsCache.put(packageName, result);
        return result;
    }

    private static Bitmap getBitmapFromDrawable(Drawable drawable) {
        final Bitmap bm = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bm);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bm;
    }

    public static Bitmap getIconForAppInCache (String packageName) {
        return iconsCache.get(packageName);
    }

    public static void freeMemory () {
        iconsCache.evictAll();
    }

    public static void addToAllowedList (int uid) {
        if (isBlocked(uid)) {
            mapPackageAllowed.add(uid);
            sort(mapPackageAllowed);
        }
    }

    public static void addToNotifiedList (int uid) {
        if (!isNotified(uid)) {
            mapPackageNotified.add(uid);
            sort(mapPackageNotified);
        }
    }

    public static void addToInteractList (int uid) {
        if (!isInteract(uid)) {
            mapPackageInteract.add(uid);
            sort(mapPackageInteract);
        }
    }

    public static void removeFromNotifiedList (int uid) {
        int index = binarySearch(mapPackageNotified, uid);
        if (index >= 0 && index < mapPackageNotified.size())
            mapPackageNotified.remove(index);
    }

    public static void removeFromInteractList (int uid) {
        int index = binarySearch(mapPackageInteract, uid);
        if (index >= 0 && index < mapPackageInteract.size())
            mapPackageInteract.remove(index);
    }

    public static void removeFromAllowedList (int uid) {
        int index = binarySearch(mapPackageAllowed, uid);
        if (index >= 0 && index < mapPackageAllowed.size())
            mapPackageAllowed.remove(index);
    }

    public static void rateApp(Activity activity) {
        Uri uri = Uri.parse(String.format("market://details?id=%s", activity.getPackageName()));
        Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            activity.startActivity(goToMarket);
        } catch (Exception ignored) {
            ignored.printStackTrace();
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(String.format("https://play.google.com/store/apps/details?id=%s"
                            , activity.getPackageName()))));
        }
    }

    public static void showAboutDialog(final Activity activity, final View viewForSnack) {
        try {
            new AlertDialog.Builder(activity, R.style.AppTheme_AlertDialog)
                    .setTitle(R.string.about)
                    .setMessage(R.string.about_content)
                    .setPositiveButton(R.string.accept, null)
                    .setNegativeButton(activity.getString(R.string.contact), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            try {
                                activity.startActivity(new Intent(Intent.ACTION_SENDTO)
                                        .setData(Uri.parse(String.format("mailto:%s", activity.getString(R.string.mail)))));
                            } catch (Exception ignored) {
                                ignored.printStackTrace();
                                Utils.showSnackBar(R.string.app_mail_not_found, viewForSnack);
                            }
                        }
                    })
                    .show();
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    private static Snackbar createSnackBar(int message, View view) {
        return Snackbar.make(view, message, Snackbar.LENGTH_LONG);
    }

    public static Snackbar createSnackBar(String message, View view) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        ((TextView) snackbar.getView().findViewById(R.id.snackbar_text))
                .setTextColor(view.getResources().getColor(R.color.gray));
        snackbar.getView().setBackgroundResource(R.color.white);
        return snackbar;
    }

    public static void showSnackBar(int msg, View view) {
        Snackbar snackbar = createSnackBar(msg, view);
        ((TextView) snackbar.getView().findViewById(R.id.snackbar_text))
                .setTextColor(view.getResources().getColor(R.color.gray));
        snackbar.getView().setBackgroundResource(R.color.white);
        snackbar.setActionTextColor(view.getResources().getColor(R.color.accent));
        snackbar.show();
    }

    public static void showSnackBar(String msg, View view) {
        Snackbar snackbar = Utils.createSnackBar(msg, view);
        ((TextView) snackbar.getView().findViewById(R.id.snackbar_text))
                .setTextColor(view.getResources().getColor(R.color.gray));
        snackbar.getView().setBackgroundResource(R.color.white);
        snackbar.show();
    }

    public static String getSizeString(double mSize, int dec) {
        String type = "B";
        if (mSize > 1024) {
            mSize /= 1024;
            type = "KB";
        }
        if (mSize > 1024) {
            mSize /= 1024;
            type = "MB";
        }
        if (mSize > 1024) {
            mSize /= 1024;
            type = "GB";
        }
        if (mSize > 1024) {
            mSize /= 1024;
            type = "TB";
        }
        StringBuilder format = new StringBuilder("###");
        if (dec > 0)
            format.append(".");
        while (dec-- > 0)
            format.append("#");
        return new StringBuilder(new DecimalFormat(format.toString())
                .format(mSize)).append(type).toString();
    }

    public static String getTimeString(float seconds) {
        StringBuilder result = new StringBuilder();
        if (seconds >= 86400) {
            seconds /= 86400;
            result.append((int)seconds).append("d");
            return result.toString();
        }
        if (seconds >= 3600) {
            seconds /= 3600;
            result.append((int) seconds).append("h");
            return result.toString();
        }
        if (seconds >= 60) {
            seconds /= 60;
            result.append((int) seconds).append("m");
            return result.toString();
        }
        result.append((int) (seconds)).append("s");
        return result.toString();
    }
}
