package win.daniu.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    
    private boolean isRefreshing = false;
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Bridge bridge = getBridge();
        if (bridge == null) return;
        
        final WebView webView = bridge.getWebView();
        
        // 0. WebView 基础设置（修复 data:image;base64 不显示问题）
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        // 强制每次从网络加载（避免缓存旧版 HTML）
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        // 允许混合内容（HTTP 图片在 HTTPS 页面）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        // 1. 禁止多窗口
        webView.getSettings().setSupportMultipleWindows(false);
        
        // 2. 拦截 onCreateWindow（_blank / window.open）
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // GitHub 链接 → 跳系统浏览器
                // 通过 WebView 自身的 loadUrl 触发 shouldOverrideUrlLoading
                return false; // 不创建新窗口，交给 shouldOverrideUrlLoading
            }
            
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100 && isRefreshing) {
                    isRefreshing = false;
                    Toast.makeText(MainActivity.this, "✅ 刷新完成", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        // 3. 拦截所有 URL 加载
        webView.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // GitHub 下载链接 → 用 Intent 跳 Chrome
                if (url.contains("github.com") || url.contains("githubusercontent.com")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addCategory(Intent.CATEGORY_BROWSABLE);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                    // 返回空响应，阻止 WebView 继续处理
                    return new WebResourceResponse(null, null, null);
                }
                return super.shouldInterceptRequest(view, request);
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                // GitHub 下载链接 → 用 Intent 跳系统浏览器（Chrome）
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
                
                // 注入当前 APK 版本号到页面（window.__APP_VERSION__）
                try {
                    String versionName = getPackageManager().getApplicationInfo(getPackageName(), 0).metaData.getString("android:versionName");
                    view.evaluateJavascript("javascript:(function(){window.__APP_VERSION__='"+versionName+"';})()", null);
                } catch(Exception e) {}
                
                // 注入 JS：拦截 _blank + window.open + 添加浮动刷新按钮
                view.loadUrl(
                    "javascript:(function(){" +
                    // 拦截 _blank 链接
                    "  document.querySelectorAll('a[target=_blank]').forEach(function(a){a.removeAttribute('target');});" +
                    // window.open 保持原样（由 Android Intent 处理）
                    // MutationObserver 拦截动态 _blank
                    "  var mo=new MutationObserver(function(muts){muts.forEach(function(m){m.addedNodes.forEach(function(n){if(n.nodeType===1){if(n.tagName==='A'&&n.target==='_blank')n.removeAttribute('target');if(n.querySelectorAll)n.querySelectorAll('a[target=_blank]').forEach(function(a){a.removeAttribute('target')});}});});});" +
                    "  mo.observe(document.documentElement,{childList:true,subtree:true});" +
                    // 添加浮动刷新按钮
                    "  if(!document.getElementById('__refreshBtn')){" +
                    "    var btn=document.createElement('div');" +
                    "    btn.id='__refreshBtn';" +
                    "    btn.innerHTML='<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"22\" height=\"22\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"white\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"23 4 23 10 17 10\"></polyline><polyline points=\"1 20 1 14 7 14\"></polyline><path d=\"M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15\"></path></svg>';" +
                    "    btn.style.cssText='position:fixed;top:16px;right:16px;z-index:2147483647;width:44px;height:44px;border-radius:50%;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);display:flex;align-items:center;justify-content:center;cursor:pointer;box-shadow:0 4px 12px rgba(102,126,234,0.4);border:none;outline:none;';" +
                    "    btn.onclick=function(){" +
                    "      window.location.reload();" +
                    "    };" +
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
}
