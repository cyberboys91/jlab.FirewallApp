package jlab.firewall.view;

import android.content.Context;

/**
 * Created by Javier on 02/01/2021.
 */

interface OnReloadListener {

    void reload();

    void refreshDetails();

    void setOnRefreshDetailsListener(Runnable newOnRefreshDetails);

    boolean hasDetails();

    int getCount();

    String getName(Context context);

}
