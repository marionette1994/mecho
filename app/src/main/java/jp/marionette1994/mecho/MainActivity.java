package jp.marionette1994.mecho;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 芽帖：WebView に index.html を載せ、ファイル入出力（Googleドライブ等への保存）をネイティブで補う。 */
public class MainActivity extends Activity {

    private WebView web;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(0xFFF4EFE3);
        getWindow().setNavigationBarColor(0xFFFBF8F0);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Native");
        setContentView(web);

        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }
        web.evaluateJavascript("onPicked(" + requestCode + "," + JSONObject.quote(uri.toString()) + ")", null);
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        if (web != null) web.destroy();
        web = null;
        super.onDestroy();
    }

    private void writeTo(Uri u, String mode, byte[] bytes) throws Exception {
        try (OutputStream os = getContentResolver().openOutputStream(u, mode)) {
            if (os == null) throw new java.io.IOException("openOutputStream returned null");
            os.write(bytes);
            os.flush();
        }
    }

    /** JavaScript から window.Native として呼ばれる */
    public class Bridge {

        /** 保存先を選ぶ（Googleドライブも選べる）。結果は onPicked(req, uri) で返す */
        @JavascriptInterface
        public void pick(int req, String name, String mime) {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_TITLE, name);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, req);
        }

        @JavascriptInterface
        public void open(int req) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, req);
        }

        /** 画面を止めずに書き込む。完了すると onWritten(key, err) を呼ぶ（成功時 err は空文字） */
        @JavascriptInterface
        public void writeAsync(final String key, final String uri, final String text) {
            io.execute(() -> {
                String err = "";
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                Uri u = Uri.parse(uri);
                try {
                    writeTo(u, "wt", bytes);
                } catch (Throwable first) {
                    try {
                        writeTo(u, "w", bytes);
                    } catch (Throwable e) {
                        err = String.valueOf(e);
                    }
                }
                final String js = "onWritten(" + JSONObject.quote(key) + "," + JSONObject.quote(err) + ")";
                runOnUiThread(() -> {
                    if (web != null) web.evaluateJavascript(js, null);
                });
            });
        }

        /** 失敗時は null */
        @JavascriptInterface
        public String read(String uri) {
            try (InputStream is = getContentResolver().openInputStream(Uri.parse(uri))) {
                if (is == null) return null;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (Throwable e) {
                return null;
            }
        }
    }
}
