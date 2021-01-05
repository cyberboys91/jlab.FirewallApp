package jlab.firewall.activity;

import jlab.firewall.R;
import jlab.firewall.vpn.FirewallService;

import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.widget.ImageView;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.support.v7.app.AppCompatActivity;
import static jlab.firewall.vpn.FirewallService.loadAppData;


/**
 * Created by Javier on 7/4/2020.
 */

public class SplashActivity extends AppCompatActivity {
    private ImageView ivIcon;
    private boolean finish = false;
    private final long timeSleep = 500;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(timeSleep);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
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

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (!FirewallService.isRunning())
                            loadAppData(SplashActivity.this, runnable);
                        else
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