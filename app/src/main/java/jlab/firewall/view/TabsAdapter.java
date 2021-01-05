package jlab.firewall.view;

import android.os.Bundle;
import android.app.ActionBar;
import android.content.Context;
import android.app.FragmentTransaction;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;

import jlab.firewall.R;

import static jlab.firewall.vpn.FirewallService.mapPackageNotified;

public class TabsAdapter extends FragmentStatePagerAdapter implements ActionBar.TabListener,
        ViewPager.OnPageChangeListener {
    private final Context mContext;
    private final ActionBar mActionBar;
    private final ViewPager mViewPager;
    private final ArrayList<TabInfo> mTabs = new ArrayList<>();
    private final ArrayList<Fragment> mFragments = new ArrayList<>();

    public TabsAdapter(FragmentActivity activity, ViewPager pager) {
        super(activity.getSupportFragmentManager());
        mContext = activity;
        mActionBar = activity.getActionBar();
        mViewPager = pager;
        mViewPager.setAdapter(this);
        mViewPager.setOnPageChangeListener(this);
    }

    private final class TabInfo {
        private final Class<?> clss;
        private final Bundle args;

        TabInfo(Class<?> _class, Bundle _args) {
            clss = _class;
            args = _args;
        }
    }

    public void reloadListFragment (final int position) {
        if (position >= 0 && position < mFragments.size()
                && mFragments.get(position) instanceof OnReloadListener)
            ((OnReloadListener) mFragments.get(position)).reload();
    }

    private void addTitleBadgeCountViewAppNotified(int position) {
        if (position >= 0 && position < mFragments.size()) {
            int count = mapPackageNotified.size();
            View customTitleView = mActionBar.getTabAt(position).getCustomView();
            if (customTitleView == null)
                customTitleView = LayoutInflater.from(mContext)
                        .inflate(R.layout.title_with_badge, null);
            ((TextView) customTitleView.findViewById(R.id.tvTitle)).setText(R.string.app_list_request);
            if (count > 0)
                ((TextView) customTitleView.findViewById(R.id.tvBadgeCount)).setText(count < 999
                        ? String.valueOf(count) : String.valueOf(count) + "+");
            (customTitleView.findViewById(R.id.rlBadgeWrapper)).setVisibility(count > 0
                    ? View.VISIBLE : View.GONE);
            mActionBar.getTabAt(position).setCustomView(customTitleView);
        }
    }

    public void addTab(ActionBar.Tab tab, Class<?> clss, Bundle args) {
        TabInfo info = new TabInfo(clss, args);
        tab.setTag(info);
        tab.setTabListener(this);
        mTabs.add(info);
        final int position = mFragments.size();
        Fragment fragment = Fragment.instantiate(mContext, info.clss.getName(), info.args);
        if (fragment instanceof OnReloadListener && ((OnReloadListener) fragment).hasDetails()) {
            ((OnReloadListener) fragment).setOnRefreshDetailsListener(new Runnable() {
                @Override
                public void run() {
                    addTitleBadgeCountViewAppNotified(position);
                }
            });
            addTitleBadgeCountViewAppNotified(position);
        }
        mFragments.add(fragment);
        mActionBar.addTab(tab);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mTabs.size();
    }

    @Override
    public Fragment getItem(int position) {
        return mFragments.get(position);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(final int position) {
        mActionBar.setSelectedNavigationItem(position);
        reloadListFragment(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        Object tag = tab.getTag();
        for (int i = 0; i < mTabs.size(); i++) {
            if (mTabs.get(i) == tag) {
                mViewPager.setCurrentItem(i);
                break;
            }
        }
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
    }
}