package org.idktech.lumen.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A YouTube trailer playing inside Lumen, behind the details panel. The player is YouTube's
 * IFrame API on a tiny page given a real https origin: an embed requested with no origin or
 * referrer is refused with error 152-4 ("video player configuration error"). The page reports
 * the player's state and errors back through `onState` ("playing", "error 150", …).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerPlayer(videoId: String, modifier: Modifier = Modifier, onState: (String) -> Unit = {}) {
    val main = Handler(Looper.getMainLooper())
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                isFocusable = false; isFocusableInTouchMode = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setBackgroundColor(Color.TRANSPARENT)
                webChromeClient = WebChromeClient()
                addJavascriptInterface(object {
                    @JavascriptInterface fun state(s: String) { main.post { onState(s) } }
                }, "Lumen")
                loadDataWithBaseURL(ORIGIN + "/", page(videoId), "text/html", "utf-8", null)
            }
        },
        update = { },
        onRelease = { v -> runCatching { v.loadUrl("about:blank"); v.destroy() } },
    )
}

// Any https origin will do (nothing is fetched from it); YouTube only needs one to be present.
private const val ORIGIN = "https://localhost"

private fun page(id: String) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body{margin:0;background:transparent;overflow:hidden;height:100%}#p{position:absolute;inset:0;width:100%;height:100%}</style></head>
<body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
function onYouTubeIframeAPIReady(){
  new YT.Player('p',{videoId:'$id',host:'https://www.youtube-nocookie.com',
    playerVars:{autoplay:1,controls:0,rel:0,modestbranding:1,iv_load_policy:3,playsinline:1,fs:0,disablekb:1,origin:'$ORIGIN'},
    events:{
      onReady:function(e){e.target.playVideo();Lumen.state('ready')},
      onStateChange:function(e){var m={'-1':'starting','0':'ended','1':'playing','2':'paused','3':'buffering','5':'cued'};Lumen.state(m[e.data]||String(e.data))},
      onError:function(e){Lumen.state('error '+e.data)}
    }});
}
</script></body></html>
""".trimIndent()
