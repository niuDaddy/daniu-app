package win.daniu.app;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bridge bridge = getBridge();
        if (bridge != null) {
            WebView webView = bridge.getWebView();
            webView.setWebViewClient(new BridgeWebViewClient(bridge) {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();

                    // HTTP/HTTPS 链接强制在 WebView 内加载
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        view.loadUrl(url);
                        return true;
                    }

                    // 其他协议（tel:, mailto: 等）交给默认处理
                    return super.shouldOverrideUrlLoading(view, request);
                }
            });
        }
    }
}
