package jlab.firewall.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.widget.SearchView;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import static jlab.firewall.vpn.FirewallService.notificationMessage;
import static jlab.firewall.vpn.FirewallService.notificationMessageUid;
import static jlab.firewall.vpn.FirewallService.mutexNotificator;

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
    protected Semaphore semaphoreLoadIcon = new Semaphore(3),
        semaphoreReload = new Semaphore(1);
    protected TextView tvEmptyList;
    protected List<ApplicationDetails> content = new ArrayList<>();
    protected static int[] colorsSpannable = new int[]{R.color.white
            , R.color.yellow, R.color.orange, R.color.green};
    private SearchView svSearch;
    protected String query;
    protected Runnable onRefreshDetailsListener = () -> { };
    protected static OnRunOnUiThread onRunOnUiThread = runnable -> { };

    protected ApplicationDbManager dbManager;

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
        this.srlRefresh.setOnRefreshListener(this::reload);

        this.svSearch.setOnCloseListener(() -> {
            query = null;
            svSearch.setVisibility(View.GONE);
            fbSearch.setVisibility(View.VISIBLE);
            return true;
        });
        this.svSearch.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            if(!hasFocus && (query == null || query.isEmpty())) {
                svSearch.setVisibility(View.GONE);
                fbSearch.setVisibility(View.VISIBLE);
            }
        });
        fbSearch.setOnClickListener(v -> {
            if (svSearch.getVisibility() != View.VISIBLE) {
                query = null;
                if (!svSearch.getQuery().toString().isEmpty())
                    svSearch.setQuery("", false);
                svSearch.setVisibility(View.VISIBLE);
                fbSearch.setVisibility(View.INVISIBLE);
            }
            svSearch.onActionViewExpanded();
            svSearch.requestFocus();
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
                if (!this.svSearch.getQuery().toString().equals(query)) {
                    this.svSearch.setQuery(query, false);
                }
                this.svSearch.setVisibility(View.VISIBLE);
                fbSearch.setVisibility(View.INVISIBLE);
                this.svSearch.postDelayed(() -> {
                    var tvSearch = this.svSearch.findViewById(androidx.appcompat.R.id.search_src_text);
                    tvSearch.setLayoutParams(new LinearLayout.LayoutParams(tvSearch.getWidth(),
                            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                    48f, getResources().getDisplayMetrics())));
                }, 0);
            } else {
                this.svSearch.setVisibility(View.GONE);
                fbSearch.setVisibility(View.VISIBLE);
            }
        }
        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(QUERY_KEY, query);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.dbManager = new ApplicationDbManager(getContext());
        this.adapter = new AppListAdapter(getContext(), getContent());
        this.adapter.setOnManagerContentListener(this);
        if (savedInstanceState != null && savedInstanceState.containsKey(QUERY_KEY))
            this.query = savedInstanceState.getString(QUERY_KEY);
    }

    @Override
    public void reload() {
        if (srlRefresh != null) {
            new Thread(() -> {
                try {
                    semaphoreReload.acquire();
                }
                catch (InterruptedException ignored) { }
                finally {
                    content = getContent();
                    handler.sendEmptyMessage(ON_LOAD_CONTENT_FINISH);
                    semaphoreReload.release();
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

    @SuppressLint("ViewHolder")
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        convertView = LayoutInflater.from(getContext())
                .inflate(R.layout.app_details_in_listview, parent, false);
        final ApplicationDetails current = adapter.getItem(position);
        final TextView packNames = convertView.findViewById(R.id.tvPackagesName),
                name = convertView.findViewById(R.id.tvName);
                //TODO: Show Tx and Rx bytes
                //txBytes = convertView.findViewById(R.id.tvTxBytes),
                //rxBytes = convertView.findViewById(R.id.tvRxBytes);
        final ImageView icon = convertView.findViewById(R.id.ivIcon);
        final SwitchMultiOptionButton swInternetStatus = convertView.findViewById(R.id.swInternetStatus);
        if (current != null) {
            packNames.setText(current.getPackNames());
            name.setText(current.getNames());
            //TODO: Show Tx and Rx bytes
            //txBytes.setText(current.getStringTxBytes());
            //rxBytes.setText(current.getStringRxBytes());
            Bitmap bmInCache = Utils.getIconForAppInCache(current.getPrincipalPackName());
            new Thread(() -> {
                final SpannableStringBuilder text = getSpannableFromText(current.getNames(), colorsSpannable);
                onRunOnUiThread.runOnUiThread(() -> name.setText(text));
            }).start();
            if (bmInCache != null)
                Glide.with(getContext()).asBitmap().load(bmInCache).into(icon);
            else {
                new Thread(() -> {
                    try {
                        semaphoreLoadIcon.acquire();
                    }
                    catch (InterruptedException ignored) { }
                    finally {
                        final Bitmap bm = current.getIcon(getContext());
                        onRunOnUiThread.runOnUiThread(() -> {
                            try {
                                Glide.with(icon).asBitmap().load(bm)
                                        .into(icon);
                                icon.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.fast_fade_in));
                            } catch (Exception ignored) { }
                        });
                        semaphoreLoadIcon.release();
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
                    try {
                        mutexNotificator.acquire();
                        if(current.getUid() == notificationMessageUid)
                            notificationMessage = null;
                    }
                    catch (InterruptedException ignored) { }
                    finally {
                        dbManager.updateApplicationData(current.getUid(), current);
                        mutexNotificator.release();
                    }
                    FirewallService.cancelNotification(current.getUid());
                    LocalBroadcastManager.getInstance(getContext())
                            .sendBroadcast(new Intent(REFRESH_COUNT_NOTIFIED_APPS_ACTION));
                }

                @Override
                public int countStates() {
                    return 3;
                }

                @Override
                public int getBackground(int state) {
                    return switch (state) {
                        case BLOCK_INTERNET_SWITCH_STATE -> R.drawable.img_cancel;
                        case NEUTRAL_SWITCH_STATE -> R.drawable.img_neutral;
                        default -> R.drawable.img_checked;
                    };
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
        content = dbManager.getAllAppDetails(query);
        return content;
    }

    public static void setOnRunOnUiThread(OnRunOnUiThread onRunOnUiThread) {
        AppListFragment.onRunOnUiThread = onRunOnUiThread;
    }

    protected SpannableStringBuilder getSpannableFromText(String text, int... colors) {
        SpannableStringBuilder strBuilder = new SpannableStringBuilder(text);
        int indexSep = 0, indexColor = 0;
        if (colors.length > 0)
            for (int i = 0; i < text.length() && indexSep < text.length(); i++)
                if (text.charAt(i) == ',') {
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