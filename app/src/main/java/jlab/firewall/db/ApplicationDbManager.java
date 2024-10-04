package jlab.firewall.db;

import java.util.List;
import java.util.ArrayList;
import android.database.Cursor;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
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

    private static final int FIRST_DATABASE_VERSION = 1;
    private static final int TX_RX_BYTES_DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "jlab.firewall.db";
    private Context mContext;

    public ApplicationDbManager(Context context) {
        super(context, DATABASE_NAME, null, TX_RX_BYTES_DATABASE_VERSION);
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
                    + ApplicationContract.NOTIFIED + " INT NOT NULL,"
                    + ApplicationContract.TX_BYTES + " INT NOT NULL,"
                    + ApplicationContract.RX_BYTES + " INT NOT NULL)");

            addApps(db);
        } catch(Exception|Error ignored) { }
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

    public void addApplicationData(SQLiteDatabase sqLiteDatabase, ApplicationDetails applicationDetails) {
        try {
            sqLiteDatabase.insert(ApplicationContract.TABLE_NAME, null, applicationDetails.toContentValues());
        } catch(Exception|Error ignored) { }
    }

    public void addApplicationData(ApplicationDetails applicationDetails) {
        try {
            SQLiteDatabase sqLiteDatabase = getWritableDatabase();
            sqLiteDatabase.insert(ApplicationContract.TABLE_NAME, null, applicationDetails.toContentValues());
        } catch(Exception|Error ignored) { }
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion == ApplicationDbManager.FIRST_DATABASE_VERSION && newVersion >= ApplicationDbManager.TX_RX_BYTES_DATABASE_VERSION) {
            sqLiteDatabase.execSQL("ALTER TABLE " + ApplicationContract.TABLE_NAME + " ADD COLUMN " + ApplicationContract.TX_BYTES + " INTEGER DEFAULT 0");
            sqLiteDatabase.execSQL("ALTER TABLE " + ApplicationContract.TABLE_NAME + " ADD COLUMN " + ApplicationContract.RX_BYTES + " INTEGER DEFAULT 0");
        }
    }

    public ArrayList<ApplicationDetails> getAllAppDetails(String namesQuery) {
        ArrayList<ApplicationDetails> result = new ArrayList<>();
        try {
            SQLiteDatabase sqLiteDatabase = getReadableDatabase();
            Cursor cursor = sqLiteDatabase.query(ApplicationContract.TABLE_NAME, null,
                    null, null, null, null, null);
            while (cursor.moveToNext()) {
                String pPackName = cursor.getString(cursor.getColumnIndexOrThrow(ApplicationContract.PRINCIPAL_PACKAGE_NAME)),
                        packName = cursor.getString(cursor.getColumnIndexOrThrow(ApplicationContract.PACKAGES_NAME)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(ApplicationContract.NAMES));
                int uid = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract._ID)),
                        count = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.COUNT)),
                        internet = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.INTERNET_PERMISSION)),
                        interact = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.USER_INTERACT)),
                        notified = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.NOTIFIED));
                long txBytes = cursor.getLong(cursor.getColumnIndexOrThrow(ApplicationContract.TX_BYTES)),
                        rxBytes = cursor.getLong(cursor.getColumnIndexOrThrow(ApplicationContract.RX_BYTES));
                if (namesQuery == null || namesQuery.isEmpty() || name.toLowerCase().contains(namesQuery))
                    result.add(new ApplicationDetails(uid, count, pPackName, packName, name,
                            internet > 0, notified > 0, interact > 0,
                            txBytes, rxBytes));
            }
            cursor.close();
        } catch(Exception|Error ignored) { }
        return result;
    }

    public ArrayList<ApplicationDetails> getNotifiedAppDetails(String namesQuery) {
        ArrayList<ApplicationDetails> result = new ArrayList<>();
        try {
            SQLiteDatabase sqLiteDatabase = getReadableDatabase();
            Cursor cursor = sqLiteDatabase.query(ApplicationContract.TABLE_NAME, null,
                    ApplicationContract.NOTIFIED + " LIKE ?"
                    , new String[]{String.valueOf(1)}, null, null, null);
            while (cursor.moveToNext()) {
                String pPackName = cursor.getString(cursor.getColumnIndexOrThrow(ApplicationContract.PRINCIPAL_PACKAGE_NAME)),
                        packName = cursor.getString(cursor.getColumnIndexOrThrow(ApplicationContract.PACKAGES_NAME)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(ApplicationContract.NAMES));
                int uid = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract._ID)),
                        count = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.COUNT)),
                        internet = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.INTERNET_PERMISSION)),
                        interact = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.USER_INTERACT)),
                        notified = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.NOTIFIED));
                long txBytes = cursor.getLong(cursor.getColumnIndexOrThrow(ApplicationContract.TX_BYTES)),
                        rxBytes = cursor.getLong(cursor.getColumnIndexOrThrow(ApplicationContract.RX_BYTES));
                if (namesQuery == null || namesQuery.isEmpty() || name.toLowerCase().contains(namesQuery))
                    result.add(new ApplicationDetails(uid, count, pPackName, packName, name,
                            internet > 0, notified > 0, interact > 0,
                            txBytes, rxBytes));
            }
            cursor.close();
        } catch(Exception|Error ignored) { }
        return result;
    }

    public void updateApplicationData(int uid, ApplicationDetails newApplicationDetails) {
        int countUpdated = 0;
        try {
            SQLiteDatabase sqLiteDatabase = getWritableDatabase();
            countUpdated = sqLiteDatabase.update(ApplicationContract.TABLE_NAME,
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
        } catch(Exception|Error ignored) { }
    }

    public ApplicationDetails getApplicationForId (int uid) {
        try {
            SQLiteDatabase sqLiteDatabase = getReadableDatabase();
            Cursor cursor = sqLiteDatabase.query(ApplicationContract.TABLE_NAME, null,
                    ApplicationContract._ID + " LIKE ?", new String[]{String.valueOf(uid)},
                    null, null, null);
            if (cursor.moveToFirst()) {
                String pPackName = cursor.getString(cursor.getColumnIndexOrThrow(ApplicationContract.PRINCIPAL_PACKAGE_NAME)),
                        packName = cursor.getString(cursor.getColumnIndexOrThrow(ApplicationContract.PACKAGES_NAME)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(ApplicationContract.NAMES));
                int count = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.COUNT)),
                        internet = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.INTERNET_PERMISSION)),
                        interact = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.USER_INTERACT)),
                        notified = cursor.getInt(cursor.getColumnIndexOrThrow(ApplicationContract.NOTIFIED));
                long txBytes = cursor.getLong(cursor.getColumnIndexOrThrow(ApplicationContract.TX_BYTES)),
                        rxBytes = cursor.getLong(cursor.getColumnIndexOrThrow(ApplicationContract.RX_BYTES));
                cursor.close();
                return new ApplicationDetails(uid, count, pPackName, packName, name,
                        internet > 0, notified > 0, interact > 0,
                        txBytes, rxBytes);
            }
            cursor.close();
        } catch(Exception|Error ignored) { }
        return null;
    }

    public int deleteApplicationData(int uid) {
        int countDeleted = 0;
        try {
            SQLiteDatabase sqLiteDatabase = getWritableDatabase();
            countDeleted = sqLiteDatabase.delete(ApplicationContract.TABLE_NAME,
                    ApplicationContract._ID + " LIKE ?",
                    new String[]{String.valueOf(uid)});
        } catch (Exception|Error ignored) { }
        return countDeleted;
    }
}