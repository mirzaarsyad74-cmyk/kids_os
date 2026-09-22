package com.kids.launcher;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DecimalFormat;

/**
 * Dedicated My Melody Calculator for Kids.
 * Cute pastel buttons, tactile feedback, big display, and kid-friendly arithmetic.
 */
public class MelodyCalculatorActivity extends AppCompatActivity {

    private TextView tvHistory;
    private TextView tvResult;
    private TextView tvSparkle;

    private String currentInput = "0";
    private double firstOperand = 0;
    private String pendingOperator = "";
    private boolean isNewNumber = true;

    private ToneGenerator toneGen;
    private final DecimalFormat format = new DecimalFormat("#,###.########");

    private final String[] SWEET_ENCOURAGEMENTS = {
            "✨🌸 Super smart! 🌸✨",
            "🎀 Math is so fun! 🎀",
            "🍓 Great job, princess! 🍓",
            "💖 You can do it! 💖",
            "🌈 Smart thinking! 🌈"
    };
    private int encouragementIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setWindowUiFlags();
        setContentView(R.layout.activity_melody_calculator);

        try {
            toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 40);
        } catch (Exception ignored) {}

        initViews();
    }

    private void setWindowUiFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void initViews() {
        findViewById(R.id.btn_calc_back).setOnClickListener(v -> finish());

        tvHistory = findViewById(R.id.tv_calc_history);
        tvResult = findViewById(R.id.tv_calc_result);
        tvSparkle = findViewById(R.id.tv_calc_sparkle);

        // Digits
        int[] digitIds = {
                R.id.btn_calc_0, R.id.btn_calc_1, R.id.btn_calc_2,
                R.id.btn_calc_3, R.id.btn_calc_4, R.id.btn_calc_5,
                R.id.btn_calc_6, R.id.btn_calc_7, R.id.btn_calc_8, R.id.btn_calc_9
        };

        for (int i = 0; i < digitIds.length; i++) {
            final int digit = i;
            findViewById(digitIds[i]).setOnClickListener(v -> {
                playClickTone();
                animateButton(v);
                appendDigit(String.valueOf(digit));
            });
        }

        findViewById(R.id.btn_calc_dot).setOnClickListener(v -> {
            playClickTone();
            animateButton(v);
            appendDot();
        });

        findViewById(R.id.btn_calc_clear).setOnClickListener(v -> {
            playClickTone();
            animateButton(v);
            clearAll();
        });

        findViewById(R.id.btn_calc_backspace).setOnClickListener(v -> {
            playClickTone();
            animateButton(v);
            backspace();
        });

        findViewById(R.id.btn_calc_add).setOnClickListener(v -> {
            playClickTone();
            animateButton(v);
            setOperator("+");
        });

        findViewById(R.id.btn_calc_sub).setOnClickListener(v -> {
            playClickTone();
            animateButton(v);
            setOperator("-");
        });

        findViewById(R.id.btn_calc_mul).setOnClickListener(v -> {
            playClickTone();
            animateButton(v);
            setOperator("×");
        });

        findViewById(R.id.btn_calc_div).setOnClickListener(v -> {
            playClickTone();
            animateButton(v);
            setOperator("÷");
        });

        findViewById(R.id.btn_calc_percent).setOnClickListener(v -> {
            playClickTone();
            animateButton(v);
            applyPercent();
        });

        findViewById(R.id.btn_calc_equals).setOnClickListener(v -> {
            playClickTone();
            animateButton(v);
            calculateEquals();
        });

        findViewById(R.id.btn_calc_heart).setOnClickListener(v -> {
            animateButton(v);
            encouragementIndex = (encouragementIndex + 1) % SWEET_ENCOURAGEMENTS.length;
            tvSparkle.setText(SWEET_ENCOURAGEMENTS[encouragementIndex]);
            tvSparkle.setScaleX(1.2f);
            tvSparkle.setScaleY(1.2f);
            tvSparkle.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
            Toast.makeText(this, "💖 You are doing wonderful math! 🌸", Toast.LENGTH_SHORT).show();
        });
    }

    private void appendDigit(String digit) {
        if (isNewNumber || "0".equals(currentInput)) {
            currentInput = digit;
            isNewNumber = false;
        } else {
            if (currentInput.length() < 12) {
                currentInput += digit;
            }
        }
        tvResult.setText(currentInput);
    }

    private void appendDot() {
        if (isNewNumber) {
            currentInput = "0.";
            isNewNumber = false;
        } else if (!currentInput.contains(".")) {
            currentInput += ".";
        }
        tvResult.setText(currentInput);
    }

    private void clearAll() {
        currentInput = "0";
        firstOperand = 0;
        pendingOperator = "";
        isNewNumber = true;
        tvHistory.setText("");
        tvResult.setText("0");
    }

    private void backspace() {
        if (isNewNumber || currentInput.length() <= 1) {
            currentInput = "0";
            isNewNumber = true;
        } else {
            currentInput = currentInput.substring(0, currentInput.length() - 1);
        }
        tvResult.setText(currentInput);
    }

    private void setOperator(String op) {
        try {
            if (!pendingOperator.isEmpty() && !isNewNumber) {
                calculateIntermediate();
            } else {
                firstOperand = Double.parseDouble(currentInput);
            }
            pendingOperator = op;
            isNewNumber = true;
            tvHistory.setText(format.format(firstOperand) + " " + op);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void calculateIntermediate() {
        double secondOperand = Double.parseDouble(currentInput);
        double res = compute(firstOperand, secondOperand, pendingOperator);
        firstOperand = res;
        currentInput = format.format(res);
        tvResult.setText(currentInput);
    }

    private void calculateEquals() {
        if (pendingOperator.isEmpty()) return;
        try {
            double secondOperand = Double.parseDouble(currentInput);
            double res = compute(firstOperand, secondOperand, pendingOperator);

            tvHistory.setText(format.format(firstOperand) + " " + pendingOperator + " " + format.format(secondOperand) + " =");
            currentInput = format.format(res);
            tvResult.setText(currentInput);

            pendingOperator = "";
            isNewNumber = true;

            // Update encouragement
            encouragementIndex = (encouragementIndex + 1) % SWEET_ENCOURAGEMENTS.length;
            tvSparkle.setText(SWEET_ENCOURAGEMENTS[encouragementIndex]);
        } catch (ArithmeticException ae) {
            tvResult.setText("Error");
            Toast.makeText(this, "Cannot divide by 0 🌸", Toast.LENGTH_SHORT).show();
            clearAll();
        } catch (Exception e) {
            tvResult.setText("Error");
            clearAll();
        }
    }

    private void applyPercent() {
        try {
            double val = Double.parseDouble(currentInput) / 100.0;
            currentInput = format.format(val);
            tvResult.setText(currentInput);
        } catch (Exception ignored) {}
    }

    private double compute(double a, double b, String op) {
        switch (op) {
            case "+": return a + b;
            case "-": return a - b;
            case "×": return a * b;
            case "÷":
                if (b == 0) throw new ArithmeticException("Division by zero");
                return a / b;
            default: return b;
        }
    }

    private void playClickTone() {
        if (toneGen != null) {
            try {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 30);
            } catch (Exception ignored) {}
        }
    }

    private void animateButton(View v) {
        v.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .setDuration(70)
                .withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90).start())
                .start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) {
            toneGen.release();
            toneGen = null;
        }
    }
}
