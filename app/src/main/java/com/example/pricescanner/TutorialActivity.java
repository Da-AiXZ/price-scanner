package com.example.pricescanner;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.List;

public class TutorialActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "settings";
    public static final String PREF_TUTORIAL_SEEN = "tutorial_seen";

    private static final Interpolator EASE =
            new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    private static final class Step {
        final int iconRes;
        final int accentColor;
        final int chapterRes;
        final int titleRes;
        final int bodyRes;
        final int tipRes;

        Step(int iconRes, int accentColor, int chapterRes,
             int titleRes, int bodyRes, int tipRes) {
            this.iconRes = iconRes;
            this.accentColor = accentColor;
            this.chapterRes = chapterRes;
            this.titleRes = titleRes;
            this.bodyRes = bodyRes;
            this.tipRes = tipRes;
        }
    }

    private final List<Step> steps = new ArrayList<>();

    private ScrollView scroll;
    private View card;
    private View iconBg;
    private ImageView icon;
    private TextView chapter;
    private TextView title;
    private TextView body;
    private View tipBox;
    private TextView tipLabel;
    private TextView tipText;
    private TextView stepText;
    private TextView skip;
    private View prev;
    private View next;
    private TextView nextLabel;
    private ProgressBar progress;
    private SharedPreferences preferences;

    private int index;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }
        setContentView(R.layout.activity_tutorial);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setupInsets();
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        bindViews();
        buildSteps();
        progress.setMax(steps.size());

        if (savedInstanceState != null) {
            index = savedInstanceState.getInt("tutorial_index", 0);
        }
        showStep(Math.max(0, Math.min(index, steps.size() - 1)), 0, false);

        prev.setOnClickListener(v -> {
            if (index > 0) {
                showStep(index - 1, -1, true);
            }
        });
        next.setOnClickListener(v -> {
            if (index >= steps.size() - 1) {
                finishTutorial();
            } else {
                showStep(index + 1, 1, true);
            }
        });
        skip.setOnClickListener(v -> finishTutorial());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (index > 0) {
                    showStep(index - 1, -1, true);
                } else {
                    finishTutorial();
                }
            }
        });
    }

    private void setupInsets() {
        View root = findViewById(R.id.tutorial_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    dpInt(18),
                    bars.top + dpInt(4),
                    dpInt(18),
                    bars.bottom + dpInt(14));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void bindViews() {
        scroll = findViewById(R.id.t_scroll);
        card = findViewById(R.id.t_card);
        iconBg = findViewById(R.id.t_icon_bg);
        icon = findViewById(R.id.t_icon);
        chapter = findViewById(R.id.t_chapter);
        title = findViewById(R.id.t_title);
        body = findViewById(R.id.t_body);
        tipBox = findViewById(R.id.t_tip);
        tipLabel = findViewById(R.id.t_tip_label);
        tipText = findViewById(R.id.t_tip_text);
        stepText = findViewById(R.id.t_step);
        skip = findViewById(R.id.t_skip);
        prev = findViewById(R.id.t_prev);
        next = findViewById(R.id.t_next);
        nextLabel = findViewById(R.id.t_next_label);
        progress = findViewById(R.id.t_progress);
    }

    private void buildSteps() {
        steps.clear();
        steps.add(new Step(
                R.drawable.ic_scan, color(R.color.blue),
                R.string.tutorial_chapter_1, R.string.tutorial_welcome_title,
                R.string.tutorial_welcome_body, R.string.tutorial_welcome_tip));
        steps.add(new Step(
                R.drawable.ic_info, color(R.color.orange),
                R.string.tutorial_chapter_2, R.string.tutorial_permission_title,
                R.string.tutorial_permission_body, R.string.tutorial_permission_tip));
        steps.add(new Step(
                R.drawable.ic_scan, color(R.color.blue),
                R.string.tutorial_chapter_3, R.string.tutorial_scan_title,
                R.string.tutorial_scan_body, R.string.tutorial_scan_tip));
        steps.add(new Step(
                R.drawable.ic_check, color(R.color.green),
                R.string.tutorial_chapter_3, R.string.tutorial_result_title,
                R.string.tutorial_result_body, R.string.tutorial_result_tip));
        steps.add(new Step(
                R.drawable.ic_plus, color(R.color.orange),
                R.string.tutorial_chapter_3, R.string.tutorial_notfound_title,
                R.string.tutorial_notfound_body, R.string.tutorial_notfound_tip));
        steps.add(new Step(
                R.drawable.ic_bolt, color(R.color.orange),
                R.string.tutorial_chapter_3, R.string.tutorial_trouble_title,
                R.string.tutorial_trouble_body, R.string.tutorial_trouble_tip));
        steps.add(new Step(
                R.drawable.ic_cart, color(R.color.blue),
                R.string.tutorial_chapter_4, R.string.tutorial_cart_title,
                R.string.tutorial_cart_body, R.string.tutorial_cart_tip));
        steps.add(new Step(
                R.drawable.ic_check, color(R.color.green),
                R.string.tutorial_chapter_4, R.string.tutorial_checkout_title,
                R.string.tutorial_checkout_body, R.string.tutorial_checkout_tip));
        steps.add(new Step(
                R.drawable.ic_search, color(R.color.blue),
                R.string.tutorial_chapter_5, R.string.tutorial_library_title,
                R.string.tutorial_library_body, R.string.tutorial_library_tip));
        steps.add(new Step(
                R.drawable.ic_plus, color(R.color.green),
                R.string.tutorial_chapter_5, R.string.tutorial_add_title,
                R.string.tutorial_add_body, R.string.tutorial_add_tip));
        steps.add(new Step(
                R.drawable.ic_chart, color(R.color.blue),
                R.string.tutorial_chapter_6, R.string.tutorial_data_title,
                R.string.tutorial_data_body, R.string.tutorial_data_tip));
        steps.add(new Step(
                R.drawable.ic_export, color(R.color.orange),
                R.string.tutorial_chapter_6, R.string.tutorial_backup_title,
                R.string.tutorial_backup_body, R.string.tutorial_backup_tip));
        steps.add(new Step(
                R.drawable.ic_check, color(R.color.green),
                R.string.tutorial_chapter_7, R.string.tutorial_done_title,
                R.string.tutorial_done_body, R.string.tutorial_done_tip));
    }

    private void showStep(int newIndex, int direction, boolean animate) {
        if (steps.isEmpty()) {
            return;
        }
        index = Math.max(0, Math.min(newIndex, steps.size() - 1));
        Step step = steps.get(index);

        icon.setImageResource(step.iconRes);
        icon.setImageTintList(ColorStateList.valueOf(step.accentColor));
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setShape(GradientDrawable.OVAL);
        iconBackground.setColor(withAlpha(step.accentColor, 26));
        iconBg.setBackground(iconBackground);

        chapter.setText(step.chapterRes);
        chapter.setTextColor(step.accentColor);
        title.setText(step.titleRes);
        body.setText(step.bodyRes);
        tipText.setText(step.tipRes);
        tipLabel.setTextColor(step.accentColor);
        GradientDrawable tipBackground = new GradientDrawable();
        tipBackground.setColor(withAlpha(step.accentColor, 20));
        tipBackground.setCornerRadius(dp(14));
        tipBox.setBackground(tipBackground);

        progress.setProgress(index + 1);
        stepText.setText(getString(R.string.tutorial_step_format, index + 1, steps.size()));

        prev.setVisibility(index == 0 ? View.GONE : View.VISIBLE);
        boolean last = index == steps.size() - 1;
        skip.setVisibility(last ? View.GONE : View.VISIBLE);
        nextLabel.setText(last ? R.string.tutorial_finish : R.string.tutorial_next);
        next.setContentDescription(getString(
                last ? R.string.tutorial_finish : R.string.tutorial_next));

        if (animate) {
            card.animate().cancel();
            card.setAlpha(0f);
            card.setTranslationX(direction * dp(28));
            card.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(240)
                    .setInterpolator(EASE)
                    .start();
        } else {
            card.setAlpha(1f);
            card.setTranslationX(0f);
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void finishTutorial() {
        preferences.edit()
                .putBoolean(PREF_TUTORIAL_SEEN, true)
                .apply();
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tutorial_index", index);
    }

    private int color(int colorRes) {
        return ContextCompat.getColor(this, colorRes);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int dpInt(float value) {
        return (int) (dp(value) + 0.5f);
    }
}
