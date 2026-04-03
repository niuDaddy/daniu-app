package win.daniu.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    
    /** 是否正在刷新 */
    private boolean isRefreshing = false;
    /** 上次刷新时间（防抖动） */
    private long lastRefreshTime = 0;
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Bridge bridge = getBridge();
        if (bridge == null) return;
        
        final RefreshWebView webView = new RefreshWebView(this);
        
        // 创建覆盖层
        createRefreshOverlay(webView);
        
        // 1. 禁止多窗口
        webView.getSettings().setSupportMultipleWindows(false);
        
        // 2. 拦截 onCreateWindow（_blank 链接）
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                return false;
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
                isRefreshing = true;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isRefreshing = false;
                hideRefreshOverlay();
                
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
    
    /** 自定义 WebView：检测下拉动作并触发刷新 */
    private class RefreshWebView extends WebView {
        
        private RefreshWebView(Context context) {
            super(context);
            setOverScrollMode(WebView.OVER_SCROLL_IF_CONTENT_SCROLLS);
        }
        
        @Override
        public void overScroll(int scrollX, int scrollY, int clampedX, int clampedY,
                               int maxOverScrollX, int maxOverScrollY, boolean clippedY) {
            super.overScroll(scrollX, scrollY, clampedX, clampedY,
                           maxOverScrollX, maxOverScrollY, clippedY);
            
            // scrollY < 0 表示下拉（overscroll 向上拉）
            // clampedY = true 表示被卡住，不能继续往下拉
            // 防止抖动：距离上次刷新超过 1 秒才触发
            if (scrollY < 0 && clampedY && !isRefreshing) {
                long now = System.currentTimeMillis();
                if (now - lastRefreshTime > 1000) {
                    lastRefreshTime = now;
                    performRefresh();
                }
            }
        }
        
        private void performRefresh() {
            // 显示刷新动画
            isRefreshing = true;
            showRefreshOverlay();
            
            // 触发刷新
            reload();
            
            // Toast 确认
            Toast.makeText(MainActivity.this, "🔄 正在刷新...", Toast.LENGTH_SHORT).show();
        }
    }
    
    // --- 刷新覆盖层 ---
    private FrameLayout refreshOverlay;
    
    private void createRefreshOverlay(WebView webView) {
        refreshOverlay = new FrameLayout(this);
        refreshOverlay.setVisibility(View.GONE);
        
        ProgressBar spinner = new ProgressBar(this, null, android.R.attr.progressBarStyle);
        spinner.setIndeterminate(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.TOP;
        lp.topMargin = dpToPx(16);
        spinner.setLayoutParams(lp);
        refreshOverlay.addView(spinner);
        
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) {
            parent.addView(refreshOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ));
        }
    }
    
    private void showRefreshOverlay() {
        if (refreshOverlay == null) return;
        refreshOverlay.setVisibility(View.VISIBLE);
        refreshOverlay.setAlpha(1f);
    }
    
    private void hideRefreshOverlay() {
        if (refreshOverlay == null) return;
        refreshOverlay.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (refreshOverlay != null) {
                        refreshOverlay.setVisibility(View.GONE);
                        refreshOverlay.setAlpha(1f);
                    }
                }
            })
            .start();
    }
    
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
