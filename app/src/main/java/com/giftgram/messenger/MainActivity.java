package com.giftgram.messenger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * GiftGram — приватная сборка клиента.
 *
 * Экран чатов построен на движке Telegram Web (клиент K): полноценные чаты, каналы,
 * группы, медиа и продажи — внутри нативного Android-приложения с брендингом GiftGram.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GiftGram";
    private static final String CHANNEL_URL = "https://t.me/Giftgrammmmmm";
    private static final String START_URL = "https://web.telegram.org/k/";

    private static final int REQ_FILE = 4101;
    private static final int REQ_PERMS = 4102;

    private WebView web;
    private ProgressBar progress;
    private View offline;
    private SwipeRefreshLayout swipe;
    private FloatingActionButton fab;

    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermission;
    private boolean pageFailed = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        web = findViewById(R.id.web);
        progress = findViewById(R.id.progress);
        offline = findViewById(R.id.offline);
        swipe = findViewById(R.id.swipe);
        fab = findViewById(R.id.fab);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setGeolocationEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " GiftGram/1.0.0");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        WebView.setWebContentsDebuggingEnabled(false);

        web.addJavascriptInterface(new Bridge(), "GiftGramNative");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener(new Downloads());

        swipe.setColorSchemeColors(0xFF7C5CFF, 0xFF22D3EE);
        swipe.setOnRefreshListener(() -> {
            hideOffline();
            web.reload();
        });

        fab.setOnClickListener(v -> showMenu());
        findViewById(R.id.retry).setOnClickListener(v -> {
            hideOffline();
            web.loadUrl(START_URL);
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        requestBasePermissions();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            Uri deep = resolveDeepLink(getIntent());
            web.loadUrl(deep != null ? deep.toString() : START_URL);
        }

        // Небольшая анимация появления FAB
        fab.setScaleX(0f);
        fab.setScaleY(0f);
        fab.animate().scaleX(1f).scaleY(1f).setStartDelay(600).setDuration(420).start();
    }

    /* ------------------------------------------------------------------ */
    /*  Deep links: t.me/<user> -> Telegram Web                            */
    /* ------------------------------------------------------------------ */

    @Nullable
    private Uri resolveDeepLink(@Nullable Intent intent) {
        if (intent == null || intent.getData() == null) {
            return null;
        }
        Uri data = intent.getData();
        String host = data.getHost() == null ? "" : data.getHost().toLowerCase();
        if (host.endsWith("t.me") || host.endsWith("telegram.me")) {
            String path = data.getPath() == null ? "" : data.getPath();
            String user = path.replace("/", "").trim();
            if (!user.isEmpty() && !user.startsWith("+") && !user.startsWith("joinchat")) {
                return Uri.parse("https://web.telegram.org/k/#@" + user);
            }
        }
        return Uri.parse(data.toString());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri deep = resolveDeepLink(intent);
        if (deep != null) {
            hideOffline();
            web.loadUrl(deep.toString());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  WebViewClient                                                      */
    /* ------------------------------------------------------------------ */

    private class Client extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
            return handleUrl(req.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, String url) {
            return handleUrl(Uri.parse(url));
        }

        private boolean handleUrl(Uri uri) {
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();

            if (scheme.equals("http") || scheme.equals("https")) {
                boolean internal = host.endsWith("telegram.org")
                        || host.endsWith("t.me")
                        || host.endsWith("telegram.me")
                        || host.endsWith("telesco.pe")
                        || host.endsWith("telegra.ph");
                if (internal) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, R.string.download_fail, Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                // неизвестная схема — просто игнорируем
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
            pageFailed = false;
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            progress.setVisibility(View.GONE);
            swipe.setRefreshing(false);
            if (!pageFailed) {
                hideOffline();
            }
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
            if (req.isForMainFrame()) {
                pageFailed = true;
                showOffline();
            }
        }

        @Override
        public void onReceivedError(WebView v, int errorCode, String description, String failingUrl) {
            pageFailed = true;
            showOffline();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  WebChromeClient: прогресс, файлы, разрешения                       */
    /* ------------------------------------------------------------------ */

    private class Chrome extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView v, int newProgress) {
            progress.setProgress(newProgress);
            progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(null);
            }
            fileCallback = cb;
            try {
                Intent intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_file)), REQ_FILE);
                return true;
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, R.string.no_file_app, Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> {
                boolean needCamera = false;
                boolean needMic = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                        needCamera = true;
                    }
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                        needMic = true;
                    }
                }
                boolean okCamera = !needCamera
                        || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED;
                boolean okMic = !needMic
                        || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;

                if (okCamera && okMic) {
                    request.grant(request.getResources());
                } else {
                    pendingWebPermission = request;
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
                    }, REQ_PERMS);
                }
            });
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
            boolean fine = ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            cb.invoke(origin, fine, false);
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            pendingWebPermission = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS && pendingWebPermission != null) {
            boolean granted = true;
            for (String p : pendingWebPermission.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(p)
                        && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(p)
                        && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                }
            }
            try {
                if (granted) {
                    pendingWebPermission.grant(pendingWebPermission.getResources());
                } else {
                    pendingWebPermission.deny();
                }
            } catch (Exception ignored) {
            }
            pendingWebPermission = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /* ------------------------------------------------------------------ */
    /*  Загрузки                                                           */
    /* ------------------------------------------------------------------ */

    private class Downloads implements DownloadListener {

        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                    String mimeType, long contentLength) {
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                saveBlob(url, mimeType, contentDisposition);
                return;
            }
            try {
                String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimeType);
                req.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) {
                    req.addRequestHeader("Cookie", cookie);
                }
                req.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(req);
                    toast(getString(R.string.downloading));
                }
            } catch (Exception e) {
                Log.w(TAG, "download failed", e);
                toast(getString(R.string.download_fail));
            }
        }
    }

    /** blob:/data: ссылки сохраняем через JS-мост (base64 -> файл). */
    private void saveBlob(String url, String mime, String disposition) {
        String name = URLUtil.guessFileName(url, disposition, mime);
        String js = "(function(){"
                + "var x=new XMLHttpRequest();x.open('GET','" + url + "',true);x.responseType='blob';"
                + "x.onload=function(){var f=new FileReader();"
                + "f.onloadend=function(){GiftGramNative.saveBase64(f.result,'" + mime + "','" + name + "');};"
                + "f.readAsDataURL(x.response);};x.send();})();";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    /** Мост JS -> Android. */
    public class Bridge {

        @JavascriptInterface
        public void saveBase64(String dataUrl, String mime, String fileName) {
            try {
                int comma = dataUrl.indexOf(',');
                String base64 = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                if (saveBytes(fileName, mime, bytes)) {
                    runOnUiThread(() -> toast(getString(R.string.download_ok)));
                } else {
                    runOnUiThread(() -> toast(getString(R.string.download_fail)));
                }
            } catch (Exception e) {
                Log.w(TAG, "save blob failed", e);
                runOnUiThread(() -> toast(getString(R.string.download_fail)));
            }
        }
    }

    private boolean saveBytes(String fileName, String mime, byte[] bytes) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, mime);
                Uri uri = getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return false;
                }
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) {
                        return false;
                    }
                    os.write(bytes);
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists() && !dir.mkdirs()) {
                    return false;
                }
                try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
                    fos.write(bytes);
                }
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "save bytes failed", e);
            return false;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Меню GiftGram                                                      */
    /* ------------------------------------------------------------------ */

    private void showMenu() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_menu, null);
        sheet.setContentView(v);

        v.findViewById(R.id.row_channel).setOnClickListener(x -> {
            sheet.dismiss();
            openUrl(CHANNEL_URL);
        });

        v.findViewById(R.id.row_refresh).setOnClickListener(x -> {
            sheet.dismiss();
            hideOffline();
            web.reload();
        });

        v.findViewById(R.id.row_share).setOnClickListener(x -> {
            sheet.dismiss();
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text));
            startActivity(Intent.createChooser(i, getString(R.string.act_share)));
        });

        android.widget.TextView themeState = v.findViewById(R.id.theme_state);
        themeState.setText(GiftGramApp.isDark() ? "Включена" : "Выключена");
        v.findViewById(R.id.row_theme).setOnClickListener(x -> {
            boolean next = !GiftGramApp.isDark();
            GiftGramApp.setDark(next);
            themeState.setText(next ? "Включена" : "Выключена");
        });

        v.findViewById(R.id.row_clear).setOnClickListener(x -> {
            sheet.dismiss();
            WebStorage.getInstance().deleteAllData();
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            web.clearCache(true);
            web.clearHistory();
            toast(getString(R.string.cache_cleared));
        });

        v.findViewById(R.id.row_about).setOnClickListener(x -> {
            sheet.dismiss();
            showAbout();
        });

        v.findViewById(R.id.row_exit).setOnClickListener(x -> {
            sheet.dismiss();
            finishAffinity();
        });

        sheet.show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.about_title))
                .setMessage(getString(R.string.about_text))
                .setPositiveButton(R.string.about_ok, null)
                .setNeutralButton(R.string.about_channel,
                        (d, w) -> openUrl(CHANNEL_URL))
                .show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.download_fail));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Сеть и разрешения                                                  */
    /* ------------------------------------------------------------------ */

    private void requestBasePermissions() {
        java.util.List<String> need = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMS + 1);
        }
    }

    private boolean hasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return true;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showOffline() {
        if (!hasNetwork()) {
            offline.setVisibility(View.VISIBLE);
            offline.setAlpha(0f);
            offline.animate().alpha(1f).setDuration(260).start();
        }
    }

    private void hideOffline() {
        if (offline.getVisibility() == View.VISIBLE) {
            offline.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> offline.setVisibility(View.GONE)).start();
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /* ------------------------------------------------------------------ */
    /*  Жизненный цикл                                                     */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (!pageFailed && hasNetwork()) {
            hideOffline();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
