package com.kids.launcher;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {

    private int currentStep = 1;
    private static final int TOTAL_STEPS = 5;

    private PreferencesManager prefs;

    private TextView tvStepIndicator;
    private TextView tvStepTitle;
    private TextView tvStepDesc;

    private LinearLayout layoutStep1;
    private LinearLayout layoutStep2;
    private LinearLayout layoutStep3;
    private LinearLayout layoutStep4;
    private LinearLayout layoutStep5;

    private EditText etChildName;
    private EditText etOnboardingPin;
    private RadioGroup rgOnboardingTime;
    private TextView tvSummaryName;

    private Button btnPrev;
    private Button btnNext;

    private String selectedAvatar = "🐰";
    private String selectedTheme = "pink";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreStockNavBar();
        setContentView(R.layout.activity_onboarding);

        prefs = new PreferencesManager(this);

        initViews();
        setupAvatarPickers();
        setupThemePickers();
        updateStepUi();
    }

    private void restoreStockNavBar() {
        if (getWindow() != null && getWindow().getDecorView() != null) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void initViews() {
        tvStepIndicator = findViewById(R.id.tv_step_indicator);
        tvStepTitle = findViewById(R.id.tv_step_title);
        tvStepDesc = findViewById(R.id.tv_step_desc);

        layoutStep1 = findViewById(R.id.layout_step_1);
        layoutStep2 = findViewById(R.id.layout_step_2);
        layoutStep3 = findViewById(R.id.layout_step_3);
        layoutStep4 = findViewById(R.id.layout_step_4);
        layoutStep5 = findViewById(R.id.layout_step_5);

        etChildName = findViewById(R.id.et_child_name);
        etOnboardingPin = findViewById(R.id.et_onboarding_pin);
        rgOnboardingTime = findViewById(R.id.rg_onboarding_time);
        tvSummaryName = findViewById(R.id.tv_summary_name);

        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);

        btnPrev.setOnClickListener(v -> {
            if (currentStep > 1) {
                currentStep--;
                updateStepUi();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentStep < TOTAL_STEPS) {
                if (currentStep == 1) {
                    String name = etChildName.getText().toString().trim();
                    if (name.isEmpty()) name = "Princess";
                    prefs.setKidName(name);
                } else if (currentStep == 4) {
                    String pin = etOnboardingPin.getText().toString().trim();
                    if (pin.length() == 4) {
                        prefs.setPin(pin);
                    }
                    int checkedId = rgOnboardingTime.getCheckedRadioButtonId();
                    if (checkedId == R.id.rb_onboarding_30m) {
                        prefs.setTimeLimitMinutes(30);
                    } else if (checkedId == R.id.rb_onboarding_60m) {
                        prefs.setTimeLimitMinutes(60);
                    } else {
                        prefs.setTimeLimitMinutes(0);
                    }
                }
                currentStep++;
                updateStepUi();
            } else {
                finishOnboarding();
            }
        });
    }

    private void setupAvatarPickers() {
        int[] ids = {
                R.id.btn_avatar_bunny, R.id.btn_avatar_kitty, R.id.btn_avatar_bear,
                R.id.btn_avatar_unicorn, R.id.btn_avatar_melody, R.id.btn_avatar_puppy
        };
        for (int id : ids) {
            TextView tv = findViewById(id);
            if (tv != null) {
                tv.setOnClickListener(v -> {
                    selectedAvatar = tv.getText().toString();
                    prefs.setAvatar(selectedAvatar);
                    for (int otherId : ids) {
                        TextView other = findViewById(otherId);
                        if (other != null) other.setAlpha(0.5f);
                    }
                    tv.setAlpha(1.0f);
                    Toast.makeText(this, "Selected " + selectedAvatar, Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    private void setupThemePickers() {
        findViewById(R.id.btn_theme_pink).setOnClickListener(v -> selectTheme("pink"));
        findViewById(R.id.btn_theme_lavender).setOnClickListener(v -> selectTheme("lavender"));
        findViewById(R.id.btn_theme_mint).setOnClickListener(v -> selectTheme("mint"));
        findViewById(R.id.btn_theme_peach).setOnClickListener(v -> selectTheme("peach"));
    }

    private void selectTheme(String theme) {
        selectedTheme = theme;
        prefs.setSelectedTheme(theme);
        Toast.makeText(this, "Theme set to " + theme + "!", Toast.LENGTH_SHORT).show();
    }

    private void updateStepUi() {
        tvStepIndicator.setText("🌸 Step " + currentStep + " of " + TOTAL_STEPS + " ✨");

        layoutStep1.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        layoutStep2.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        layoutStep3.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);
        layoutStep4.setVisibility(currentStep == 4 ? View.VISIBLE : View.GONE);
        layoutStep5.setVisibility(currentStep == 5 ? View.VISIBLE : View.GONE);

        btnPrev.setVisibility(currentStep > 1 ? View.VISIBLE : View.GONE);

        if (currentStep == 1) {
            tvStepTitle.setText("Welcome to Melody OS! 🌸");
            tvStepDesc.setText("What is your name, sweetie?");
            btnNext.setText("Next ➔");
        } else if (currentStep == 2) {
            tvStepTitle.setText("Pick Your Magical Avatar! 💖");
            tvStepDesc.setText("Tap your favorite mascot below:");
            btnNext.setText("Next ➔");
        } else if (currentStep == 3) {
            tvStepTitle.setText("Pick Your Pastel Theme! 🎨");
            tvStepDesc.setText("Choose the colors you love most:");
            btnNext.setText("Next ➔");
        } else if (currentStep == 4) {
            tvStepTitle.setText("Parent Zone Setup 🛡️");
            tvStepDesc.setText("For grown-ups: Configure security PIN & time limit.");
            btnNext.setText("Next ➔");
        } else if (currentStep == 5) {
            tvStepTitle.setText("All Set & Ready! 🚀✨");
            tvStepDesc.setText("Let the magical adventures begin!");
            tvSummaryName.setText("Welcome aboard, " + prefs.getKidName() + "! " + prefs.getAvatar());
            btnNext.setText("Start Playing! 🌸");
        }
    }

    private void finishOnboarding() {
        prefs.setOnboardingCompleted(true);
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.melody_app_open_enter, R.anim.melody_app_open_exit);
        finish();
    }
}
