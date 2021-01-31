package jlab.firewall.activity;

import jlab.firewall.R;
import jlab.firewall.vpn.FirewallService;

import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.os.Looper;
import android.widget.ImageView;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.appcompat.app.AppCompatActivity;
import static jlab.firewall.vpn.FirewallService.loadAppData;


/**
 * Created by Javier on 7/4/2020.
 */

public class SplashActivity extends AppCompatActivity {
    private ImageView ivIcon;
    private boolean finish = false;
    private final long timeSleep = 250;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(timeSleep);
                    } catch (InterruptedException e) {
                        //TODO: disable log
                        //e.printStackTrace();
                    }
                    if(!finish) {
                        Intent intent = new Intent(getBaseContext(), MainActivity.class);
                        startActivity(intent);
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                        finish();
                    }
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_screen);
        ivIcon = (ImageView) findViewById(R.id.ivIconInSplash);
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.beat);
        ivIcon.startAnimation(animation);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (!FirewallService.isRunning())
                            loadAppData(getBaseContext());
                        runnable.run();
                    }
                }).start();
            }
        }, 0);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        this.finish = true;
        finish();
    }
}