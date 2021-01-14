package jlab.firewall.view;

import android.os.Bundle;
import jlab.firewall.R;
import android.view.View;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import android.view.ViewGroup;
import android.content.Intent;
import android.graphics.Color;
import android.content.Context;
import android.view.LayoutInflater;
import android.content.IntentFilter;
import androidx.fragment.app.Fragment;
import android.content.BroadcastReceiver;
import androidx.cardview.widget.CardView;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.widget.TextView;
import jlab.firewall.vpn.FirewallService;
import lecho.lib.hellocharts.formatter.AxisValueFormatter;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.view.LineChartView;
import static jlab.firewall.vpn.FirewallService.downByteTotal;
import static jlab.firewall.vpn.FirewallService.isRunning;
import static jlab.firewall.vpn.FirewallService.trafficDataDownSpeedPoints;
import static jlab.firewall.vpn.FirewallService.trafficDataUpSpeedPoints;
import static jlab.firewall.vpn.FirewallService.upByteTotal;
import static jlab.firewall.vpn.Utils.getSizeString;
import static jlab.firewall.vpn.Utils.getTimeString;

/**
 * Created by Javier on 28/12/2020.
 */

public class HomeFragment extends Fragment {

    private CardView vpnButton;
    public static Runnable startVPN;
    private final int ONE_KB = 1024;
    private LineChartView chart;
    private LineChartData chartData;
    private TextView tvTextButton, tvUpByteTotal, tvDownByteTotal;
    private Semaphore mutexRefreshTraffic = new Semaphore(1);
    private static final int COLOR_GREEN = Color.GREEN, COLOR_NEUTRAL = Color.parseColor("#2389af"),
        COLOR_TRANSPARENT = Color.argb(0, 255, 255, 255);
    private BroadcastReceiver onFirewallChangeStatusReceiver = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case FirewallService.REFRESH_TRAFFIC_DATA:
                    refreshTrafficData();
                    break;
                case FirewallService.STARTED_VPN_ACTION:
                    changeStateButton(false);
                    refreshTrafficData();
                    break;
                case FirewallService.STOPPED_VPN_ACTION:
                    changeStateButton(true);
                    refreshTrafficData();
                    break;
                case FirewallService.NOT_PREPARED_VPN_ACTION:
                    changeStateButton(true);
                    break;
                default:
                    break;
            }
        }
    };

    private void refreshTrafficData() {
        try {
            mutexRefreshTraffic.acquire();
            tvUpByteTotal.setText(getSizeString((double) upByteTotal.get(),
                    upByteTotal.get() > ONE_KB ? 2 : 0));
            tvDownByteTotal.setText(getSizeString((double) downByteTotal.get(),
                    upByteTotal.get() > ONE_KB ? 2 : 0));
            if (chart != null) {
                ArrayList<Line> lines = new ArrayList<>();

                //Creando copia para evitar la modificación concurrente
                ArrayList<PointValue> pointsLineUpSpeed = new ArrayList<>();
                pointsLineUpSpeed.addAll(trafficDataUpSpeedPoints);
                ArrayList<PointValue> pointsLineDownSpeed = new ArrayList<>();
                pointsLineDownSpeed.addAll(trafficDataDownSpeedPoints);
                //.
                lines.add(new Line(pointsLineUpSpeed).setColor(COLOR_NEUTRAL)
                        .setPointColor(COLOR_TRANSPARENT).setStrokeWidth(1).setFilled(true));
                lines.add(new Line(pointsLineDownSpeed).setColor(COLOR_GREEN)
                        .setPointColor(COLOR_TRANSPARENT).setStrokeWidth(1).setFilled(true));
                chartData.setLines(lines);
                chart.setLineChartData(chartData);
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        } finally {
            mutexRefreshTraffic.release();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        tvTextButton = view.findViewById(R.id.tvTextButton);
        vpnButton = view.findViewById(R.id.btMgrVpn);
        vpnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!FirewallService.isRunning())
                    startFirewallService();
                else
                    stopFirewallService();
            }
        });
        tvUpByteTotal = view.findViewById(R.id.tvUpByteTotal);
        tvDownByteTotal = view.findViewById(R.id.tvDownByteTotal);
        vpnButton.setOnTouchListener(SwitchMultiOptionButton.viewOnTouchListener());
        chart = view.findViewById(R.id.chart);
        chart.setInteractive(false);
        chartData = new LineChartData();
        chartData.setAxisXBottom(new Axis()
                .setFormatter(new AxisValueFormatter() {
                    @Override
                    public int formatValueForManualAxis(char[] chars, AxisValue axisValue) {
                        return 0;
                    }

                    @Override
                    public int formatValueForAutoGeneratedAxis(char[] chars, float v, int i) {
                        String formatted = getTimeString(v);
                        return resultFormatValueForAutoGeneratedAxis(formatted, chars,
                                formatted.length() - 1);
                    }
                }));
        chartData.setAxisYLeft(new Axis().setAutoGenerated(false));
        chartData.setAxisYRight(new Axis().setInside(true)
                .setFormatter(new AxisValueFormatter() {
            @Override
            public int formatValueForManualAxis(char[] chars, AxisValue axisValue) {
                return 0;
            }

            @Override
            public int formatValueForAutoGeneratedAxis(char[] chars, float v, int i) {
                String formatted = getSizeString(v, v >= ONE_KB ? 2 : 0);
                return resultFormatValueForAutoGeneratedAxis(formatted
                        , chars, formatted.length() - 2) - 1;
            }
        }));
        chartData.setAxisXTop(new Axis().setAutoGenerated(false));
        refreshTrafficData();
        return view;
    }

    private void stopFirewallService() {
        tvTextButton.setText(R.string.stopping_vpn);
        vpnButton.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                LocalBroadcastManager.getInstance(getContext())
                        .sendBroadcast(new Intent(FirewallService.STOP_VPN_ACTION));
            }
        }).start();
    }

    private void startFirewallService() {
        if (startVPN != null && !FirewallService.isRunning()) {
            vpnButton.setEnabled(false);
            tvTextButton.setText(R.string.starting_vpn);
            startVPN.run();
        }
    }

    private int resultFormatValueForAutoGeneratedAxis (String result, char[] chars, int lastIndexResult) {
        int i = chars.length - 1;
        for (int j = lastIndexResult;
             j >= 0 && i >= 0; j--, i--) {
            chars[i] = result.charAt(j);
        }
        return result.length();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter intentFilter = new IntentFilter(FirewallService.REFRESH_TRAFFIC_DATA);
        intentFilter.addAction(FirewallService.STARTED_VPN_ACTION);
        intentFilter.addAction(FirewallService.STOPPED_VPN_ACTION);
        intentFilter.addAction(FirewallService.NOT_PREPARED_VPN_ACTION);
        LocalBroadcastManager.getInstance(getContext())
                .registerReceiver(onFirewallChangeStatusReceiver, intentFilter);
    }

    public void changeStateButton(boolean stopped) {
        try {
            if (vpnButton != null) {
                if (stopped) {
                    tvTextButton.setText(R.string.start_vpn);
                    vpnButton.setCardBackgroundColor(getResources().getColor(R.color.neutral));
                } else {
                    tvTextButton.setText(R.string.stop_vpn);
                    vpnButton.setCardBackgroundColor(getResources().getColor(R.color.yellow));
                }
                vpnButton.setEnabled(true);
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(onFirewallChangeStatusReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        changeStateButton(!isRunning());
    }
}
