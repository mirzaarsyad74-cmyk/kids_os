package com.kids.launcher;

import android.app.Dialog;
import android.content.Context;
import android.media.ToneGenerator;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

public class MelodyEyeBreakDialog {

    private static final String[] PROMPTS = {
            "Blink your eyes gently and take a deep breath ✨",
            "Look out the window at green trees or the blue sky! 🌳",
            "Roll your eyes in a slow, gentle circle! 🎈",
            "Look as far away as you can! 🔭",
            "Almost done! You're taking great care of your eyes! 🌸"
    };

    public interface OnEyeBreakFinishedListener {
        void onFinished();
    }

    public static void show(Context context, OnEyeBreakFinishedListener listener) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_eye_break);
        dialog.setCancelable(false);

        ProgressBar pb = dialog.findViewById(R.id.pb_eye_break);
        TextView tvCountdown = dialog.findViewById(R.id.tv_eye_countdown);
        TextView tvPrompt = dialog.findViewById(R.id.tv_eye_prompt);
        View btnOverride = dialog.findViewById(R.id.btn_eye_override);

        PreferencesManager prefs = new PreferencesManager(context);

        CountDownTimer timer = new CountDownTimer(20000, 1000) {
            int secondsLeft = 20;

            @Override
            public void onTick(long millisUntilFinished) {
                secondsLeft = (int) (millisUntilFinished / 1000);
                if (pb != null) pb.setProgress(secondsLeft);
                if (tvCountdown != null) tvCountdown.setText(String.valueOf(secondsLeft));

                int promptIdx = (20 - secondsLeft) / 4;
                if (promptIdx < PROMPTS.length && tvPrompt != null) {
                    tvPrompt.setText(PROMPTS[promptIdx]);
                }
            }

            @Override
            public void onFinish() {
                if (tvCountdown != null) tvCountdown.setText("✨");
                if (tvPrompt != null) tvPrompt.setText("Great job! Your eyes are happy and rested! 🌸");

                try {
                    ToneGenerator tone = new ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 90);
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400);
                } catch (Exception ignored) {}

                tvPrompt.postDelayed(() -> {
                    try {
                        if (dialog.isShowing()) dialog.dismiss();
                    } catch (Exception ignored) {}
                    if (listener != null) listener.onFinished();
                }, 1200);
            }
        };

        btnOverride.setOnClickListener(v -> {
            MelodyPinPadDialog.show(context, "Parent Override 🔑", "Enter parental PIN to bypass eye break", () -> {
                timer.cancel();
                try {
                    if (dialog.isShowing()) dialog.dismiss();
                } catch (Exception ignored) {}
                if (listener != null) listener.onFinished();
                Toast.makeText(context, "Eye break bypassed by parent", Toast.LENGTH_SHORT).show();
            });
        });

        timer.start();
        dialog.show();
    }
}
