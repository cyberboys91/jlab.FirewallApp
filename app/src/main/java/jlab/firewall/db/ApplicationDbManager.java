package jlab.firewall.db;

import java.util.List;
import java.util.ArrayList;

import android.database.Cursor;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import static java.util.Arrays.sort;
import static jlab.firewall.vpn.Utils.addToAllowedList;
import static jlab.firewall.vpn.Utils.addToInteractList;
import static jlab.firewall.vpn.Utils.addToNotifiedList;
import static jlab.firewall.vpn.Utils.getPackagesInternetPermission;
import static jlab.firewall.vpn.Utils.removeFromAllowedList;
import static jlab.firewall.vpn.Utils.removeFromInteractList;
import static jlab.firewall.vpn.Utils.removeFromNotifiedList;

/*
 * Created by Javier on 24/04/2017.
 */
public class ApplicationDbManager extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "jlab.firewall.db";
    private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + ApplicationContract.TABLE_NAME;
    private Context mContext;

    public ApplicationDbManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE TABLE " + ApplicationContract.TABLE_NAME + " ("
                    + ApplicationContract._ID + " INTEGER PRIMARY KEY,"
                    + ApplicationContract.COUNT + " INT NOT NULL,"
                    + ApplicationContract.PRINCIPAL_PACKAGE_NAME + " TEXT NOT NULL,"
                    + ApplicationContract.PACKAGES_NAME + " TEXT NOT NULL,"
                    + ApplicationContract.NAMES + " TEXT NOT NULL,"
                    + ApplicationContract.INTERNET_PERMISSION + " INT NOT NULL,"
                    + ApplicationContract.USER_INTERACT + " INT NOT NULL,"
                    + ApplicationContract.NOTIFIED + " INT NOT NULL)");
            addApps(db);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addApps(SQLiteDatabase db) {
        addApps(db, getPackagesInternetPermission(mContext, new ArrayList<Integer>()));
    }

    public void addApps(SQLiteDatabase db, List<ApplicationDetails> appsDetails) {
        for (ApplicationDetails app : appsDetails)
            addApplicationData(db, app);
    }

    public void addApps(List<ApplicationDetails> appsDetails) {
        addApps(getWritableDatabase(), appsDetails);
    }

    public long addApplicationData(SQLiteDatabase sqLiteDatabase, ApplicationDetails applicationDetails) {
        return sqLiteDatabase.insert(ApplicationContract.TABLE_NAME, null, applicationDetails.toContentValues());
    }

    public long addApplicationData(ApplicationDetails applicationDetails) {
        SQLiteDatabase sqLiteDatabase = getWritableDatabase();
        return sqLiteDatabase.insert(ApplicationContract.TABLE_NAME, null, applicationDetails.toContentValues());
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        sqLiteDatabase.execSQL(SQL_DELETE_ENTRIES);
        onCreate(sqLiteDatabase);
    }

    public ArrayList<ApplicationDetails> getAllAppDetails() {
        SQLiteDatabase sqLiteDatabase = getReadableDatabase();
        ArrayList<ApplicationDetails> result = new ArrayList<>();
        Cursor cursor = sqLiteDatabase.query(ApplicationContract.TABLE_NAME, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            String pPackName = cursor.getString(cursor.getColumnIndex(ApplicationContract.PRINCIPAL_PACKAGE_NAME)),
                    packName = cursor.getString(cursor.getColumnIndex(ApplicationContract.PACKAGES_NAME)),
                    name = cursor.getString(cursor.getColumnIndex(ApplicationContract.NAMES));
            int uid = cursor.getInt(cursor.getColumnIndex(ApplicationContract._ID)),
                    count = cursor.getInt(cursor.getColumnIndex(ApplicationContract.COUNT)),
                    internet = cursor.getInt(cursor.getColumnIndex(ApplicationContract.INTERNET_PERMISSION)),
                    interact = cursor.getInt(cursor.getColumnIndex(ApplicationContract.USER_INTERACT)),
                    notified = cursor.getInt(cursor.getColumnIndex(ApplicationContract.NOTIFIED));
            result.add(new ApplicationDetails(uid, count, pPackName, packName, name,
                    internet > 0, notified > 0, interact > 0));
        }
        cursor.close();
        return result;
    }

    public ArrayList<ApplicationDetails> getNotifiedAppDetails() {
        SQLiteDatabase sqLiteDatabase = getReadableDatabase();
        ArrayList<ApplicationDetails> result = new ArrayList<>();
        Cursor cursor = sqLiteDatabase.query(ApplicationContract.TABLE_NAME, null,
                ApplicationContract.NOTIFIED + " LIKE ?"
                , new String[]{String.valueOf(1)}, null, null, null);
        while (cursor.moveToNext()) {
            String pPackName = cursor.getString(cursor.getColumnIndex(ApplicationContract.PRINCIPAL_PACKAGE_NAME)),
                    packName = cursor.getString(cursor.getColumnIndex(ApplicationContract.PACKAGES_NAME)),
                    name = cursor.getString(cursor.getColumnIndex(ApplicationContract.NAMES));
            int uid = cursor.getInt(cursor.getColumnIndex(ApplicationContract._ID)),
                    count = cursor.getInt(cursor.getColumnIndex(ApplicationContract.COUNT)),
                    internet = cursor.getInt(cursor.getColumnIndex(ApplicationContract.INTERNET_PERMISSION)),
                    interact = cursor.getInt(cursor.getColumnIndex(ApplicationContract.USER_INTERACT)),
                    notified = cursor.getInt(cursor.getColumnIndex(ApplicationContract.NOTIFIED));
            result.add(new ApplicationDetails(uid, count, pPackName, packName, name,
                    internet > 0, notified > 0, interact > 0));
        }
        cursor.close();
        return result;
    }

    public int updateApplicationData(int uid, ApplicationDetails newApplicationDetails) {
        SQLiteDatabase sqLiteDatabase = getWritableDatabase();
        int countUpdated = sqLiteDatabase.update(ApplicationContract.TABLE_NAME,
                newApplicationDetails.toContentValues(),
                ApplicationContract._ID + " LIKE ?",
                new String[]{Integer.toString(uid)});
        if (countUpdated > 0) {
            if (newApplicationDetails.hasInternet())
                addToAllowedList(uid);
            else
                removeFromAllowedList(uid);
            if (newApplicationDetails.interact())
                addToInteractList(uid);
            else
                removeFromInteractList(uid);
            if (newApplicationDetails.notified())
                addToNotifiedList(uid);
            else
                removeFromNotifiedList(uid);
        }
        return countUpdated;
    }

    public ApplicationDetails getApplicationForId (int uid) {
        SQLiteDatabase sqLiteDatabase = getReadableDatabase();
        Cursor cursor = sqLiteDatabase.query(ApplicationContract.TABLE_NAME, null,
                ApplicationContract._ID + " LIKE ?", new String[] {String.valueOf(uid)},
                null, null, null);
        if (cursor.moveToFirst()) {
            String pPackName = cursor.getString(cursor.getColumnIndex(ApplicationContract.PRINCIPAL_PACKAGE_NAME)),
                    packName = cursor.getString(cursor.getColumnIndex(ApplicationContract.PACKAGES_NAME)),
                    name = cursor.getString(cursor.getColumnIndex(ApplicationContract.NAMES));
            int count = cursor.getInt(cursor.getColumnIndex(ApplicationContract.COUNT)),
                    internet = cursor.getInt(cursor.getColumnIndex(ApplicationContract.INTERNET_PERMISSION)),
                    interact = cursor.getInt(cursor.getColumnIndex(ApplicationContract.USER_INTERACT)),
                    notified = cursor.getInt(cursor.getColumnIndex(ApplicationContract.NOTIFIED));
            cursor.close();
            return new ApplicationDetails(uid, count, pPackName, packName, name,
                    internet > 0, notified > 0, interact > 0);
        }
        cursor.close();
        return null;
    }

    public int deleteAplicationData(int uid) {
        SQLiteDatabase sqLiteDatabase = getWritableDatabase();
        return sqLiteDatabase.delete(ApplicationContract.TABLE_NAME,
                ApplicationContract._ID + " LIKE ?",
                new String[]{String.valueOf(uid)});
    }
}