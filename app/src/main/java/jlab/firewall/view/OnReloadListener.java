package jlab.firewall.view;

/**
 * Created by Javier on 02/01/2021.
 */

interface OnReloadListener {

    void reload();

    void refreshDetails();

    void setOnRefreshDetailsListener(Runnable newOnRefreshDetails);

    boolean hasDetails();

}
