package com.giftgram.messenger;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Анимированный сплэш GiftGram: логотип появляется, пульсирует и уступает место клиенту.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_MS = 1250L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.splash_logo);
        View halo = findViewById(R.id.splash_halo);
        TextView title = findViewById(R.id.splash_title);
        TextView tagline = findViewById(R.id.splash_tagline);
        View bar = findViewById(R.id.splash_bar);

        logo.setScaleX(0.6f);
        logo.setScaleY(0.6f);
        logo.setAlpha(0f);
        halo.setScaleX(0.4f);
        halo.setScaleY(0.4f);
        halo.setAlpha(0f);
        title.setAlpha(0f);
        title.setTranslationY(18f);
        tagline.setAlpha(0f);
        tagline.setTranslationY(14f);
        bar.setScaleX(0f);

        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(620).setInterpolator(new DecelerateInterpolator()).start();

        halo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(900).setInterpolator(new DecelerateInterpolator()).start();

        title.animate().alpha(1f).translationY(0f).setStartDelay(160).setDuration(560).start();
        tagline.animate().alpha(1f).translationY(0f).setStartDelay(300).setDuration(560).start();
        bar.animate().scaleX(1f).setStartDelay(120).setDuration(900)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();

        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(halo,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.35f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 0f));
        pulse.setDuration(1600);
        pulse.setStartDelay(500);
        pulse.setInterpolator(new DecelerateInterpolator());
        pulse.start();

        new Handler(Looper.getMainLooper()).postDelayed(this::go, SPLASH_MS);
    }

    private void go() {
        if (isFinishing()) {
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        Intent incoming = getIntent();
        if (incoming != null && incoming.getData() != null) {
            intent.setData(incoming.getData());
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
