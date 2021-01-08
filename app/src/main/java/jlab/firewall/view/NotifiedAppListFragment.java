package jlab.firewall.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
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

/**
 * Created by Javier on 02/01/2021.
 */

public class NotifiedAppListFragment extends AppListFragment {

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
            name.setText(getSpannableFromText(current.getNames(), ',', colorsSpannable));
            Bitmap bmInCache = Utils.getIconForAppInCache(current.getPrincipalPackName());
            if(bmInCache != null)
                Glide.with(icon).asBitmap().load(bmInCache).into(icon);
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
                    appDbMgr.updateApplicationData(current.getUid(), current);
                    FirewallService.cancelNotification(current.getUid());
                    reload();
                }
            });
            allowInternet.setOnTouchListener(viewOnTouchListener());
            blockInternet.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    current.setInternet(false);
                    current.setInteract(true);
                    current.setNotified(false);
                    appDbMgr.updateApplicationData(current.getUid(), current);
                    FirewallService.cancelNotification(current.getUid());
                    reload();
                }
            });
            blockInternet.setOnTouchListener(viewOnTouchListener());
        }
        return convertView;
    }

    @Override
    public boolean hasDetails() {
        return true;
    }

    @Override
    public List<ApplicationDetails> getContent() {
        content = appDbMgr.getNotifiedAppDetails();
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

