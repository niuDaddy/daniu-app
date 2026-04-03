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
        
        // 1. 禁止多窗口
        webView.getSettings().setSupportMultipleWindows(false);
        
        // 2. 拦截 onCreateWindow（_blank 链接）
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
        });
        
        // 3. 拦截所有 URL 加载
        webView.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
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
                
                // 注入 JS：拦截 _blank + window.open + 添加浮动刷新按钮
                view.loadUrl(
                    "javascript:(function(){" +
                    // 拦截 _blank 链接
                    "  document.querySelectorAll('a[target=_blank]').forEach(function(a){a.removeAttribute('target');});" +
                    // 拦截 window.open
                    "  window.open=function(url){window.location.href=url;return window;};" +
                    // MutationObserver 拦截动态 _blank
                    "  var mo=new MutationObserver(function(muts){muts.forEach(function(m){m.addedNodes.forEach(function(n){if(n.nodeType===1){if(n.tagName==='A'&&n.target==='_blank')n.removeAttribute('target');if(n.querySelectorAll)n.querySelectorAll('a[target=_blank]').forEach(function(a){a.removeAttribute('target')});}});});});" +
                    "  mo.observe(document.documentElement,{childList:true,subtree:true});" +
                    // 添加浮动刷新按钮
                    "  if(!document.getElementById('__refreshBtn')){" +
                    "    var btn=document.createElement('div');" +
                    "    btn.id='__refreshBtn';" +
                    "    btn.innerHTML='🔄';" +
                    "    btn.style.cssText='position:fixed;top:16px;right:16px;z-index:2147483647;width:44px;height:44px;border-radius:50%;background:rgba(0,0,0,0.55);display:flex;align-items:center;justify-content:center;font-size:22px;cursor:pointer;box-shadow:0 2px 8px rgba(0,0,0,0.3);';" +
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
