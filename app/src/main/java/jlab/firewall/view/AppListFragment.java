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
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.SearchView;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
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
import jlab.firewall.vpn.FirewallService;
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
    private static final String QUERY_KEY = "QUERY_KEY";
    protected AppListAdapter adapter;
    private SwipeRefreshLayout srlRefresh;
    protected Semaphore semaphoreLoadIcon = new Semaphore(3);
    protected ApplicationDbManager appDbMgr;
    protected TextView tvEmptyList;
    protected static int[] colorsSpannable = new int[]{R.color.darken
            , R.color.yellow, R.color.orange, R.color.green};
    private SearchView svSearch;
    protected String query;
    protected Runnable onRefreshDetailsListener = new Runnable() {
        @Override
        public void run() {
        }
    };
    protected static OnRunOnUiThread onRunOnUiThread = new OnRunOnUiThread() {
        @Override
        public void runOnUiThread(Runnable runnable) {
        }
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
    private FloatingActionButton fbSearch;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_list, container, false);
        this.tvEmptyList = view.findViewById(R.id.tvEmptyList);
        this.fbSearch = view.findViewById(R.id.fbSearch);
        this.svSearch = view.findViewById(R.id.svSearch);
        this.srlRefresh = view.findViewById(R.id.srlRefresh);
        ListView lvAppList = view.findViewById(R.id.lvAppList);
        lvAppList.setAdapter(adapter);
        this.srlRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                reload();
            }
        });

        this.svSearch.setQueryHint(getString(R.string.search_hint));
        this.svSearch.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                svSearch.setVisibility(View.GONE);
                fbSearch.setVisibility(View.VISIBLE);
                return true;
            }
        });
        this.svSearch.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus && (query == null || query.isEmpty())) {
                    svSearch.setVisibility(View.GONE);
                    fbSearch.setVisibility(View.VISIBLE);
                }
            }
        });
        fbSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (svSearch.getVisibility() != View.VISIBLE) {
                    query = null;
                    svSearch.setQuery("", true);
                    svSearch.setVisibility(View.VISIBLE);
                    fbSearch.setVisibility(View.INVISIBLE);
                }
                svSearch.onActionViewExpanded();
                svSearch.requestFocus();
            }
        });
        this.svSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                query = newText.toLowerCase();
                reload();
                return true;
            }
        });
        if(this.svSearch != null) {
            if (query != null && !query.isEmpty()) {
                this.svSearch.setQuery(query, true);
                this.svSearch.setVisibility(View.VISIBLE);
                fbSearch.setVisibility(View.INVISIBLE);
            } else {
                this.svSearch.setVisibility(View.GONE);
                fbSearch.setVisibility(View.VISIBLE);
            }
        }
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(QUERY_KEY, query);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.appDbMgr = new ApplicationDbManager(getContext());
        this.adapter = new AppListAdapter(getContext(), getContent());
        this.adapter.setOnManagerContentListener(this);
        if (savedInstanceState != null && savedInstanceState.containsKey(QUERY_KEY))
            this.query = savedInstanceState.getString(QUERY_KEY);
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
                    content = getContent();
                    handler.sendEmptyMessage(ON_LOAD_CONTENT_FINISH);
                }
            }).start();
        }
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
        final TextView packNames = convertView.findViewById(R.id.tvPackagesName),
                name = convertView.findViewById(R.id.tvName);
        final ImageView icon = convertView.findViewById(R.id.ivIcon);
        final SwitchMultiOptionButton swInternetStatus = convertView.findViewById(R.id.swInternetStatus);
        if (current != null) {
            packNames.setText(current.getPackNames());
            name.setText(current.getNames());
            Bitmap bmInCache = Utils.getIconForAppInCache(current.getPrincipalPackName());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final SpannableStringBuilder text = getSpannableFromText(current.getNames(), ',', colorsSpannable);
                    onRunOnUiThread.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            name.setText(text);
                        }
                    });
                }
            }).start();
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
                    FirewallService.cancelNotification(current.getUid());
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
        content = appDbMgr.getAllAppDetails(query);
        return content;
    }

    public static void setOnRunOnUiThread(OnRunOnUiThread onRunOnUiThread) {
        AppListFragment.onRunOnUiThread = onRunOnUiThread;
    }

    protected SpannableStringBuilder getSpannableFromText(String text, char sep, int... colors) {
        SpannableStringBuilder strBuilder = new SpannableStringBuilder(text);
        int indexSep = 0, indexColor = 0;
        if (colors.length > 0)
            for (int i = 0; i < text.length() && indexSep < text.length(); i++)
                if (text.charAt(i) == sep) {
                    if (indexColor >= colors.length)
                        indexColor = 0;
                    ForegroundColorSpan colorSpan = new ForegroundColorSpan
                            (getResources().getColor(colors[indexColor++]));
                    strBuilder.setSpan(colorSpan, indexSep, i, 0);
                    indexSep = i + 2;
                }
        if(query != null) {
            int index = text.toLowerCase().indexOf(query);
            if(index > -1) {
                BackgroundColorSpan colorSpan = new BackgroundColorSpan(getResources()
                        .getColor(R.color.neutral));
                strBuilder.setSpan(colorSpan, index, index + query.length(), 0);
            }
        }
        Selection.selectAll(strBuilder);
        return strBuilder;
    }
}