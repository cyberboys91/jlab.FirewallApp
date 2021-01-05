package jlab.firewall.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.util.ArrayList;
import jlab.firewall.R;
import jlab.firewall.vpn.FirewallService;
import lecho.lib.hellocharts.formatter.AxisValueFormatter;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.view.LineChartView;
import static jlab.firewall.vpn.FirewallService.isRunning;
import static jlab.firewall.vpn.FirewallService.trafficDataDownSpeedPoints;
import static jlab.firewall.vpn.FirewallService.trafficDataUpSpeedPoints;
import static jlab.firewall.vpn.Utils.getSizeString;
import static jlab.firewall.vpn.Utils.getTimeString;
import static jlab.firewall.vpn.Utils.showSnackBar;

/**
 * Created by Javier on 28/12/2020.
 */

public class HomeFragment extends Fragment {

    private Button vpnButton;
    public static Runnable startVPN;
    private final int ONE_KB = 1024;
    private LineChartView chart;
    private LineChartData chartData;
    private static final int COLOR_GREEN = Color.GREEN, COLOR_NEUTRAL = Color.parseColor("#2389af"),
        COLOR_TRANSPARENT = Color.argb(0, 255, 255, 255);
    private BroadcastReceiver onRefreshTrafficDataReceiver = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(FirewallService.REFRESH_TRAFFIC_DATA))
                    addChartData();
        }
    };

    private void addChartData() {
        if (chart != null) {
            ArrayList<Line> lines = new ArrayList<>();
            lines.add(new Line(trafficDataUpSpeedPoints).setColor(COLOR_NEUTRAL)
                    .setPointColor(COLOR_TRANSPARENT).setStrokeWidth(1).setFilled(true));
            lines.add(new Line(trafficDataDownSpeedPoints).setColor(COLOR_GREEN)
                    .setPointColor(COLOR_TRANSPARENT).setStrokeWidth(1).setFilled(true));
            chartData.setLines(lines);
            chart.setLineChartData(chartData);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        vpnButton = view.findViewById(R.id.btMgrVpn);
        vpnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (startVPN != null && !FirewallService.isRunning())
                    startVPN.run();
            }
        });
        chart = view.findViewById(R.id.chart);
        chart.setInteractive(false);
        chartData = new LineChartData();
        chartData.setAxisXBottom(new Axis().setName(getString(R.string.time))
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
        chartData.setAxisYRight(new Axis().setName("Bps").setInside(true)
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
        addChartData();
        return view;
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
        FirewallService.setPostRunning(new FirewallService.IPostRunningListener() {
            @Override
            public void run(boolean running) {
                try {
                    showSnackBar(running
                            ? R.string.running_vpn_service
                            : R.string.shutdown_vpn_service, vpnButton);
                    enableButton(!running);
                } catch (Exception ignored) {
                    ignored.printStackTrace();
                }
            }
        });
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(onRefreshTrafficDataReceiver,
                new IntentFilter(FirewallService.REFRESH_TRAFFIC_DATA));
    }

    public void enableButton(boolean enable) {
        if (vpnButton != null) {
            vpnButton.setEnabled(enable);
            if (enable) {
                vpnButton.setText(R.string.start_vpn);
            } else {
                vpnButton.setText(R.string.stop_vpn);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(onRefreshTrafficDataReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        enableButton(!isRunning());
    }
}
