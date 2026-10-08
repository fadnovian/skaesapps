package id.co.skaes.distribusiku;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Parcelable;
import android.provider.MediaStore;
import java.io.File;
import java.io.IOException;
import androidx.core.content.FileProvider;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.ViewGroup;

public class MainActivity extends Activity {
    private static final String HOME = "https://apps.skaes.co.id/?distribusiku=1&p=dashboard";
    private static final int FILE_PICKER_REQUEST = 101;
    private static final int LOCATION_PERMISSION_REQUEST = 102;
    private static final int WEB_CAMERA_PERMISSION_REQUEST = 103;
    private static final int FILE_CAMERA_PERMISSION_REQUEST = 104;
    private static final String TRUSTED_GEO_HOST = "apps.skaes.co.id";
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebCameraRequest;
    private WebChromeClient.FileChooserParams pendingFileChooserParams;
    private Uri cameraPhotoUri;
    private File cameraPhotoFile;
    private GeolocationPermissions.Callback pendingLocationCallback;
    private String pendingLocationOrigin;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                // Cegah situs eksternal menggunakan izin lokasi dari aplikasi SKAES.
                if (!isTrustedOrigin(Uri.parse(origin))) {
                    callback.invoke(origin, false, false);
                    return;
                }

                // Android 12+ memungkinkan pengguna memilih lokasi perkiraan (coarse).
                if (hasForegroundLocationPermission()) {
                    callback.invoke(origin, true, false);
                    return;
                }

                // Tunda jawaban WebView sampai pengguna menjawab dialog izin Android.
                if (pendingLocationCallback != null) finishLocationRequest(false);
                pendingLocationCallback = callback;
                pendingLocationOrigin = origin;
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, LOCATION_PERMISSION_REQUEST);
            }


            @Override public void onPermissionRequest(PermissionRequest request) {
                // getUserMedia() dari website HTTPS perlu izin terpisah di Android + WebView.
                // Hanya izinkan kamera untuk origin utama SKAES; jangan grant semua resources.
                if (!isTrustedOrigin(request.getOrigin())
                        || !requestsCamera(request)) {
                    request.deny();
                    return;
                }
                if (hasCameraPermission()) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    return;
                }
                if (pendingWebCameraRequest != null) {
                    pendingWebCameraRequest.deny();
                }
                pendingWebCameraRequest = request;
                requestPermissions(new String[]{Manifest.permission.CAMERA}, WEB_CAMERA_PERMISSION_REQUEST);
            }

            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                // WebView sudah membatalkan permintaan, jangan gunakan callback lama.
                if (pendingWebCameraRequest == request) pendingWebCameraRequest = null;
            }

            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                // Mendukung input[type=file], termasuk input accept=image/* capture.
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                pendingFileChooserParams = params;
                if (params.isCaptureEnabled() && acceptsImage(params) && !hasCameraPermission()) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, FILE_CAMERA_PERMISSION_REQUEST);
                    return true;
                }
                launchFileChooser(params);
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (("https".equals(scheme)) && ("apps.skaes.co.id".equals(host))) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Tidak ada aplikasi untuk membuka tautan", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        webView.setDownloadListener((url, userAgent, disposition, mimeType, contentLength) -> {
            try {
                Uri uri = Uri.parse(url);
                if (!"https".equals(uri.getScheme()) || !"apps.skaes.co.id".equals(uri.getHost())) {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return;
                }
                DownloadManager.Request request = new DownloadManager.Request(uri);
                request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                request.addRequestHeader("User-Agent", userAgent);
                request.setMimeType(mimeType);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalFilesDir(MainActivity.this, Environment.DIRECTORY_DOWNLOADS, "skaes-unduhan-" + System.currentTimeMillis());
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                Toast.makeText(MainActivity.this, "Mengunduh file...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Unduhan gagal", Toast.LENGTH_SHORT).show();
            }
        });
        if (state == null) webView.loadUrl(HOME);
        else webView.restoreState(state);
    }


    private boolean isTrustedOrigin(Uri origin) {
        return origin != null
                && "https".equalsIgnoreCase(origin.getScheme())
                && TRUSTED_GEO_HOST.equalsIgnoreCase(origin.getHost());
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean requestsCamera(PermissionRequest request) {
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) return true;
        }
        return false;
    }

    private boolean acceptsImage(WebChromeClient.FileChooserParams params) {
        for (String type : params.getAcceptTypes()) {
            if (type != null && (type.toLowerCase().startsWith("image/")
                    || type.toLowerCase().equals(".jpg") || type.toLowerCase().equals(".png")
                    || type.toLowerCase().equals(".jpeg"))) return true;
        }
        return false;
    }

    private Intent makeCameraIntent() throws IOException {
        File dir = new File(getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Tidak dapat menyiapkan direktori kamera");
        cameraPhotoFile = File.createTempFile("kunjungan-skaes-", ".jpg", dir);
        cameraPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraPhotoFile);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setClipData(android.content.ClipData.newUri(getContentResolver(), "Foto kunjungan", cameraPhotoUri));
        return intent;
    }

    private void launchFileChooser(WebChromeClient.FileChooserParams params) {
        try {
            Intent picker = params.createIntent();
            Intent intent = picker;
            cameraPhotoUri = null;
            cameraPhotoFile = null;
            if (acceptsImage(params) && hasCameraPermission()) {
                try {
                    Intent camera = makeCameraIntent();
                    if (params.isCaptureEnabled()) {
                        intent = camera;
                    } else {
                        Intent chooser = Intent.createChooser(picker, "Pilih foto atau ambil gambar");
                        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[]{camera});
                        intent = chooser;
                    }
                } catch (IOException | IllegalArgumentException e) {
                    // Kamera native tidak tersedia: kembali ke pemilih foto biasa.
                    cameraPhotoUri = null;
                    cameraPhotoFile = null;
                    intent = picker;
                }
            }
            startActivityForResult(intent, FILE_PICKER_REQUEST);
        } catch (ActivityNotFoundException | SecurityException e) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = null;
            pendingFileChooserParams = null;
            Toast.makeText(this, "Tidak dapat membuka kamera atau pemilih foto", Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasForegroundLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void finishLocationRequest(boolean granted) {
        if (pendingLocationCallback == null) return;
        GeolocationPermissions.Callback callback = pendingLocationCallback;
        String origin = pendingLocationOrigin;
        pendingLocationCallback = null;
        pendingLocationOrigin = null;
        // Jangan simpan izin web selamanya; izin Android tetap menjadi kendali pengguna.
        callback.invoke(origin, granted, false);
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WEB_CAMERA_PERMISSION_REQUEST) {
            PermissionRequest request = pendingWebCameraRequest;
            pendingWebCameraRequest = null;
            if (request != null) {
                if (hasCameraPermission() && isTrustedOrigin(request.getOrigin())) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    request.deny();
                    Toast.makeText(this, "Izinkan kamera untuk SKAES Distribusiku di Pengaturan aplikasi", Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == FILE_CAMERA_PERMISSION_REQUEST) {
            WebChromeClient.FileChooserParams params = pendingFileChooserParams;
            if (params != null) {
                // Jika ditolak, masih bisa memilih foto dari galeri sebagai alternatif.
                launchFileChooser(params);
            }
        } else if (requestCode == LOCATION_PERMISSION_REQUEST) {
            boolean granted = hasForegroundLocationPermission();
            finishLocationRequest(granted);
            if (!granted) {
                Toast.makeText(this,
                        "Izinkan lokasi untuk SKAES Distribusiku di Pengaturan aplikasi Android",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST && fileCallback != null) {
            Uri[] uris = null;
            if (resultCode == RESULT_OK) {
                // Kamera eksternal menyimpan foto ke URI milik FileProvider.
                if (cameraPhotoFile != null && cameraPhotoFile.exists() && cameraPhotoFile.length() > 0
                        && (data == null || data.getData() == null)) {
                    uris = new Uri[]{cameraPhotoUri};
                } else if (data != null) {
                    // Hasil SAF/picker standar, termasuk galeri dan pilihan multi-file.
                    uris = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                }
            }
            fileCallback.onReceiveValue(uris);
            fileCallback = null;
            pendingFileChooserParams = null;
            cameraPhotoUri = null;
            cameraPhotoFile = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        finishLocationRequest(false);
        if (pendingWebCameraRequest != null) {
            pendingWebCameraRequest.deny();
            pendingWebCameraRequest = null;
        }
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (webView != null) {
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }
}
