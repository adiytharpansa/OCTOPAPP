package cloud.octop.mobile

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("octop", Context.MODE_PRIVATE)

    var url: String
        get() = sp.getString("url", "") ?: ""
        set(v) = sp.edit().putString("url", v).apply()

    var servers: List<String>
        get() = (sp.getString("servers", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("servers", v.joinToString("\n")).apply()

    fun rememberServer(u: String) {
        if (u !in servers) servers = servers + u
    }

    var textZoom: Int
        get() = sp.getInt("textZoom", 100)
        set(v) = sp.edit().putInt("textZoom", v).apply()

    var notifyReplies: Boolean
        get() = sp.getBoolean("notify", true)
        set(v) = sp.edit().putBoolean("notify", v).apply()

    var keepAlive: Boolean
        get() = sp.getBoolean("keepAlive", false)
        set(v) = sp.edit().putBoolean("keepAlive", v).apply()

    var appLock: Boolean
        get() = sp.getBoolean("appLock", false)
        set(v) = sp.edit().putBoolean("appLock", v).apply()
}
