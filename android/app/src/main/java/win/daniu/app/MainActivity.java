package win.daniu.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    
    private boolean isRefreshing = false;
    private ImageButton refreshBtn;
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Bridge bridge = getBridge();
        if (bridge == null) return;
        
        final WebView webView = bridge.getWebView();
        
        // 创建右上角刷新按钮
        createRefreshButton(webView);
        
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
                    if (refreshBtn != null) {
                        refreshBtn.setImageResource(android.R.drawable.ic_menu_rotate);
                    }
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
                
                // 注入 JS
                view.loadUrl(
                    "javascript:(function(){" +
                    "  document.querySelectorAll('a[target=_blank]').forEach(function(a){a.removeAttribute('target');});" +
                    "  window.open=function(url){window.location.href=url;return window;};" +
                    "  var mo=new MutationObserver(function(muts){muts.forEach(function(m){m.addedNodes.forEach(function(n){if(n.nodeType===1){if(n.tagName==='A'&&n.target==='_blank')n.removeAttribute('target');if(n.querySelectorAll)n.querySelectorAll('a[target=_blank]').forEach(function(a){a.removeAttribute('target')});}});});});" +
                    "  mo.observe(document.documentElement,{childList:true,subtree:true});" +
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
    
    /** 创建右上角刷新按钮 */
    private void createRefreshButton(final WebView webView) {
        refreshBtn = new ImageButton(this);
        refreshBtn.setImageResource(android.R.drawable.ic_menu_rotate);
        refreshBtn.setBackgroundColor(0x00000000);
        refreshBtn.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        refreshBtn.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        
        final FrameLayout root = new FrameLayout(MainActivity.this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        
        // 点击刷新
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRefreshing = true;
                Toast.makeText(MainActivity.this, "🔄 正在刷新...", Toast.LENGTH_SHORT).show();
                webView.reload();
            }
        });
        
        // 找到内容视图并在其上叠加按钮
        View contentView = findViewById(android.R.id.content);
        if (contentView != null) {
            ViewGroup parent = (ViewGroup) contentView.getParent();
            if (parent != null) {
                FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                    dpToPx(48), dpToPx(48)
                );
                btnParams.gravity = Gravity.TOP | Gravity.END;
                btnParams.topMargin = dpToPx(4);
                btnParams.rightMargin = dpToPx(4);
                parent.addView(refreshBtn, btnParams);
            }
        }
    }
    
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
