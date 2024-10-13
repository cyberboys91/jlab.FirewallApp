package jlab.firewall.db;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import jlab.firewall.vpn.Utils;

/*
 * Created by Javier on 24/04/2017.
 */
public class ApplicationDetails implements Parcelable {

    private int uid, count;
    private long txBytes, rxBytes;
    private StringBuilder pPackName, names, packNames;
    private boolean internet, notified, interact;

    public ApplicationDetails(int uid, int count, String pPackName, String packNames, String names,
                              boolean internet, boolean notified, boolean interact, long txBytes,
                              long rxBytes) {
        this.pPackName = new StringBuilder(pPackName);
        this.packNames = new StringBuilder(packNames);
        this.internet = internet;
        this.notified = notified;
        this.interact = interact;
        this.names = new StringBuilder(names);
        this.uid = uid;
        this.count = count;
        this.txBytes = txBytes;
        this.rxBytes = rxBytes;
    }

    public ApplicationDetails(int uid, String pPackName, String packNames, String names,
                              boolean internet, boolean notified, boolean interact, long txBytes,
                              long rxBytes) {
        this(uid, 1, pPackName, packNames, names, internet, notified, interact, txBytes, rxBytes);
    }

    private ApplicationDetails(Parcel in) {
        this.uid = in.readInt();
        this.count = in.readInt();
        this.pPackName = new StringBuilder(in.readString());
        this.packNames = new StringBuilder(in.readString());
        this.names = new StringBuilder(in.readString());
        this.internet = in.readInt() > 0;
        this.interact = in.readInt() > 0;
        this.notified = in.readInt() > 0;
        this.txBytes = in.readLong();
        this.rxBytes = in.readLong();
    }

    public static final Creator<ApplicationDetails> CREATOR = new Creator<>() {
        @Override
        public ApplicationDetails createFromParcel(Parcel in) {
            return new ApplicationDetails(in);
        }

        @Override
        public ApplicationDetails[] newArray(int size) {
            return new ApplicationDetails[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.uid);
        parcel.writeInt(this.count);
        parcel.writeString(getPrincipalPackName());
        parcel.writeString(getPackNames());
        parcel.writeString(getNames());
        parcel.writeInt(this.internet ? 1 : 0);
        parcel.writeInt(this.interact ? 1 : 0);
        parcel.writeInt(this.notified ? 1 : 0);
        parcel.writeLong(this.txBytes);
        parcel.writeLong(this.rxBytes);
    }

    public String getPackNames() {
        return packNames.toString();
    }

    public Bitmap getIcon (Context context) {
        return Utils.getIconForApp(pPackName.toString(), context);
    }

    public void setPrincipalPackName(String principalPackageName) {
        this.pPackName = new StringBuilder(principalPackageName);
    }

    public String getNames() {
        return names.toString();
    }

    public boolean hasInternet () {
        return this.internet;
    }

    public boolean notified () {
        return this.notified;
    }

    public boolean interact () {
        return this.interact;
    }

    public int getUid() {
        return uid;
    }

    public int getCount() {
        return count;
    }

    public long getTxBytes() {
        return txBytes;
    }

    public long getRxBytes() {
        return rxBytes;
    }

    public String getStringTxBytes () {
        return Utils.getSizeString(this.txBytes, this.txBytes > Utils.ONE_KB ? 2 : 0);
    }

    public String getStringRxBytes () {
        return Utils.getSizeString(this.rxBytes, this.rxBytes > Utils.ONE_KB ? 2 : 0);
    }

    public void addPackageAndName(String name, String packageName) {
        this.names.append(", ").append(name);
        this.packNames.append(", ").append(packageName);
        this.count++;
    }

    public String getPrincipalPackName() {
        return pPackName.toString();
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public void setInteract(boolean interact) {
        this.interact = interact;
    }

    public void setTxBytes(long txBytes) {
        this.txBytes = txBytes;
    }

    public void setRxBytes(long rxBytes) {
        this.rxBytes = rxBytes;
    }

    public void setInternet(boolean internet) {
        this.internet = internet;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setNames(String names) {
        this.names = new StringBuilder(names);
    }

    public void setPackNames(String packNames) {
        this.packNames = new StringBuilder(packNames);
    }

    public ContentValues toContentValues() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(ApplicationContract._ID, this.uid);
        contentValues.put(ApplicationContract.COUNT, this.count);
        contentValues.put(ApplicationContract.PRINCIPAL_PACKAGE_NAME, getPrincipalPackName());
        contentValues.put(ApplicationContract.PACKAGES_NAME, getPackNames());
        contentValues.put(ApplicationContract.NAMES, getNames());
        contentValues.put(ApplicationContract.INTERNET_PERMISSION, this.internet ? 1 : 0);
        contentValues.put(ApplicationContract.USER_INTERACT, this.interact ? 1 : 0);
        contentValues.put(ApplicationContract.NOTIFIED, this.notified ? 1 : 0);
        contentValues.put(ApplicationContract.TX_BYTES, this.txBytes);
        contentValues.put(ApplicationContract.RX_BYTES, this.rxBytes);
        return contentValues;
    }
}
