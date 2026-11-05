package cloud.octop.mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONObject

object AppState {
    @Volatile var foreground = false
}

object Diagnostics {
    private val frames = ArrayDeque<String>()
    @Synchronized fun add(raw: String) {
        frames.addLast(raw.take(400))
        while (frames.size > 30) frames.removeFirst()
    }
    @Synchronized fun snapshot(): List<String> = frames.toList()
    @Synchronized fun clear() = frames.clear()
}

const val HOOK_JS = """
(function(){
  if (window.__octopHook) return;
  window.__octopHook = true;
  var N = window.WebSocket;
  function W(u, p) {
    var ws = (p === undefined) ? new N(u) : new N(u, p);
    try {
      if (String(u).indexOf('/chat/ws') !== -1) {
        ws.addEventListener('message', function(e) {
          if (typeof e.data === 'string' && window.OctopBridge) {
            try { window.OctopBridge.onFrame(e.data); } catch (x) {}
          }
        });
      }
    } catch (x) {}
    return ws;
  }
  W.prototype = N.prototype;
  ['CONNECTING','OPEN','CLOSING','CLOSED'].forEach(function(k){ W[k] = N[k]; });
  window.WebSocket = W;
})();
"""

class ReplyTracker(private val ctx: Context, private val prefs: Prefs) {
    private val main = Handler(Looper.getMainLooper())
    private val buffer = StringBuilder()
    private var lastText = ""
    private var usedDelta = false
    private val flush = Runnable { deliver() }

    private class Ex(val text: String, val delta: Boolean)

    @JavascriptInterface
    fun onFrame(raw: String) {
        Diagnostics.add(raw)
        val ex = extract(raw) ?: return
        main.post {
            if (ex.delta) { buffer.append(ex.text); usedDelta = true } else { lastText = ex.text }
            main.removeCallbacks(flush)
            main.postDelayed(flush, 3000)
        }
    }

    private fun deliver() {
        val text = (if (usedDelta) buffer.toString() else lastText).trim()
        buffer.setLength(0); lastText = ""; usedDelta = false
        if (text.isBlank()) return
        if (AppState.foreground || !prefs.notifyReplies) return
        Notifier.showReply(ctx, text)
    }

    private fun extract(raw: String): Ex? {
        if (raw.trimStart().firstOrNull() != '{') return null
        val o = try { JSONObject(raw) } catch (_: Exception) { return null }
        val type = o.optString("type").lowercase()
        if (type in IGNORED_TYPES) return null
        if (o.optString("role").equals("user", true)) return null
        val scope = o.optJSONObject("data") ?: o
        for (k in TEXT_KEYS) {
            val v = scope.opt(k)
            if (v is String && v.isNotBlank()) return Ex(v, k == "delta")
        }
        return null
    }

    companion object {
        private val TEXT_KEYS = listOf("delta", "content", "text", "message", "reply", "answer")
        private val IGNORED_TYPES = setOf("ping", "pong", "heartbeat", "ack", "status", "connected")
    }
}
