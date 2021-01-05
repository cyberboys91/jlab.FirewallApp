package jlab.firewall.db;

import android.provider.BaseColumns;

/*
 * Created by Javier on 24/04/2017.
 */
public class ApplicationContract implements BaseColumns {
    public static final String TABLE_NAME = "apps";
    public static final String PRINCIPAL_PACKAGE_NAME = "pPackName";
    public static final String PACKAGES_NAME = "packNames";
    public static final String NAMES = "names";
    public static final String COUNT = "count";
    public static final String INTERNET_PERMISSION = "internet";
    public static final String USER_INTERACT = "interact";
    public static final String NOTIFIED = "notified";
}