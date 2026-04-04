package win.daniu.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    
    private static final int PERMISSIONS_REQUEST_LOCATION = 1001;
    private boolean isRefreshing = false;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Bridge bridge = getBridge();
        if (bridge == null) return;
        
        final WebView webView = bridge.getWebView();
        
        // 0. WebView 基础设置
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        // 允许混合内容（HTTP 图片在 HTTPS 页面）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        // 开启定位支持
        webView.getSettings().setGeolocationEnabled(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        }
        
        // 1. 禁止多窗口
        webView.getSettings().setSupportMultipleWindows(false);
        
        // 2. 拦截 onCreateWindow（_blank / window.open）+ Geolocation 权限回调
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                return false;
            }
            
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100 && isRefreshing) {
                    isRefreshing = false;
                    Toast.makeText(MainActivity.this, "✅ 刷新完成", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                // 保存回调，动态申请定位权限后再回调
                geoOrigin = origin;
                geoCallback = callback;
                requestLocationPermission();
            }
        });
        
        // 3. 拦截所有 URL 加载
        webView.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("github.com") || url.contains("githubusercontent.com")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addCategory(Intent.CATEGORY_BROWSABLE);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                    return new WebResourceResponse(null, null, null);
                }
                return super.shouldInterceptRequest(view, request);
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                if (url.contains("github.com") || url.contains("githubusercontent.com")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addCategory(Intent.CATEGORY_BROWSABLE);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                    return true;
                }
                
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url);
                    return true;
                }
                
                if (url.startsWith("tel:") || url.startsWith("mailto:") ||
                    url.startsWith("sms:") || url.startsWith("geo:") ||
                    url.startsWith("whatsapp:") || url.startsWith("intent:")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                    return true;
                }
                
                return super.shouldOverrideUrlLoading(view, request);
            }
            
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                
                try {
                    String versionName = getPackageManager().getApplicationInfo(getPackageName(), 0).metaData.getString("android:versionName");
                    view.evaluateJavascript("javascript:(function(){window.__APP_VERSION__='"+versionName+"';})()", null);
                } catch(Exception e) {}
                
                view.loadUrl(
                    "javascript:(function(){" +
                    "  document.querySelectorAll('a[target=_blank]').forEach(function(a){a.removeAttribute('target');});" +
                    "  var mo=new MutationObserver(function(muts){muts.forEach(function(m){m.addedNodes.forEach(function(n){if(n.nodeType===1){if(n.tagName==='A'&&n.target==='_blank')n.removeAttribute('target');if(n.querySelectorAll)n.querySelectorAll('a[target=_blank]').forEach(function(a){a.removeAttribute('target')});}});});});" +
                    "  mo.observe(document.documentElement,{childList:true,subtree:true});" +
                    "  if(!document.getElementById('__refreshBtn')){" +
                    "    var btn=document.createElement('div');" +
                    "    btn.id='__refreshBtn';" +
                    "    btn.innerHTML='<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"22\" height=\"22\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"white\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"23 4 23 10 17 10\"></polyline><polyline points=\"1 20 1 14 7 14\"></polyline><path d=\"M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15\"></path></svg>';" +
                    "    btn.style.cssText='position:fixed;top:16px;right:16px;z-index:2147483647;width:44px;height:44px;border-radius:50%;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);display:flex;align-items:center;justify-content:center;cursor:pointer;box-shadow:0 4px 12px rgba(102,126,234,0.4);border:none;outline:none;';" +
                    "    btn.onclick=function(){window.location.reload();};" +
                    "    document.body.appendChild(btn);" +
                    "  }" +
                    "})()"
                );
            }
        });
        
        // 4. 返回按钮处理
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    String currentUrl = webView.getUrl();
                    String baseUrl = "https://daniu.win";
                    
                    if (currentUrl != null && 
                        !currentUrl.equals(baseUrl) && 
                        !currentUrl.equals(baseUrl + "/") &&
                        !currentUrl.equals(baseUrl + "/index.html")) {
                        webView.goBack();
                    } else {
                        webView.loadUrl(baseUrl);
                    }
                }
            }
        });
    }
    
    /**
     * 运行时动态申请定位权限
     * Android 6.0+ 必须动态申请
     */
    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                PERMISSIONS_REQUEST_LOCATION);
        } else {
            // 已授权，直接回调
            if (geoCallback != null) {
                geoCallback.invoke(geoOrigin, true, true);
                geoCallback = null;
                geoOrigin = null;
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_LOCATION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (geoCallback != null) {
                geoCallback.invoke(geoOrigin, granted, true);
                geoCallback = null;
                geoOrigin = null;
            }
            if (!granted) {
                Toast.makeText(this, "⚠️ 定位权限未授予，离我最近功能不可用", Toast.LENGTH_LONG).show();
            }
        }
    }
}
