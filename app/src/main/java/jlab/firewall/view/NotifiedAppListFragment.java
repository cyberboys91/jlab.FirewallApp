package jlab.firewall.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import java.util.List;
import jlab.firewall.R;
import jlab.firewall.db.ApplicationDetails;
import jlab.firewall.vpn.FirewallService;
import jlab.firewall.vpn.Utils;

import static jlab.firewall.view.SwitchMultiOptionButton.viewOnTouchListener;
import static jlab.firewall.vpn.FirewallService.REFRESH_COUNT_NOTIFIED_APPS_ACTION;
import static jlab.firewall.vpn.FirewallService.notificationMessage;
import static jlab.firewall.vpn.FirewallService.notificationMessageUid;
import static jlab.firewall.vpn.FirewallService.mutexNotificator;

/**
 * Created by Javier on 02/01/2021.
 */

public class NotifiedAppListFragment extends AppListFragment {

    private BroadcastReceiver refreshCountNotifiedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(REFRESH_COUNT_NOTIFIED_APPS_ACTION)) {
                reload();
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocalBroadcastManager.getInstance(getContext())
                .registerReceiver(refreshCountNotifiedReceiver
                , new IntentFilter(REFRESH_COUNT_NOTIFIED_APPS_ACTION));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(refreshCountNotifiedReceiver);
    }

    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        convertView = LayoutInflater.from(getContext())
                .inflate(R.layout.app_details_notified_in_listview, parent, false);
        final ApplicationDetails current = adapter.getItem(position);
        final TextView packNames = convertView.findViewById(R.id.tvPackagesName),
                name = convertView.findViewById(R.id.tvName);
        final ImageView icon = convertView.findViewById(R.id.ivIcon);
        if (current != null) {
            packNames.setText(current.getPackNames());
            name.setText(current.getNames());
            Bitmap bmInCache = Utils.getIconForAppInCache(current.getPrincipalPackName());
            new Thread(() -> {
                final SpannableStringBuilder text = getSpannableFromText(current.getNames(), colorsSpannable);
                onRunOnUiThread.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        name.setText(text);
                    }
                });
            }).start();
            if(bmInCache != null)
                Glide.with(icon).asBitmap().load(bmInCache).into(icon);
            else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            semaphoreLoadIcon.acquire();
                        } catch (InterruptedException e) {
                            //TODO: disable log
                            //e.printStackTrace();
                        } finally {
                            final Bitmap bm = current.getIcon(getContext());
                            onRunOnUiThread.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    icon.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.fast_fade_in));
                                    Glide.with(icon).asBitmap().load(bm)
                                            .into(icon);
                                }
                            });
                            semaphoreLoadIcon.release();
                        }
                    }
                }).start();
            }
            final View allowInternet = convertView.findViewById(R.id.llAllowInternet),
                    blockInternet = convertView.findViewById(R.id.llBlockInternet);
            allowInternet.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    current.setInternet(true);
                    current.setInteract(true);
                    current.setNotified(false);
                    try {
                        mutexNotificator.acquire();
                        if(current.getUid() == notificationMessageUid)
                            notificationMessage = null;
                    } catch (InterruptedException e) {
                        //TODO: disable log
                        //e.printStackTrace();
                    }
                    finally {
                        dbManager.updateApplicationData(current.getUid(), current);
                        mutexNotificator.release();
                    }
                    FirewallService.cancelNotification(current.getUid());
                    //Refresh
                    sendRefreshCountNotifiedBroadcast();
                }
            });
            allowInternet.setOnTouchListener(viewOnTouchListener());
            blockInternet.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    current.setInternet(false);
                    current.setInteract(true);
                    current.setNotified(false);
                    try {
                        mutexNotificator.acquire();
                        if(current.getUid() == notificationMessageUid)
                            notificationMessage = null;
                    } catch (InterruptedException e) {
                        //TODO: disable log
                        //e.printStackTrace();
                    }
                    finally {
                        dbManager.updateApplicationData(current.getUid(), current);
                        mutexNotificator.release();
                    }
                    FirewallService.cancelNotification(current.getUid());
                    //Refresh
                    sendRefreshCountNotifiedBroadcast();
                }
            });
            blockInternet.setOnTouchListener(viewOnTouchListener());
        }
        return convertView;
    }

    private void sendRefreshCountNotifiedBroadcast () {
        Intent intent = new Intent(REFRESH_COUNT_NOTIFIED_APPS_ACTION);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }

    @Override
    public boolean hasDetails() {
        return true;
    }

    @Override
    public List<ApplicationDetails> getContent() {
        content = dbManager.getNotifiedAppDetails(query);
        return content;
    }

    @Override
    public int getCount() {
        return FirewallService.mapPackageNotified.size();
    }

    @Override
    public String getName(Context context) {
        return context.getString(R.string.app_list_request);
    }
}

