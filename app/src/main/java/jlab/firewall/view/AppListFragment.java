package jlab.firewall.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import jlab.firewall.R;
import jlab.firewall.db.ApplicationDbManager;
import jlab.firewall.db.ApplicationDetails;
import jlab.firewall.vpn.Utils;
import static jlab.firewall.vpn.FirewallService.REFRESH_COUNT_NOTIFIED_APPS_ACTION;

/**
 * Created by Javier on 28/12/2020.
 */

public class AppListFragment extends Fragment implements AppListAdapter.IOnManagerContentListener,
        OnReloadListener {
    private static final int ALLOW_INTERNET_SWITCH_STATE = 2, BLOCK_INTERNET_SWITCH_STATE = 0,
            NEUTRAL_SWITCH_STATE = 1;
    private static final int RUN_ON_REFRESH_DETAILS_LISTENER = 9300, ON_LOAD_CONTENT_FINISH = 9301;
    protected AppListAdapter adapter;
    private SwipeRefreshLayout srlRefresh;
    protected Semaphore semaphoreLoadIcon = new Semaphore(5);
    protected ApplicationDbManager appDbMgr;
    protected TextView tvEmptyList;
    protected Runnable onRefreshDetailsListener = new Runnable() {
        @Override
        public void run() { }
    };
    protected static OnRunOnUiThread onRunOnUiThread = new OnRunOnUiThread() {
        @Override
        public void runOnUiThread(Runnable runnable) { }
    };
    protected List<ApplicationDetails> content = new ArrayList<>();
    protected Handler handler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case RUN_ON_REFRESH_DETAILS_LISTENER:
                    refreshDetails();
                    break;
                case ON_LOAD_CONTENT_FINISH:
                    adapter.reload(content);
                    tvEmptyList.setVisibility(adapter.getCount() == 0
                            ? View.VISIBLE
                            : View.INVISIBLE);
                    srlRefresh.setRefreshing(false);
                    refreshDetails();
                    break;
                default:
                    break;
            }
            return false;
        }
    });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_list, container, false);
        this.tvEmptyList = view.findViewById(R.id.tvEmptyList);
        this.srlRefresh = view.findViewById(R.id.srlRefresh);
        ListView lvAppList = view.findViewById(R.id.lvAppList);
        lvAppList.setAdapter(adapter);
        this.srlRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                reload();
            }
        });
        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.appDbMgr = new ApplicationDbManager(getContext());
        this.adapter = new AppListAdapter(getContext(), getContent());
        this.adapter.setOnManagerContentListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void reload() {
        if (srlRefresh != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    content = getContent();
                    handler.sendEmptyMessage(ON_LOAD_CONTENT_FINISH);
                }
            }).start();
        } else
            handler.sendEmptyMessage(RUN_ON_REFRESH_DETAILS_LISTENER);
    }

    @Override
    public void refreshDetails() {
        onRefreshDetailsListener.run();
    }

    @Override
    public void setOnRefreshDetailsListener(Runnable newOnRefreshDetails) {
        this.onRefreshDetailsListener = newOnRefreshDetails;
    }

    @Override
    public boolean hasDetails() {
        return false;
    }

    @Override
    public int getCount() {
        return content.size();
    }

    @Override
    public String getName(Context context) {
        return context.getString(R.string.app_list);
    }

    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        convertView = LayoutInflater.from(getContext())
                .inflate(R.layout.app_details_in_listview, parent, false);
        final ApplicationDetails current = adapter.getItem(position);
        TextView packNames = convertView.findViewById(R.id.tvPackagesName),
                name = convertView.findViewById(R.id.tvName);
        final ImageView icon = convertView.findViewById(R.id.ivIcon);
        final SwitchMultiOptionButton swInternetStatus = convertView.findViewById(R.id.swInternetStatus);
        if (current != null) {
            packNames.setText(current.getPackNames());
            name.setText(current.getNames());
            Bitmap bmInCache = Utils.getIconForAppInCache(current.getPrincipalPackName());
            if (bmInCache != null)
                Glide.with(getContext()).asBitmap().load(bmInCache).into(icon);
            else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            semaphoreLoadIcon.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            final Bitmap bm = current.getIcon(getContext());
                            onRunOnUiThread.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Glide.with(icon).asBitmap().load(bm)
                                                .into(icon);
                                        icon.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.fast_fade_in));
                                    } catch (Exception ignored) {
                                        ignored.printStackTrace();
                                    }
                                }
                            });
                            semaphoreLoadIcon.release();
                        }
                    }
                }).start();
            }
            swInternetStatus.setOnSwitchListener(new OnSwitchListener() {
                @Override
                public void onSwitchChange(int state) {
                    switch (state) {
                        case ALLOW_INTERNET_SWITCH_STATE:
                            current.setInternet(true);
                            current.setInteract(true);
                            current.setNotified(false);
                            break;
                        case BLOCK_INTERNET_SWITCH_STATE:
                            current.setInternet(false);
                            current.setInteract(true);
                            current.setNotified(false);
                            break;
                        case NEUTRAL_SWITCH_STATE:
                            current.setInteract(false);
                            current.setInternet(false);
                            current.setNotified(false);
                            break;
                        default:
                            break;
                    }
                    appDbMgr.updateApplicationData(current.getUid(), current);
                    LocalBroadcastManager.getInstance(getContext())
                            .sendBroadcast(new Intent(REFRESH_COUNT_NOTIFIED_APPS_ACTION));
                }

                @Override
                public int countStates() {
                    return 3;
                }

                @Override
                public int getBackground(int state) {
                    switch (state) {
                        case BLOCK_INTERNET_SWITCH_STATE:
                            return R.drawable.img_cancel;
                        case NEUTRAL_SWITCH_STATE:
                            return R.drawable.img_neutral;
                        default:
                            return R.drawable.img_checked;
                    }
                }
            });
            swInternetStatus.setState(getSwitchStateFromAppDetails(current));
        }
        return convertView;
    }

    private int getSwitchStateFromAppDetails(ApplicationDetails details) {
        if (details.hasInternet())
            return ALLOW_INTERNET_SWITCH_STATE;
        else if (details.interact())
            return BLOCK_INTERNET_SWITCH_STATE;
        return NEUTRAL_SWITCH_STATE;
    }

    @Override
    public List<ApplicationDetails> getContent() {
        content = appDbMgr.getAllAppDetails();
        return content;
    }

    public static void setOnRunOnUiThread(OnRunOnUiThread onRunOnUiThread) {
        AppListFragment.onRunOnUiThread = onRunOnUiThread;
    }
}