package com.kids.launcher;

import android.content.Context;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MelodyKeyboardService extends InputMethodService {

    private LinearLayout layoutKeyboardRoot;
    private LinearLayout layoutLetters;
    private LinearLayout layoutSymbols;
    private LinearLayout layoutEmojis;

    private LinearLayout rowLetters1, rowLetters2, rowLetters3Mid;
    private LinearLayout rowSymbols1, rowSymbols2, rowSymbols3Mid;
    private LinearLayout layoutQuickEmojis;
    private GridLayout gridEmojis;

    private TextView btnShift;
    private TextView btnEnter, btnSymbolsEnter;

    private final List<TextView> letterKeyViews = new ArrayList<>();

    private int shiftState = 0; // 0 = lowercase, 1 = uppercase, 2 = caps lock
    private boolean isSymbolsMode = false;
    private boolean isEmojiMode = false;

    private AudioManager audioManager;
    private Vibrator vibrator;

    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private Runnable backspaceRunnable;

    private static final String[] ROW_1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
    private static final String[] ROW_2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
    private static final String[] ROW_3 = {"z", "x", "c", "v", "b", "n", "m"};

    private static final String[] SYM_ROW_1 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[] SYM_ROW_2 = {"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"};
    private static final String[] SYM_ROW_3 = {"/", "!", "?", ":", ";", "\"", "'", "="};

    private static final String[] QUICK_EMOJIS = {
            "🌸", "🎀", "💖", "🐱", "🐶", "🦄", "⭐", "😊", "🎉", "🍭", "✨", "🎈", "🍦", "🍰", "🍎", "🧸", "🍓", "🍧"
    };

    private static final String[] FULL_EMOJIS = {
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🥰", "😍", "🤩", "😘", "😋", "😛",
            "😜", "🤪", "🤗", "🤔", "🤭", "🤫", "🥳", "👍", "👏", "👋", "👧", "👦", "🐶", "🐱", "🦄", "🐼",
            "🐰", "🦁", "🐸", "🌸", "🌹", "🌺", "🌻", "🌈", "☀️", "⭐", "🌙", "🎈", "🎉", "🎁", "🍦", "🍰",
            "🍭", "🍬", "🍕", "🍔", "🚗", "🚀", "🎮", "🎵"
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public View onCreateInputView() {
        View view = getLayoutInflater().inflate(R.layout.view_melody_keyboard, null);
        layoutKeyboardRoot = view.findViewById(R.id.layout_keyboard_root);

        layoutLetters = view.findViewById(R.id.layout_letters);
        layoutSymbols = view.findViewById(R.id.layout_symbols);
        layoutEmojis = view.findViewById(R.id.layout_emojis);

        rowLetters1 = view.findViewById(R.id.row_letters_1);
        rowLetters2 = view.findViewById(R.id.row_letters_2);
        rowLetters3Mid = view.findViewById(R.id.row_letters_3_mid);

        rowSymbols1 = view.findViewById(R.id.row_symbols_1);
        rowSymbols2 = view.findViewById(R.id.row_symbols_2);
        rowSymbols3Mid = view.findViewById(R.id.row_symbols_3_mid);

        layoutQuickEmojis = view.findViewById(R.id.layout_quick_emojis);
        gridEmojis = view.findViewById(R.id.grid_emojis);

        btnShift = view.findViewById(R.id.btn_shift);
        btnEnter = view.findViewById(R.id.btn_enter);
        btnSymbolsEnter = view.findViewById(R.id.btn_symbols_enter);

        setupKeys();
        setupQuickEmojis();
        setupActionButtons(view);
        setupEmojiGrid();

        return view;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        showLettersMode();
        shiftState = 0;
        updateShiftUi();
        updateEnterKeyLabel(info);
    }

    private void updateEnterKeyLabel(EditorInfo info) {
        String label = "↵";
        if (info != null) {
            int action = info.imeOptions & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
            switch (action) {
                case EditorInfo.IME_ACTION_SEARCH:
                    label = "🔍";
                    break;
                case EditorInfo.IME_ACTION_GO:
                    label = "Go";
                    break;
                case EditorInfo.IME_ACTION_SEND:
                    label = "Send";
                    break;
                case EditorInfo.IME_ACTION_NEXT:
                    label = "Next";
                    break;
                case EditorInfo.IME_ACTION_DONE:
                    label = "Done";
                    break;
            }
        }
        if (btnEnter != null) btnEnter.setText(label);
        if (btnSymbolsEnter != null) btnSymbolsEnter.setText(label);
    }

    private void setupKeys() {
        letterKeyViews.clear();
        buildLetterRow(rowLetters1, ROW_1);
        buildLetterRow(rowLetters2, ROW_2);
        buildLetterRow(rowLetters3Mid, ROW_3);

        buildSymbolRow(rowSymbols1, SYM_ROW_1);
        buildSymbolRow(rowSymbols2, SYM_ROW_2);
        buildSymbolRow(rowSymbols3Mid, SYM_ROW_3);
    }

    private void buildLetterRow(LinearLayout container, String[] letters) {
        if (container == null) return;
        container.removeAllViews();
        for (String letter : letters) {
            TextView key = createKeyView(letter);
            letterKeyViews.add(key);
            container.addView(key);
        }
    }

    private void buildSymbolRow(LinearLayout container, String[] symbols) {
        if (container == null) return;
        container.removeAllViews();
        for (String sym : symbols) {
            TextView key = createKeyView(sym);
            container.addView(key);
        }
    }

    private TextView createKeyView(String text) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        lp.setMargins(2, 0, 2, 0);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#831843"));
        tv.setTextSize(18f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setBackgroundResource(R.drawable.bg_keyboard_key);
        tv.setClickable(true);
        tv.setFocusable(true);

        tv.setOnClickListener(v -> {
            playFeedback();
            commitCharacter(tv.getText().toString());
            if (shiftState == 1) {
                shiftState = 0;
                updateShiftUi();
            }
        });

        return tv;
    }

    private void setupQuickEmojis() {
        if (layoutQuickEmojis == null) return;
        layoutQuickEmojis.removeAllViews();
        for (String emoji : QUICK_EMOJIS) {
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (40 * getResources().getDisplayMetrics().density),
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
            lp.setMargins(2, 0, 2, 0);
            tv.setLayoutParams(lp);
            tv.setText(emoji);
            tv.setTextSize(18f);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setOnClickListener(v -> {
                playFeedback();
                commitCharacter(emoji);
            });
            layoutQuickEmojis.addView(tv);
        }
    }

    private void setupActionButtons(View root) {
        // Shift key
        if (btnShift != null) {
            btnShift.setOnClickListener(v -> {
                playFeedback();
                if (shiftState == 0) shiftState = 1;
                else if (shiftState == 1) shiftState = 2;
                else shiftState = 0;
                updateShiftUi();
            });
        }

        // Backspace keys with repeat on long press
        setupBackspaceButton(root.findViewById(R.id.btn_backspace));
        setupBackspaceButton(root.findViewById(R.id.btn_symbols_backspace));
        setupBackspaceButton(root.findViewById(R.id.btn_emoji_backspace));

        // Space keys
        setupSpaceButton(root.findViewById(R.id.btn_space));
        setupSpaceButton(root.findViewById(R.id.btn_symbols_space));
        setupSpaceButton(root.findViewById(R.id.btn_emoji_space));

        // Period & Comma keys
        View btnDot = root.findViewById(R.id.btn_dot);
        if (btnDot != null) btnDot.setOnClickListener(v -> { playFeedback(); commitCharacter("."); });
        View btnSymDot = root.findViewById(R.id.btn_symbols_dot);
        if (btnSymDot != null) btnSymDot.setOnClickListener(v -> { playFeedback(); commitCharacter("."); });
        View btnSymComma = root.findViewById(R.id.btn_symbols_comma);
        if (btnSymComma != null) btnSymComma.setOnClickListener(v -> { playFeedback(); commitCharacter(","); });

        // Enter keys
        if (btnEnter != null) btnEnter.setOnClickListener(v -> handleEnterAction());
        if (btnSymbolsEnter != null) btnSymbolsEnter.setOnClickListener(v -> handleEnterAction());

        // Mode toggles
        View btnToggleSymbols = root.findViewById(R.id.btn_toggle_symbols);
        if (btnToggleSymbols != null) btnToggleSymbols.setOnClickListener(v -> showSymbolsMode());
        View btnToggleAbc = root.findViewById(R.id.btn_toggle_abc);
        if (btnToggleAbc != null) btnToggleAbc.setOnClickListener(v -> showLettersMode());
        View btnToggleEmojis = root.findViewById(R.id.btn_toggle_emojis);
        if (btnToggleEmojis != null) btnToggleEmojis.setOnClickListener(v -> showEmojiMode());
        View btnEmojiAbc = root.findViewById(R.id.btn_emoji_abc);
        if (btnEmojiAbc != null) btnEmojiAbc.setOnClickListener(v -> showLettersMode());

        // Hide keyboard button
        View btnHide = root.findViewById(R.id.btn_hide_keyboard);
        if (btnHide != null) btnHide.setOnClickListener(v -> requestHideSelf(0));
    }

    private void setupBackspaceButton(View btn) {
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            playFeedback();
            handleBackspace();
        });
        btn.setOnLongClickListener(v -> {
            startRepeatBackspace();
            return true;
        });
        btn.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopRepeatBackspace();
            }
            return false;
        });
    }

    private void setupSpaceButton(View btn) {
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            playFeedback();
            commitCharacter(" ");
        });
    }

    private void setupEmojiGrid() {
        if (gridEmojis == null) return;
        gridEmojis.removeAllViews();
        for (String emoji : FULL_EMOJIS) {
            TextView tv = new TextView(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = (int) (48 * getResources().getDisplayMetrics().density);
            lp.height = (int) (44 * getResources().getDisplayMetrics().density);
            lp.setMargins(2, 2, 2, 2);
            tv.setLayoutParams(lp);
            tv.setText(emoji);
            tv.setTextSize(20f);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.bg_keyboard_key);
            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setOnClickListener(v -> {
                playFeedback();
                commitCharacter(emoji);
            });
            gridEmojis.addView(tv);
        }
    }

    private void updateShiftUi() {
        boolean isUpper = (shiftState > 0);
        for (TextView key : letterKeyViews) {
            String current = key.getText().toString();
            key.setText(isUpper ? current.toUpperCase() : current.toLowerCase());
        }
        if (btnShift != null) {
            if (shiftState == 2) {
                btnShift.setText("⇪");
                btnShift.setBackgroundColor(Color.parseColor("#F472B6"));
            } else if (shiftState == 1) {
                btnShift.setText("⇧");
                btnShift.setBackgroundResource(R.drawable.bg_keyboard_action_key);
            } else {
                btnShift.setText("⇧");
                btnShift.setBackgroundResource(R.drawable.bg_keyboard_key);
            }
        }
    }

    private void showLettersMode() {
        isSymbolsMode = false;
        isEmojiMode = false;
        if (layoutLetters != null) layoutLetters.setVisibility(View.VISIBLE);
        if (layoutSymbols != null) layoutSymbols.setVisibility(View.GONE);
        if (layoutEmojis != null) layoutEmojis.setVisibility(View.GONE);
    }

    private void showSymbolsMode() {
        isSymbolsMode = true;
        isEmojiMode = false;
        if (layoutLetters != null) layoutLetters.setVisibility(View.GONE);
        if (layoutSymbols != null) layoutSymbols.setVisibility(View.VISIBLE);
        if (layoutEmojis != null) layoutEmojis.setVisibility(View.GONE);
    }

    private void showEmojiMode() {
        isEmojiMode = true;
        isSymbolsMode = false;
        if (layoutLetters != null) layoutLetters.setVisibility(View.GONE);
        if (layoutSymbols != null) layoutSymbols.setVisibility(View.GONE);
        if (layoutEmojis != null) layoutEmojis.setVisibility(View.VISIBLE);
    }

    private void commitCharacter(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            CharSequence selectedText = ic.getSelectedText(0);
            if (selectedText != null && selectedText.length() > 0) {
                ic.commitText("", 1);
            } else {
                ic.deleteSurroundingText(1, 0);
            }
        }
    }

    private void startRepeatBackspace() {
        backspaceRunnable = new Runnable() {
            @Override
            public void run() {
                playFeedback();
                handleBackspace();
                repeatHandler.postDelayed(this, 50);
            }
        };
        repeatHandler.postDelayed(backspaceRunnable, 300);
    }

    private void stopRepeatBackspace() {
        if (backspaceRunnable != null) {
            repeatHandler.removeCallbacks(backspaceRunnable);
            backspaceRunnable = null;
        }
    }

    private void handleEnterAction() {
        playFeedback();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        EditorInfo info = getCurrentInputEditorInfo();
        if (info != null && (info.imeOptions & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION)) != 0) {
            int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            ic.performEditorAction(action);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    private void playFeedback() {
        try {
            if (audioManager != null) {
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(15);
            }
        } catch (Throwable ignored) {}
    }
}
