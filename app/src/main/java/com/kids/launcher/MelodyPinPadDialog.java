package com.kids.launcher;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.ToneGenerator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class MelodyPinPadDialog {

    public interface OnPinVerifiedListener {
        void onVerified();
    }

    public static void show(Context context, String title, OnPinVerifiedListener listener) {
        show(context, title, null, listener);
    }

    public static void show(Context context, String title, String subtitle, OnPinVerifiedListener listener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_builtin_pinpad);
        dialog.setCancelable(true);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }

        PreferencesManager prefs = new PreferencesManager(context);
        StringBuilder currentPin = new StringBuilder();

        TextView tvTitle = dialog.findViewById(R.id.tv_pinpad_subtitle);
        if (subtitle != null && tvTitle != null) {
            tvTitle.setText(subtitle);
        }

        TextView tvError = dialog.findViewById(R.id.tv_pinpad_error);
        View layoutDots = dialog.findViewById(R.id.layout_pin_dots);
        View[] dots = new View[]{
                dialog.findViewById(R.id.dot_1),
                dialog.findViewById(R.id.dot_2),
                dialog.findViewById(R.id.dot_3),
                dialog.findViewById(R.id.dot_4)
        };

        Runnable updateDots = () -> {
            int len = currentPin.length();
            for (int i = 0; i < dots.length; i++) {
                if (dots[i] != null) {
                    if (i < len) {
                        dots[i].setBackgroundResource(R.drawable.bg_melody_chip_selected);
                    } else {
                        dots[i].setBackgroundResource(R.drawable.bg_melody_chip_unselected);
                    }
                }
            }
        };

        updateDots.run();

        int[] numButtonIds = new int[]{
                R.id.btn_num_0, R.id.btn_num_1, R.id.btn_num_2, R.id.btn_num_3, R.id.btn_num_4,
                R.id.btn_num_5, R.id.btn_num_6, R.id.btn_num_7, R.id.btn_num_8, R.id.btn_num_9
        };

        View.OnClickListener numListener = v -> {
            if (currentPin.length() >= 4) return;
            Button b = (Button) v;
            currentPin.append(b.getText().toString());
            if (tvError != null) tvError.setVisibility(View.INVISIBLE);
            updateDots.run();

            if (currentPin.length() == 4) {
                String entered = currentPin.toString();
                if (entered.equals(prefs.getPin())) {
                    try {
                        ToneGenerator tone = new ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80);
                        tone.startTone(ToneGenerator.TONE_PROP_ACK, 200);
                    } catch (Exception ignored) {}

                    dialog.dismiss();
                    if (listener != null) {
                        listener.onVerified();
                    }
                } else {
                    try {
                        ToneGenerator tone = new ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80);
                        tone.startTone(ToneGenerator.TONE_PROP_NACK, 250);
                    } catch (Exception ignored) {}

                    if (tvError != null) tvError.setVisibility(View.VISIBLE);

                    if (layoutDots != null) {
                        ObjectAnimator shake = ObjectAnimator.ofFloat(layoutDots, "translationX",
                                0, 20, -20, 16, -16, 8, -8, 0);
                        shake.setDuration(400);
                        shake.start();
                    }

                    layoutDots.postDelayed(() -> {
                        currentPin.setLength(0);
                        updateDots.run();
                    }, 400);
                }
            }
        };

        for (int id : numButtonIds) {
            View btn = dialog.findViewById(id);
            if (btn != null) btn.setOnClickListener(numListener);
        }

        View btnClear = dialog.findViewById(R.id.btn_num_clear);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                currentPin.setLength(0);
                if (tvError != null) tvError.setVisibility(View.INVISIBLE);
                updateDots.run();
            });
        }

        View btnBackspace = dialog.findViewById(R.id.btn_num_backspace);
        if (btnBackspace != null) {
            btnBackspace.setOnClickListener(v -> {
                if (currentPin.length() > 0) {
                    currentPin.deleteCharAt(currentPin.length() - 1);
                    if (tvError != null) tvError.setVisibility(View.INVISIBLE);
                    updateDots.run();
                }
            });
        }

        View btnCancel = dialog.findViewById(R.id.btn_pinpad_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
}
