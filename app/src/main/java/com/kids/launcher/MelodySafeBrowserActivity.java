package com.kids.launcher;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.Set;

public class MelodySafeBrowserActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar pbLoading;
    private TextView tvUrl;
    private LinearLayout layoutBlocked;
    private TextView tvBlockedReason;
    private PreferencesManager prefs;

    private static final String DEFAULT_URL = "https://kids.nationalgeographic.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreStockNavBar();
        setContentView(R.layout.activity_melody_safe_browser);

        prefs = new PreferencesManager(this);

        initViews();
        setupWebView();
        setupBookmarks();

        loadUrl(DEFAULT_URL);
    }

    private void restoreStockNavBar() {
        if (getWindow() != null && getWindow().getDecorView() != null) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void initViews() {
        webView = findViewById(R.id.webview_safe);
        pbLoading = findViewById(R.id.pb_browser_loading);
        tvUrl = findViewById(R.id.tv_browser_url);
        layoutBlocked = findViewById(R.id.layout_url_blocked);
        tvBlockedReason = findViewById(R.id.tv_blocked_reason);

        findViewById(R.id.btn_browser_back).setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        findViewById(R.id.btn_browser_forward).setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });

        findViewById(R.id.btn_browser_refresh).setOnClickListener(v -> webView.reload());
        findViewById(R.id.btn_browser_home).setOnClickListener(v -> finish());

        Button btnBackToSafe = findViewById(R.id.btn_back_to_safe);
        btnBackToSafe.setOnClickListener(v -> loadUrl(DEFAULT_URL));
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    pbLoading.setVisibility(View.VISIBLE);
                    pbLoading.setProgress(newProgress);
                } else {
                    pbLoading.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    return handleUrl(request.getUrl().toString());
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (tvUrl != null) tvUrl.setText(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (tvUrl != null) tvUrl.setText(url);
            }
        });
    }

    private boolean isUrlAllowed(String url) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);

            Set<String> whitelist = prefs.getSafeWebWhitelist();
            for (String allowed : whitelist) {
                String allowedLower = allowed.toLowerCase(Locale.ROOT).trim();
                if (host.equals(allowedLower) || host.endsWith("." + allowedLower)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean handleUrl(String url) {
        if (isUrlAllowed(url)) {
            layoutBlocked.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            return false; // let webview load it
        } else {
            showBlockedScreen(url);
            return true; // block navigation
        }
    }

    private void showBlockedScreen(String url) {
        webView.setVisibility(View.GONE);
        layoutBlocked.setVisibility(View.VISIBLE);
        tvBlockedReason.setText("'" + url + "' is not on your approved safe websites list.\nAsk a grown-up to unlock this domain in Parent Zone 🌸");
    }

    public void loadUrl(String url) {
        if (isUrlAllowed(url)) {
            layoutBlocked.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
        } else {
            showBlockedScreen(url);
        }
    }

    private void setupBookmarks() {
        findViewById(R.id.bm_pbs_kids).setOnClickListener(v -> loadUrl("https://pbskids.org"));
        findViewById(R.id.bm_natgeo).setOnClickListener(v -> loadUrl("https://kids.nationalgeographic.com"));
        findViewById(R.id.bm_kiddle).setOnClickListener(v -> loadUrl("https://www.kiddle.co"));
        findViewById(R.id.bm_scratch).setOnClickListener(v -> loadUrl("https://scratch.mit.edu"));
        findViewById(R.id.bm_wiki).setOnClickListener(v -> loadUrl("https://simple.wikipedia.org"));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
