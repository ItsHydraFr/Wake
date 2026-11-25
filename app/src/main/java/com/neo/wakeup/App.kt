package com.neo.wakeup

import android.animation.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.animation.DecelerateInterpolator
import android.app.*
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.RippleDrawable
import android.media.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

// ---------- Themes ----------
class Th(val name: String, b1: String, b2: String, x1: String, x2: String, t: String, s: String, c: String, bt: String, val dark: Boolean) {
    val bg1 = Color.parseColor(b1); val bg2 = Color.parseColor(b2)
    val ac1 = Color.parseColor(x1); val ac2 = Color.parseColor(x2)
    val text = Color.parseColor(t); val sub = Color.parseColor(s)
    val card = Color.parseColor(c); val btnText = Color.parseColor(bt)
}

val THEMES = listOf(
    Th("Midnight", "#0F0C29", "#302B63", "#8E7BFF", "#FF6B9D", "#FFFFFF", "#B8B5E0", "#26FFFFFF", "#FFFFFF", true),
    Th("Sunrise", "#FFF1E6", "#FFC9A8", "#FF8A00", "#FF3D77", "#3B1F1A", "#8A5A4B", "#99FFFFFF", "#FFFFFF", false),
    Th("Ocean", "#021B3A", "#06607F", "#1CB5E0", "#00E5B0", "#FFFFFF", "#A8D8EA", "#26FFFFFF", "#FFFFFF", true),
    Th("Forest", "#0B1F14", "#1E4D2B", "#A8E063", "#56AB2F", "#EAFBEA", "#A5CBA8", "#26FFFFFF", "#0B1F14", true),
    Th("Candy", "#FFE3F1", "#E0C3FC", "#FF8AD8", "#9B6BFF", "#3A1F4D", "#7C5C99", "#99FFFFFF", "#FFFFFF", false),
    Th("Mono", "#000000", "#1C1C1C", "#FFFFFF", "#BDBDBD", "#FFFFFF", "#9E9E9E", "#1AFFFFFF", "#000000", true),
    Th("Rose Gold", "#2B1B1F", "#5E3A3F", "#F7C5A8", "#E8869A", "#FFF1EC", "#D9B3A6", "#26FFFFFF", "#2B1B1F", true),
    Th("Lavender", "#F3EEFF", "#D9CCFF", "#9D7BFF", "#6C5CE7", "#2A2150", "#6E639A", "#99FFFFFF", "#FFFFFF", false),
    Th("Dusk", "#1A1A2E", "#533A71", "#FF9A8B", "#FF6A88", "#FFFFFF", "#C9B8E0", "#26FFFFFF", "#1A1A2E", true),
    Th("Aurora", "#021124", "#0B3D3A", "#7CF9C0", "#5B8DEF", "#EAFFF7", "#9BD3C0", "#26FFFFFF", "#021124", true),
    Th("Cherry", "#2A0610", "#7A1230", "#FF5C7A", "#FF9A5C", "#FFFFFF", "#F0B2C0", "#26FFFFFF", "#2A0610", true),
    Th("Gold", "#0B0B0B", "#2A220F", "#F6D365", "#C9A227", "#FFF7DC", "#B9A56A", "#1AFFFFFF", "#0B0B0B", true),
    Th("Arctic", "#EAF6FF", "#BFE3FF", "#2D9CDB", "#56CCF2", "#0E2A3F", "#4F7694", "#99FFFFFF", "#FFFFFF", false),
    Th("Lava", "#1B0000", "#5C0A00", "#FF4E00", "#FFB200", "#FFF2E5", "#E0A890", "#26FFFFFF", "#1B0000", true),
    Th("Mint", "#E8FFF4", "#BFF2DB", "#00B894", "#55EFC4", "#0B3B2E", "#3F7F6C", "#99FFFFFF", "#0B3B2E", false),
    Th("Slate", "#14181F", "#2B3442", "#7C93FF", "#5ED3F3", "#E8EDF5", "#8FA0B8", "#26FFFFFF", "#14181F", true)
)


// ---------- Core ----------
object Core {
    const val CH_ALARM = "alarm"; const val CH_INFO = "info"; const val CH_SVC = "run"; const val CH_POLITE = "polite"; const val CH_POLITE_DND = "polite_dnd"; const val CH_MACRO = "macro"
    val ICON = R.drawable.ic_alarm
    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("p", 0)
    private val waiters = HashMap<String, (Boolean) -> Unit>()
    @Volatile var lastErr = ""
    fun whyFailed(): String {
        val e = lastErr
        return when {
            e == "HTTP 429" -> "ntfy.sh says too many messages (rate limit). Wait a few minutes."
            e.startsWith("HTTP") -> "ntfy.sh error ($e)."
            e.startsWith("net:") -> "can't reach ntfy.sh (" + e.removePrefix("net:") + "). It may be blocked or slow on this network."
            else -> "check your connection."
        }
    }
    fun rid(): String = (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
    fun dndOn(c: Context): Boolean = System.currentTimeMillis() < prefs(c).getLong("dnd_until", 0L)

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        try { nm.deleteNotificationChannel("svc") } catch (e: Exception) {}
        try { nm.deleteNotificationChannel("quiet") } catch (e: Exception) {}
        try { nm.deleteNotificationChannel("bg") } catch (e: Exception) {}
        nm.createNotificationChannel(NotificationChannel(CH_SVC, "Background listening", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_INFO, "Messages", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_MACRO, "Macro phone", NotificationManager.IMPORTANCE_DEFAULT))
        val po = NotificationChannel(CH_POLITE, "Are you awake?", NotificationManager.IMPORTANCE_HIGH)
        po.setBypassDnd(true); nm.createNotificationChannel(po)
        if (nm.isNotificationPolicyAccessGranted) {
            val pd = NotificationChannel(CH_POLITE_DND, "Are you awake? (overrides Do Not Disturb)", NotificationManager.IMPORTANCE_HIGH)
            pd.setBypassDnd(true); nm.createNotificationChannel(pd)
        }
        val a = NotificationChannel(CH_ALARM, "Wake-up alarm", NotificationManager.IMPORTANCE_HIGH)
        a.setSound(null, null); a.setBypassDnd(true)
        nm.createNotificationChannel(a)
    }

    private fun rawPost(topic: String, body: ByteArray, put: Boolean = false, fname: String? = null): Pair<Boolean, String> {
        val h = URL("https://ntfy.sh/$topic").openConnection() as HttpURLConnection
        h.requestMethod = if (put) "PUT" else "POST"; h.doOutput = true; h.connectTimeout = 15000; h.readTimeout = 30000
        if (fname != null) h.setRequestProperty("Filename", fname)
        h.outputStream.use { it.write(body) }
        val code = h.responseCode
        lastErr = if (code == 200) "" else "HTTP $code"
        val txt = try { (if (code == 200) h.inputStream else h.errorStream).bufferedReader().readText() } catch (e: Exception) { "" }
        h.disconnect()
        return Pair(code == 200, txt)
    }

    fun postJson(c: Context, o: JSONObject, done: ((Boolean) -> Unit)? = null, track: Boolean = true): String {
        if (!o.has("id")) o.put("id", rid())
        val id = o.getString("id")
        val t = prefs(c).getString("theirs", "")!!.trim()
        if (t.isEmpty()) { done?.invoke(false); return id }
        Thread {
            var ok = false
            if (track) log(c, 0, describe(o), id, 0)
            var tries = 0
            while (tries < 3 && !ok) {
                try { ok = rawPost(t, o.toString().toByteArray()).first } catch (e: Exception) { lastErr = "net:" + e.javaClass.simpleName }
                val retry = !ok && (lastErr == "HTTP 429" || lastErr.startsWith("net:") || lastErr.startsWith("HTTP 5"))
                if (!retry) break
                tries++
                if (tries < 3) Thread.sleep(if (tries == 1) 2000L else 5000L)
            }
            if (track && !ok) mark(c, id, 2)
            if (done != null) Handler(Looper.getMainLooper()).post { done(ok) }
        }.start()
        return id
    }
    fun send(c: Context, type: String, done: ((Boolean) -> Unit)? = null): String = postJson(c, JSONObject().put("t", type), done)

    fun postTo(topic: String, body: ByteArray): Boolean = rawPost(topic, body).first
    fun postToErr(topic: String, body: ByteArray): String = try {
        val r = rawPost(topic, body); if (r.first) "" else "Server refused it: " + r.second.take(80)
    } catch (e: Exception) { "Can't reach ntfy.sh" }
    fun armWatchdog(c: Context, ms: Long = 120000L) {
        try {
            val am = c.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(c, 9, Intent(c, WatchdogReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val at = System.currentTimeMillis() + ms
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: Exception) {}
    }
    fun infoId(c: Context, id: Int, title: String, text: String) {
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, CH_INFO).setSmallIcon(ICON).setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true).build()
        c.getSystemService(NotificationManager::class.java).notify(id, n)
    }
    fun macroNotify(c: Context, a: JSONArray) {
        if (!prefs(c).getBoolean("ml_notify", false) || dndOn(c)) return
        val nm = c.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MacroLogActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val now = System.currentTimeMillis()
        val fresh = ArrayList<JSONObject>()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (now - o.optLong("ts") < 600000) fresh.add(o) }
        val show = fresh.takeLast(15)
        for ((k, o) in show.withIndex()) {
            val title = o.optString("app") + (if (o.optString("title").isNotEmpty()) " · " + o.optString("title") else "")
            val body = o.optString("text")
            val n = Notification.Builder(c, CH_MACRO).setSmallIcon(ICON).setContentTitle(title).setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true).setWhen(o.optLong("ts")).build()
            nm.notify((now % 1000000).toInt() + 1000 + k, n)
        }
        if (fresh.size > show.size) {
            val n = Notification.Builder(c, CH_MACRO).setSmallIcon(ICON).setContentTitle("Macro phone").setContentText("+" + (fresh.size - show.size) + " more in the log")
                .setContentIntent(open).setAutoCancel(true).build()
            nm.notify(999, n)
        }
    }
    fun politeChannel(c: Context): String = try {
        if (c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) { channels(c); CH_POLITE_DND } else CH_POLITE
    } catch (e: Exception) { CH_POLITE }

    fun watch(id: String, cb: (Boolean) -> Unit) { synchronized(waiters) { waiters[id] = cb } }
    fun delivered(c: Context, id: String, dnd: Boolean) {
        mark(c, id, if (dnd) 3 else 1)
        val cb = synchronized(waiters) { waiters.remove(id) }
        if (cb != null) Handler(Looper.getMainLooper()).post { cb(dnd) }
    }

    fun sendCustom(c: Context, title: String, body: String, img: Uri?, done: (Boolean) -> Unit): String {
        val o = JSONObject().put("t", "msg").put("title", title).put("body", body).put("id", rid())
        if (img != null) o.put("photo", true)
        val id = o.getString("id")
        val t = prefs(c).getString("theirs", "")!!.trim()
        if (t.isEmpty()) { done(false); return id }
        Thread {
            var ok = false
            log(c, 0, describe(o), id, 0)
            try {
                if (img != null) {
                    val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    c.contentResolver.openInputStream(img).use { BitmapFactory.decodeStream(it, null, opt) }
                    var ss = 1
                    while (opt.outWidth / ss > 1400 || opt.outHeight / ss > 1400) ss *= 2
                    val bmp = c.contentResolver.openInputStream(img).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = ss }) }!!
                    val bos = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                    val r = rawPost(t, bos.toByteArray(), true, "photo.jpg")
                    if (!r.first) throw Exception("upload failed")
                    o.put("img", JSONObject(r.second).getJSONObject("attachment").getString("url"))
                }
                ok = rawPost(t, o.toString().toByteArray()).first
            } catch (e: Exception) { lastErr = "net:" + e.javaClass.simpleName }
            if (!ok) mark(c, id, 2)
            Handler(Looper.getMainLooper()).post { done(ok) }
        }.start()
        return id
    }

    fun sendBoard(c: Context, bmp: Bitmap, done: (Boolean) -> Unit): String {
        val id = rid()
        val t = prefs(c).getString("theirs", "")!!.trim()
        if (t.isEmpty()) { done(false); return id }
        Thread {
            var ok = false
            log(c, 0, "Drawing synced", id, 0)
            try {
                val bos = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                val r = rawPost(t, bos.toByteArray(), true, "board.png")
                if (r.first) {
                    val o = JSONObject().put("t", "board").put("id", id).put("img", JSONObject(r.second).getJSONObject("attachment").getString("url"))
                    ok = rawPost(t, o.toString().toByteArray()).first
                }
            } catch (e: Exception) { lastErr = "net:" + e.javaClass.simpleName }
            if (!ok) mark(c, id, 2)
            Handler(Looper.getMainLooper()).post { done(ok) }
        }.start()
        return id
    }

    fun info(c: Context, title: String, text: String) {
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, CH_INFO).setSmallIcon(ICON).setContentTitle(title).setContentText(text)
            .setContentIntent(pi).setAutoCancel(true).build()
        c.getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % 100000).toInt() + 10, n)
    }

    fun withReply(c: Context, ch: String, title: String, text: String, label: String, reply: String) {
        val nid = (System.currentTimeMillis() % 100000).toInt() + 10
        val pi = PendingIntent.getBroadcast(c, nid, Intent(c, ReplyReceiver::class.java).putExtra("r", reply).putExtra("n", nid),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, ch).setSmallIcon(ICON).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(c, ICON), label, pi).build()).build()
        c.getSystemService(NotificationManager::class.java).notify(nid, n)
    }

    fun custom(c: Context, title: String, body: String, bmp: Bitmap?) {
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(c, CH_INFO).setSmallIcon(ICON).setContentTitle(if (title.isBlank()) "Message" else title)
            .setContentText(body).setContentIntent(pi).setAutoCancel(true)
        if (bmp != null) b.setStyle(Notification.BigPictureStyle().bigPicture(bmp).setSummaryText(body))
        else b.setStyle(Notification.BigTextStyle().bigText(body))
        c.getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % 100000).toInt() + 10, b.build())
    }

    fun ring(c: Context) {
        val i = Intent(c, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(c, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(c, CH_ALARM).setSmallIcon(ICON).setContentTitle("WAKE UP").setContentText("Tap to open")
            .setCategory(Notification.CATEGORY_ALARM).setFullScreenIntent(pi, true).setContentIntent(pi).setOngoing(true).build()
        c.getSystemService(NotificationManager::class.java).notify(2, n)
        try { c.startActivity(i) } catch (e: Exception) {}
    }

    // st: 0 sent, 1 delivered, 2 failed, 3 delivered (their DND on), 4 received but muted
    @Synchronized fun log(c: Context, kind: Int, text: String, id: String = "", st: Int = 0) {
        try {
            val p = prefs(c)
            val old = JSONArray(p.getString("hist", "[]"))
            val n = JSONArray()
            n.put(JSONObject().put("k", kind).put("x", text).put("t", System.currentTimeMillis()).put("i", id).put("s", st))
            var i = 0
            while (i < old.length() && n.length() < 300) { n.put(old.get(i)); i++ }
            p.edit().putString("hist", n.toString()).apply()
        } catch (e: Exception) {}
        Backup.save(c)
        Handler(Looper.getMainLooper()).post { Hooks.onLog?.invoke() }
    }
    @Synchronized fun mark(c: Context, id: String, st: Int) {
        try {
            val p = prefs(c)
            val a = JSONArray(p.getString("hist", "[]"))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.optString("i") == id) o.put("s", st) }
            p.edit().putString("hist", a.toString()).apply()
        } catch (e: Exception) {}
        Backup.save(c)
        Handler(Looper.getMainLooper()).post { Hooks.onLog?.invoke() }
    }

    fun describe(o: JSONObject): String = when (o.optString("t")) {
        "wake" -> "Wake-up"
        "polite" -> "Are you awake?"
        "meow" -> "Nyaaa"
        "yesh" -> "Replied: Yesh"
        "meowr" -> "Replied: Meowww"
        "ack" -> "Confirmed they're up"
        "late" -> "Not up yet notice"
        "board" -> "Drawing synced"
        "msg" -> {
            val s = (o.optString("title") + " " + o.optString("body")).trim()
            "Message" + (if (s.isNotEmpty()) ": $s" else "") + (if (o.optString("img").isNotEmpty() || o.optBoolean("photo")) " (photo)" else "")
        }
        else -> o.optString("t")
    }

    fun freshWake(c: Context) {
        val p = prefs(c)
        if (System.currentTimeMillis() > p.getLong("snzu", 0L)) p.edit().putInt("snz", 0).apply()
    }
    private fun schedPi(c: Context, code: Int, k: String) = PendingIntent.getBroadcast(c, code,
        Intent(c, SchedReceiver::class.java).putExtra("k", k), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    fun setAt(c: Context, t: Long, code: Int, k: String) {
        try {
            val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(t, open), schedPi(c, code, k))
        } catch (e: Exception) {}
    }
    fun cancelAt(c: Context, code: Int) { try { c.getSystemService(AlarmManager::class.java).cancel(schedPi(c, code, "x")) } catch (e: Exception) {} }
    fun scheduleNext(c: Context) {
        try {
            val p = prefs(c)
            if (!p.getBoolean("sched_on", false)) { cancelAt(c, 4); return }
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, p.getInt("sh", 8)); cal.set(java.util.Calendar.MINUTE, p.getInt("sm", 0))
            cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            setAt(c, cal.timeInMillis, 4, "ring")
        } catch (e: Exception) {}
    }
    fun appMode(c: Context): Boolean = prefs(c).getBoolean("stop_app", false) && !prefs(c).getString("stop_pkg", "").isNullOrEmpty()
    fun hasUsage(c: Context): Boolean = try {
        c.getSystemService(AppOpsManager::class.java).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }
    fun finishWake(c: Context) {
        Alarm.stop(c)
        prefs(c).edit().putInt("snz", 0).putBoolean("pend", false).apply()
        try { c.getSystemService(AlarmManager::class.java).cancel(snoozePi(c)) } catch (e: Exception) {}
        cancelAt(c, 5); log(c, 2, "You're up"); send(c, "ack")
    }
}

// ---------- Alarm sound ----------
object Alarm {
    private var mp: MediaPlayer? = null
    private var vib: Vibrator? = null
    private val h = Handler(Looper.getMainLooper())
    private var vol = 0.2f
    var ringing = false
    private var ctx: Context? = null
    private var startAt = 0L
    private val poll = object : Runnable {
        override fun run() {
            if (!ringing) return
            val a = ctx ?: return
            val pk = Core.prefs(a).getString("stop_pkg", "")!!
            var found = false
            try {
                val u = a.getSystemService(UsageStatsManager::class.java)
                val ev = u.queryEvents(startAt, System.currentTimeMillis()); val e = UsageEvents.Event()
                while (ev.hasNextEvent()) {
                    ev.getNextEvent(e)
                    if (e.packageName == pk && (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || e.eventType == UsageEvents.Event.ACTIVITY_RESUMED)) found = true
                }
            } catch (e: Exception) {}
            if (found) Core.finishWake(a) else h.postDelayed(this, 1500)
        }
    }
    private val ramp = object : Runnable {
        override fun run() {
            if (!ringing) return
            vol = minOf(1f, vol + 0.1f)
            try { mp?.setVolume(vol, vol) } catch (e: Exception) {}
            h.postDelayed(this, 6000)
        }
    }

    fun start(c: Context) {
        ctx = c.applicationContext
        val a = ctx!!
        if (ringing) return
        ringing = true; vol = 0.2f; startAt = System.currentTimeMillis()
        Core.log(a, 2, "Alarm rang")
        val p = Core.prefs(a)
        val am = a.getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val s = p.getString("tone", "")!!
        val uri = if (s.isEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(s)
        try {
            val m = MediaPlayer()
            m.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp = m
            m.setWakeMode(a, PowerManager.PARTIAL_WAKE_LOCK); m.setDataSource(a, uri); m.isLooping = true; m.setVolume(vol, vol); m.prepare(); m.start()
        } catch (e: Exception) {}
        try {
            vib = a.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        } catch (e: Exception) {}
        h.postDelayed(ramp, 6000)
        if (Core.appMode(a)) h.postDelayed(poll, 1500)
        else if (!p.getBoolean("forever", true)) h.postDelayed({ stop(a) }, 90000)
        Core.ring(a)
    }

    fun stop(c: Context) {
        val a = c.applicationContext
        ringing = false
        h.removeCallbacksAndMessages(null)
        val m = mp; mp = null
        if (m != null) {
            try { m.setVolume(0f, 0f) } catch (e: Exception) {}
            try { m.pause() } catch (e: Exception) {}
            try { m.stop() } catch (e: Exception) {}
            try { m.release() } catch (e: Exception) {}
        }
        try { vib?.cancel() } catch (e: Exception) {}
        try { a.getSystemService(NotificationManager::class.java).cancel(2) } catch (e: Exception) {}
    }
}

fun snoozePi(c: Context): PendingIntent =
    PendingIntent.getBroadcast(c, 3, Intent(c, SnoozeReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Alarm.start(c) }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        c.startForegroundService(Intent(c, ListenService::class.java)); Core.scheduleNext(c); Core.armWatchdog(c)
    }
}

class SchedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = Core.prefs(c)
        when (i.getStringExtra("k")) {
            "ring" -> {
                Core.scheduleNext(c)
                if (!p.getBoolean("sched_on", false)) return
                Core.freshWake(c)
                p.edit().putBoolean("pend", true).apply()
                Alarm.start(c)
                Core.setAt(c, System.currentTimeMillis() + p.getInt("grace", 15) * 60000L, 5, "check")
            }
            "check" -> if (p.getBoolean("pend", false)) { p.edit().putBoolean("pend", false).apply(); Core.send(c, "late") }
        }
    }
}

class WakeWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val pi = PendingIntent.getBroadcast(c, 6, Intent(c, WakeWidget::class.java).setAction("com.neo.wakeup.WAKE"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val v = RemoteViews(c.packageName, R.layout.widget); v.setOnClickPendingIntent(R.id.w, pi)
        for (id in ids) m.updateAppWidget(id, v)
    }
    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        if (i.action == "com.neo.wakeup.WAKE") {
            Core.send(c, "wake")
            try { c.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (e: Exception) {}
        }
    }
}

class WakeTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; label = "Wake partner"; icon = Icon.createWithResource(this@WakeTile, R.drawable.ic_alarm); updateTile() }
    }
    override fun onClick() { Core.send(this, "wake") }
}

// ---------- Listener service ----------
class ListenService : Service() {
    companion object { @Volatile var running = false; @Volatile var poller: ((Boolean) -> String)? = null; @Volatile var conn: HttpURLConnection? = null; @Volatile var alive = false }
    private var started = false
    override fun onDestroy() { running = false; poller = null; Core.armWatchdog(this, 5000L); super.onDestroy() }
    override fun onTaskRemoved(i: Intent?) { Core.armWatchdog(this, 5000L); super.onTaskRemoved(i) }
    private var netCb = false
    private fun watchNetwork() {
        if (netCb) return
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(n: Network) { Thread { try { conn?.disconnect() } catch (e: Exception) {} }.start() }
            })
            netCb = true
        } catch (e: Exception) {}
    }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        Core.channels(this)
        val n = Notification.Builder(this, Core.CH_SVC).setSmallIcon(Core.ICON).setContentTitle("WakeUp is running").setContentText("Listening for wake-ups").setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        running = true; poller = { deep -> pollOnce(deep) }; Core.armWatchdog(this); watchNetwork()
        if (!started) {
            started = true
            Thread { Core.log(this, 2, "Listener started") }.start()
            Thread {
                alive = true
                try { loop("mine", "") { m, a -> handle(m, a) } }
                catch (e: Throwable) { Core.log(this, 2, "Listener crashed: " + e.javaClass.simpleName) }
                finally { alive = false; started = false }
            }.start()
        }
        return START_STICKY
    }

    private fun handle(m: JSONObject, age: Long) {
        val t = m.optString("t")
        try { getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wakeup:msg").acquire(20000L) } catch (e: Exception) {}
        if (t == "dlv") { Core.delivered(this, m.optString("ref"), m.optBoolean("dnd")); return }
        if (t == "nlog") { if (!BuildConfig.MACRO) handleMacro(m); return }
        val dnd = Core.dndOn(this)
        val id = m.optString("id")
        if (id.isNotEmpty()) Core.postJson(this, JSONObject().put("t", "dlv").put("ref", id).put("dnd", dnd), null, false)
        Core.log(this, 1, Core.describe(m) + (if (age > 900 && t == "wake") " (arrived late)" else ""), "", if (dnd && t != "board") 4 else 0)
        val ui = Handler(Looper.getMainLooper())
        if (t == "board") {
            Thread {
                try {
                    val bmp = URL(m.optString("img")).openStream().use { BitmapFactory.decodeStream(it) }
                    if (bmp != null) {
                        Board.store(this, bmp)
                        ui.post { Board.view?.reload(); if (!dnd) Core.info(this, "Drawing updated", "Your partner saved a new drawing") }
                    }
                } catch (e: Exception) {}
            }.start()
            return
        }
        if (dnd) return
        if (t == "msg") {
            Thread {
                var bmp: Bitmap? = null
                try { val u = m.optString("img"); if (u.isNotEmpty()) bmp = URL(u).openStream().use { BitmapFactory.decodeStream(it) } } catch (e: Exception) {}
                ui.post { Core.custom(this, m.optString("title"), m.optString("body"), bmp) }
            }.start()
            return
        }
        ui.post {
          try {
            when (t) {
                "wake" -> if (age > 900) Core.infoId(this, 3, "Missed wake-up", "A wake-up arrived ${age / 60} min late, so the alarm was skipped")
                          else { Core.freshWake(this); Alarm.start(this) }
                "polite" -> Core.withReply(this, Core.politeChannel(this), "Are you awake?", "Your partner is checking on you", "Yesh", "yesh")
                "meow" -> Core.withReply(this, Core.CH_INFO, "Nyaaa", "", "Meowww", "meowr")
                "yesh" -> Core.info(this, "Yesh", "They're awake")
                "meowr" -> Core.info(this, "Meowww", "")
                "ack" -> Core.info(this, "They're up", "Your partner confirmed they're awake")
                "late" -> Core.info(this, "Still asleep", "They haven't confirmed they're up. Tap to wake them!")
            }
          } catch (e: Throwable) { Core.log(this, 2, "Error: " + e.javaClass.simpleName) }
        }
    }

    private fun handleMacro(m: JSONObject): Boolean {
        if (m.optString("t") != "nlog") return false
        return try {
            if (!MacroLog.firstTime(this, m.optString("id"))) false
            else {
                val txt = GZIPInputStream(java.io.ByteArrayInputStream(Base64.decode(m.getString("z"), Base64.NO_WRAP))).bufferedReader().readText()
                val arr = JSONArray(txt); MacroLog.append(this, arr); Core.macroNotify(this, arr); true
            }
        } catch (e: Exception) { false }
    }

    private fun pollOnce(deep: Boolean): String {
        val p = Core.prefs(this)
        val topic = p.getString("mine", "")!!
        if (topic.isEmpty()) return "No code set"
        return try {
            val last0 = p.getLong("lastT", 0L)
            val since = if (deep || last0 == 0L) System.currentTimeMillis() / 1000 - 43200 else last0 - 60
            val h = URL("https://ntfy.sh/$topic/json?poll=1&since=$since").openConnection() as HttpURLConnection
            h.connectTimeout = 15000; h.readTimeout = 30000
            h.setRequestProperty("Accept-Encoding", "identity"); h.useCaches = false
            var logs = 0; var fresh = 0; var rec = 0
            h.inputStream.bufferedReader().forEachLine { line ->
                try {
                    val o = JSONObject(line)
                    if (o.optString("event") == "message") {
                        val m = JSONObject(o.getString("message"))
                        if (m.optString("t") == "nlog") { logs++; if (handleMacro(m)) { fresh++; rec++ } }
                        else {
                            val tm = o.optLong("time"); val id = o.optString("id"); val lt = p.getLong("lastT", 0L)
                            if (tm > lt || (tm == lt && id != p.getString("lastId", ""))) {
                                p.edit().putLong("lastT", tm).putString("lastId", id).apply()
                                val age = System.currentTimeMillis() / 1000 - tm
                                if (age < 21600) { handle(m, age); rec++ }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            if (!deep && rec > 0) Core.log(this, 2, "Catch-up recovered $rec message(s)")
            if (logs == 0) "Nothing from the macro phone for this code. Check that its Partner's code matches your Your code." else "$fresh new, $logs found on the server"
        } catch (e: Exception) { "Can't reach ntfy.sh" }
    }

    private fun loop(key: String, sfx: String, cb: (JSONObject, Long) -> Unit) {
        while (true) {
            val p = Core.prefs(this)
            val topic = p.getString(key, "")!!
            if (topic.isEmpty()) { Thread.sleep(5000); continue }
            try {
                val last = p.getLong("lastT$sfx", 0L)
                val since = if (last > 0) last else System.currentTimeMillis() / 1000 - 7200
                val h = URL("https://ntfy.sh/$topic/json?since=$since").openConnection() as HttpURLConnection
                h.readTimeout = 120000; h.connectTimeout = 20000; conn = h
                h.setRequestProperty("Accept-Encoding", "identity"); h.useCaches = false
                h.inputStream.bufferedReader().forEachLine { line ->
                    if (Core.prefs(this).getString(key, "") != topic) throw RuntimeException("code changed")
                    p.edit().putLong("hb$sfx", System.currentTimeMillis()).apply()
                    try {
                        val o = JSONObject(line)
                        if (o.optString("event") == "message") {
                            val tm = o.optLong("time"); val id = o.optString("id")
                            val lt = p.getLong("lastT$sfx", 0L)
                            if (tm > lt || (tm == lt && id != p.getString("lastId$sfx", ""))) {
                                p.edit().putLong("lastT$sfx", tm).putString("lastId$sfx", id).apply()
                                val age = System.currentTimeMillis() / 1000 - tm
                                if (age < 21600) cb(JSONObject(o.getString("message")), age)
                            }
                        }
                    } catch (e: Throwable) {}
                }
            } catch (e: Throwable) {}
            Thread.sleep(5000)
        }
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Core.armWatchdog(c)
        if (!ListenService.running || !ListenService.alive) { try { c.startForegroundService(Intent(c, ListenService::class.java)) } catch (e: Exception) {} }
        else { val pr = goAsync(); Thread { try { ListenService.poller?.invoke(false) } catch (e: Exception) {}; pr.finish() }.start() }
    }
}

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val r = i.getStringExtra("r") ?: return
        val pr = goAsync()
        try { c.getSystemService(NotificationManager::class.java).cancel(i.getIntExtra("n", 0)) } catch (e: Exception) {}
        Core.send(c, r) { pr.finish() }
    }
}

object Backup {
    private val SKIP = setOf("lastT", "lastId", "hb", "snz", "snzu", "pend", "dnd_until", "bk_ok")
    private fun dir(): java.io.File? = try {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) null
        else java.io.File(Environment.getExternalStorageDirectory(), "WakeUp").also { it.mkdirs() }
    } catch (e: Exception) { null }
    fun available(): Boolean = dir() != null
    fun hasFile(): Boolean = dir()?.let { java.io.File(it, "backup.json").exists() } ?: false

    @Synchronized fun save(c: Context) {
        val d = dir() ?: return
        val p = Core.prefs(c)
        if (p.getString("mine", "").isNullOrEmpty()) return
        val f = java.io.File(d, "backup.json")
        if (f.exists() && !p.getBoolean("bk_ok", false)) return
        try {
            val o = JSONObject()
            for ((k, v) in p.all) {
                if (k in SKIP) continue
                when (v) {
                    is String -> o.put(k, JSONArray().put("s").put(v))
                    is Boolean -> o.put(k, JSONArray().put("b").put(v))
                    is Int -> o.put(k, JSONArray().put("i").put(v))
                    is Long -> o.put(k, JSONArray().put("l").put(v))
                }
            }
            f.writeText(o.toString())
            val bf = Board.file(c); if (bf.exists()) bf.copyTo(java.io.File(d, "board.png"), true)
            if (!p.getBoolean("bk_ok", false)) p.edit().putBoolean("bk_ok", true).apply()
        } catch (e: Exception) {}
    }
    fun restore(c: Context): Boolean {
        val d = dir() ?: return false
        val f = java.io.File(d, "backup.json"); if (!f.exists()) return false
        return try {
            val o = JSONObject(f.readText()); val e = Core.prefs(c).edit()
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                when (a.getString(0)) { "s" -> e.putString(k, a.getString(1)); "b" -> e.putBoolean(k, a.getBoolean(1)); "i" -> e.putInt(k, a.getInt(1)); "l" -> e.putLong(k, a.getLong(1)) }
            }
            e.putBoolean("bk_ok", true).apply()
            val bf = java.io.File(d, "board.png"); if (bf.exists()) bf.copyTo(Board.file(c), true)
            val lf = java.io.File(d, "macro_log.jsonl"); if (lf.exists()) lf.copyTo(java.io.File(c.filesDir, "macro_log.jsonl"), true)
            true
        } catch (e: Exception) { false }
    }
    fun saveLog(c: Context) {
        val d = dir() ?: return
        try { val lf = java.io.File(c.filesDir, "macro_log.jsonl"); if (lf.exists() && lf.length() < 30_000_000 && java.io.File(d, "backup.json").exists()) lf.copyTo(java.io.File(d, "macro_log.jsonl"), true) } catch (e: Exception) {}
    }
    fun restoreIfFresh(c: Context) { if (Core.prefs(c).getString("mine", "").isNullOrEmpty()) restore(c) }
}

// ---------- Macro phone: capture + forward ----------
val IGNORED_PKGS = setOf("com.spotify.music", "com.spotify.lite")

object MacroQueue {
    private fun f(c: Context) = java.io.File(c.filesDir, "nq.jsonl")
    @Synchronized fun add(c: Context, o: JSONObject) {
        try {
            val file = f(c); file.appendText(o.toString() + "\n")
            if (file.length() > 3_000_000) { val l = file.readLines(); file.writeText(l.takeLast(l.size / 2).joinToString("\n") + "\n") }
        } catch (e: Exception) {}
    }
    @Synchronized fun peek(c: Context, max: Int): List<JSONObject> =
        try { f(c).useLines { it.take(max).map { s -> JSONObject(s) }.toList() } } catch (e: Exception) { emptyList() }
    @Synchronized fun drop(c: Context, n: Int) {
        try { val file = f(c); val l = file.readLines(); file.writeText(l.drop(n).joinToString("\n") + (if (l.size > n) "\n" else "")) } catch (e: Exception) {}
    }
    @Synchronized fun size(c: Context): Int = try { f(c).useLines { it.count() } } catch (e: Exception) { 0 }
}

object NotifForward {
    private fun gz(s: String): String {
        val bos = ByteArrayOutputStream(); GZIPOutputStream(bos).use { it.write(s.toByteArray()) }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }
    private val sent = ArrayList<Long>()
    @Synchronized fun busy(): Boolean { val cut = System.currentTimeMillis() - 3600000L; sent.removeAll { it < cut }; return sent.size > 60 }
    @Synchronized fun flushNow(c: Context): Boolean {
        val p = Core.prefs(c); val target = p.getString("theirs", "")!!.trim()
        if (target.isEmpty()) { p.edit().putString("nf_err", "Add your main phone's code as Partner's code").apply(); return true }
        while (true) {
            val es = MacroQueue.peek(c, 80); if (es.isEmpty()) return true
            val arr = JSONArray(); var best = ""; var n = 0
            for (e in es) { arr.put(e); val z = gz(arr.toString()); if (z.length > 3000) break; best = z; n++ }
            if (n == 0) { MacroQueue.drop(c, 1); continue }
            val body = JSONObject().put("t", "nlog").put("id", Core.rid()).put("z", best).put("n", n).toString().toByteArray()
            val err = Core.postToErr(target, body)
            if (err.isNotEmpty()) { p.edit().putString("nf_err", err).apply(); return false }
            MacroQueue.drop(c, n); p.edit().putLong("nf_last", System.currentTimeMillis()).putString("nf_err", "").apply(); sent.add(System.currentTimeMillis())
            Thread.sleep(1500)
        }
    }
}

class NotifCapture : NotificationListenerService() {
    private val ui = Handler(Looper.getMainLooper())
    private val last = HashMap<String, Pair<String, Long>>()
    private var scheduled = false
    private var pending = 0
    private val flush = Runnable {
        scheduled = false; pending = 0
        Thread {
            var ok = false
            try { ok = NotifForward.flushNow(applicationContext) } catch (e: Exception) {}
            ui.post { if (!ok) scheduleIn(60000L) }
        }.start()
    }
    private fun scheduleIn(ms: Long) {
        if (scheduled) return
        scheduled = true; ui.postDelayed(flush, ms)
    }
    override fun onListenerConnected() { scheduleIn(3000L) }
    override fun onListenerDisconnected() {
        ui.removeCallbacks(flush); scheduled = false
        try { requestRebind(ComponentName(this, NotifCapture::class.java)) } catch (e: Exception) {}
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!BuildConfig.MACRO) return
        val p = Core.prefs(this)
        if (!p.getBoolean("nf_on", true) || sbn.packageName == packageName || sbn.packageName in IGNORED_PKGS) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || n.category == Notification.CATEGORY_TRANSPORT) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        if (title.isEmpty() && text.isEmpty() && big.isEmpty()) return
        val body = if (big.length > text.length) big else text
        val key = title + "|" + body; val now = System.currentTimeMillis()
        val prev = last[sbn.packageName]
        if (prev != null && prev.first == key && now - prev.second < 30000) return
        last[sbn.packageName] = Pair(key, now)
        val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
        MacroQueue.add(this, JSONObject().put("ts", sbn.postTime).put("pkg", sbn.packageName).put("app", app)
            .put("title", title.take(200)).put("text", body.take(600)).put("sub", sub.take(100)))
        pending++
        if (pending >= 40) { ui.removeCallbacks(flush); scheduled = false; ui.post(flush) } else scheduleIn(if (NotifForward.busy()) 60000L else 2000L)
    }
}

// ---------- Main phone: stored macro log ----------
object MacroLog {
    private fun f(c: Context) = java.io.File(c.filesDir, "macro_log.jsonl")
    @Synchronized private fun lines(c: Context): List<String> = try { f(c).readLines() } catch (e: Exception) { emptyList() }
    @Synchronized fun append(c: Context, a: JSONArray) {
        try {
            var kept = 0
            f(c).appendText(buildString { for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.optString("pkg") !in IGNORED_PKGS) { append(o.toString()).append('\n'); kept++ } } })
            val p = Core.prefs(c)
            p.edit().putInt("ml_count", p.getInt("ml_count", 0) + kept).putLong("ml_last", System.currentTimeMillis()).apply()
            if (f(c).length() > 8_000_000) { val l = f(c).readLines(); f(c).writeText(l.takeLast(l.size / 2).joinToString("\n") + "\n") }
        } catch (e: Exception) {}
        Handler(Looper.getMainLooper()).post { Hooks.onMacro?.invoke() }
    }
    @Synchronized fun firstTime(c: Context, id: String): Boolean {
        if (id.isEmpty()) return true
        val p = Core.prefs(c)
        val l = (p.getString("ml_seen", "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()
        if (l.contains(id)) return false
        l.add(id); p.edit().putString("ml_seen", l.takeLast(300).joinToString(",")).apply()
        return true
    }
    fun query(c: Context, q: String, apps: Set<String>): List<JSONObject> {
        val out = ArrayList<JSONObject>(); val ls = lines(c); var i = ls.size - 1
        while (i >= 0 && out.size < 5000) {
            try {
                val o = JSONObject(ls[i])
                if ((apps.isEmpty() || apps.contains(o.optString("app"))) &&
                    (q.isEmpty() || (o.optString("app") + " " + o.optString("title") + " " + o.optString("text")).lowercase().contains(q))) out.add(o)
            } catch (e: Exception) {}
            i--
        }
        return out
    }
    @Synchronized fun purge(c: Context) {
        try {
            val keep = lines(c).filter { l -> try { JSONObject(l).optString("pkg") !in IGNORED_PKGS } catch (e: Exception) { false } }
            f(c).writeText(if (keep.isEmpty()) "" else keep.joinToString("\n") + "\n")
            Core.prefs(c).edit().putInt("ml_count", keep.size).apply()
        } catch (e: Exception) {}
    }
    fun apps(c: Context): List<String> =
        lines(c).mapNotNull { try { JSONObject(it).optString("app") } catch (e: Exception) { null } }.filter { it.isNotEmpty() }.distinct().sorted()
    fun csv(c: Context): String {
        val sb = StringBuilder("time,app,package,title,text,extra\n")
        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        fun q(s: String) = "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\""
        for (l in lines(c)) try {
            val o = JSONObject(l)
            sb.append(f.format(java.util.Date(o.optLong("ts")))).append(',').append(q(o.optString("app"))).append(',').append(q(o.optString("pkg"))).append(',')
                .append(q(o.optString("title"))).append(',').append(q(o.optString("text"))).append(',').append(q(o.optString("sub"))).append('\n')
        } catch (e: Exception) {}
        return sb.toString()
    }
    @Synchronized fun clear(c: Context) {
        try { f(c).delete() } catch (e: Exception) {}
        Core.prefs(c).edit().putInt("ml_count", 0).putLong("ml_last", 0L).apply()
    }
}

class MacroLogActivity : Base() {
    private lateinit var list: LinearLayout
    private lateinit var q: EditText
    private lateinit var appBtn: Button
    private lateinit var info: TextView
    private var appFilter: Set<String> = emptySet()
    private fun appLabel(x: Set<String>) = when { x.isEmpty() -> "Apps: All"; x.size == 1 -> "Apps: " + x.first(); else -> "Apps: ${x.size} selected" }
    private var matches: List<JSONObject> = emptyList()
    private var shown = 0
    private val ui = Handler(Looper.getMainLooper())
    private val fmt = java.text.SimpleDateFormat("MMM d, HH:mm:ss", java.util.Locale.getDefault())

    override fun onDestroy() { Hooks.onMacro = null; super.onDestroy() }
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        q = field("Search", "").apply {
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { reload() }
                override fun beforeTextChanged(s: CharSequence?, st: Int, bf: Int, ct: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, ct: Int) {}
            })
        }
        appFilter = Core.prefs(this).getStringSet("ml_apps", emptySet())?.toSet() ?: emptySet()
        appBtn = btn(appLabel(appFilter), false) { pickApp() }
        info = tv("", 13f, t.sub)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val clear = btn("Clear log", false) {
            AlertDialog.Builder(this).setTitle("Clear the whole log?").setMessage("This deletes every stored macro-phone notification on this phone.")
                .setPositiveButton("Clear") { _, _ -> MacroLog.clear(this); reload() }.setNegativeButton("Cancel", null).show()
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(16)) }
        col.addView(btn("‹  Back", false) { finish() }, LinearLayout.LayoutParams(dpi(130), dpi(54)))
        col.put(tv("Macro phone log", 28f, t.text, true), 8)
        col.put(q, 12); col.put(row(appBtn, btn("Export CSV", false) { export() }), 10); col.put(info, 8)
        col.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dpi(8) })
        col.put(clear, 8)
        val root = FrameLayout(this); root.addView(col, FrameLayout.LayoutParams(-1, -1)); show(root)
        Hooks.onMacro = { reload() }
        reload()
    }
    private fun reload() {
        val query = q.text.toString().trim().lowercase(); val af = appFilter
        Thread { val r = MacroLog.query(this, query, af); ui.post { matches = r; shown = 0; list.removeAllViews(); info.text = "${r.size} entries"; more() } }.start()
    }
    private fun more() {
        val end = minOf(matches.size, shown + 80)
        for (i in shown until end) {
            val o = matches[i]; val v = ArrayList<View>()
            v.add(tv(o.optString("app") + "  ·  " + fmt.format(java.util.Date(o.optLong("ts"))), 12f, t.ac1, true))
            if (o.optString("title").isNotEmpty()) v.add(tv(o.optString("title"), 15f, t.text, true))
            if (o.optString("text").isNotEmpty()) v.add(tv(o.optString("text"), 13f, t.sub))
            list.put(card(*v.toTypedArray()), if (i == 0) 0 else 8)
        }
        shown = end
        if (shown < matches.size) list.put(btn("Load more", false) { list.removeViewAt(list.childCount - 1); more() }, 10)
    }
    private fun pickApp() {
        Thread {
            val names = MacroLog.apps(this)
            ui.post {
                val sel = BooleanArray(names.size) { appFilter.contains(names[it]) }
                AlertDialog.Builder(this).setTitle("Show these apps")
                    .setMultiChoiceItems(names.toTypedArray(), sel) { _, i, on -> sel[i] = on }
                    .setPositiveButton("Apply") { _, _ -> applyApps(names.filterIndexed { i, _ -> sel[i] }.toSet()) }
                    .setNeutralButton("All apps") { _, _ -> applyApps(emptySet()) }
                    .setNegativeButton("Cancel", null).show()
            }
        }.start()
    }
    private fun applyApps(x: Set<String>) {
        appFilter = x
        Core.prefs(this).edit().putStringSet("ml_apps", x).apply()
        appBtn.text = appLabel(x)
        reload()
    }
    private fun export() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE, "macro-log.csv"), 31)
    }
    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        val u = d?.data ?: return
        if (r == 31 && c == RESULT_OK) Thread { try { contentResolver.openOutputStream(u)?.use { it.write(MacroLog.csv(this).toByteArray()) } } catch (e: Exception) {} }.start()
    }
}

// ---------- Hooks / Board ----------
object Hooks { var onLog: (() -> Unit)? = null; var onMacro: (() -> Unit)? = null }

val PAPER = Color.parseColor("#FAF8F2")

object Board {
    const val W = 1000; const val H = 1400
    var view: DrawView? = null
    fun file(c: Context) = java.io.File(c.filesDir, "board.png")
    fun load(c: Context): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); cv.drawColor(PAPER)
        try {
            val f = file(c)
            if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { cv.drawBitmap(Bitmap.createScaledBitmap(it, W, H, true), 0f, 0f, null) }
        } catch (e: Exception) {}
        return bmp
    }
    fun store(c: Context, bmp: Bitmap) {
        try { file(c).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) {}
    }
}

class Stroke(val color: Int, val size: Float, val eraser: Boolean, val clear: Boolean = false) { val pts = ArrayList<Float>() }

class DrawView(private val c: Context) : View(c) {
    private val bmp = Board.load(c)
    private val cv = Canvas(bmp)
    private var base: Bitmap = bmp.copy(Bitmap.Config.ARGB_8888, false)
    private val strokes = ArrayList<Stroke>()
    private var cur: Stroke? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val m = Matrix()
    private val inv = Matrix()
    var color = Color.parseColor("#111111")
    var eraser = false
    var size = 14f
    private var lx = 0f; private var ly = 0f; private var mx = 0f; private var my = 0f

    override fun onLayout(ch: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(ch, l, t, r, b)
        if (Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(Rect(0, 0, r - l, b - t))
    }
    override fun onDraw(canvas: Canvas) {
        val s = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        m.reset(); m.postScale(s, s); m.postTranslate((width - bmp.width * s) / 2, (height - bmp.height * s) / 2); m.invert(inv)
        canvas.drawBitmap(bmp, m, null)
    }
    private fun setup(s: Stroke) { paint.color = if (s.eraser) PAPER else s.color; paint.strokeWidth = s.size }
    private fun render(s: Stroke) {
        if (s.clear) { cv.drawColor(PAPER); return }
        setup(s)
        val p = s.pts
        if (p.size < 2) return
        if (p.size == 2) { cv.drawPoint(p[0], p[1], paint); return }
        path.reset(); path.moveTo(p[0], p[1])
        var px = p[0]; var py = p[1]; var i = 2
        while (i < p.size) { val x = p[i]; val y = p[i + 1]; path.quadTo(px, py, (x + px) / 2, (y + py) / 2); px = x; py = y; i += 2 }
        path.lineTo(px, py); cv.drawPath(path, paint)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)
        val pt = floatArrayOf(e.x, e.y); inv.mapPoints(pt)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val s = Stroke(color, size, eraser); s.pts.add(pt[0]); s.pts.add(pt[1]); cur = s; setup(s)
                lx = pt[0]; ly = pt[1]; mx = lx; my = ly; cv.drawPoint(lx, ly, paint)
            }
            MotionEvent.ACTION_MOVE -> cur?.let { s ->
                s.pts.add(pt[0]); s.pts.add(pt[1]); setup(s)
                val midx = (pt[0] + lx) / 2; val midy = (pt[1] + ly) / 2
                path.reset(); path.moveTo(mx, my); path.quadTo(lx, ly, midx, midy); cv.drawPath(path, paint)
                mx = midx; my = midy; lx = pt[0]; ly = pt[1]
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cur?.let { s ->
                setup(s); path.reset(); path.moveTo(mx, my); path.lineTo(lx, ly); cv.drawPath(path, paint)
                strokes.add(s); cur = null
                if (strokes.size > 1500) { base.recycle(); base = bmp.copy(Bitmap.Config.ARGB_8888, false); strokes.clear() }
            }
        }
        invalidate(); return true
    }
    fun undo() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.size - 1)
        cv.drawBitmap(base, 0f, 0f, null)
        for (s in strokes) render(s)
        invalidate()
    }
    fun clear() { strokes.add(Stroke(0, 0f, false, true)); cv.drawColor(PAPER); invalidate() }
    fun reload() { base.recycle(); base = Board.load(c); strokes.clear(); cv.drawBitmap(base, 0f, 0f, null); invalidate() }
    fun saveLocal() = Board.store(c, bmp)
    fun snapshot(): Bitmap = bmp.copy(Bitmap.Config.ARGB_8888, false)
}

class Sq(c: Context) : View(c) {
    override fun onMeasure(w: Int, h: Int) { val s = MeasureSpec.getSize(w); setMeasuredDimension(s, s) }
}

class AspectFrame(c: Context) : FrameLayout(c) {
    override fun onMeasure(w: Int, h: Int) {
        val aw = MeasureSpec.getSize(w); val ah = MeasureSpec.getSize(h)
        val r = Board.W.toFloat() / Board.H
        var tw = aw; var th = (aw / r).toInt()
        if (th > ah) { th = ah; tw = (ah * r).toInt() }
        super.onMeasure(MeasureSpec.makeMeasureSpec(tw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(th, MeasureSpec.EXACTLY))
    }
}

class HScroll(c: Context) : HorizontalScrollView(c) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        return super.dispatchTouchEvent(ev)
    }
}

// ---------- Swipe pager ----------
class Pager(c: Context, private val onScroll: (Float) -> Unit, private val onSettle: (Int) -> Unit) : FrameLayout(c) {
    var pos = 0f
    private var downX = 0f; private var downY = 0f; private var lastX = 0f
    private var drag = false
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var vt: VelocityTracker? = null
    private var anim: ValueAnimator? = null

    private fun apply() {
        for (i in 0 until childCount) {
            val v = getChildAt(i); val d = i - pos
            v.translationX = d * width
            v.visibility = if (Math.abs(d) < 1f) View.VISIBLE else View.INVISIBLE
        }
        onScroll(pos)
    }
    override fun onLayout(ch: Boolean, l: Int, t: Int, r: Int, b: Int) { super.onLayout(ch, l, t, r, b); apply() }

    fun goTo(i: Int, animate: Boolean = true) {
        anim?.cancel()
        try { context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0) } catch (e: Exception) {}
        val target = i.coerceIn(0, childCount - 1)
        if (!animate) { pos = target.toFloat(); apply(); onSettle(target); return }
        anim = ValueAnimator.ofFloat(pos, target.toFloat()).apply {
            duration = 320; interpolator = DecelerateInterpolator()
            addUpdateListener { pos = it.animatedValue as Float; apply() }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { onSettle(target) } })
            start()
        }
    }
    private fun track(e: MotionEvent) { if (vt == null) vt = VelocityTracker.obtain(); vt!!.addMovement(e) }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { downX = e.x; downY = e.y; lastX = e.x; drag = false; anim?.cancel(); vt?.recycle(); vt = null }
        track(e)
        if (e.actionMasked == MotionEvent.ACTION_MOVE && !drag &&
            Math.abs(e.x - downX) > slop && Math.abs(e.x - downX) > Math.abs(e.y - downY)) { drag = true; lastX = e.x }
        return drag
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { downX = e.x; downY = e.y; lastX = e.x; drag = false; anim?.cancel(); vt?.recycle(); vt = null }
        track(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!drag && Math.abs(e.x - downX) > slop && Math.abs(e.x - downX) > Math.abs(e.y - downY)) { drag = true; lastX = e.x }
                if (drag) { pos = (pos - (e.x - lastX) / width).coerceIn(0f, childCount - 1f); lastX = e.x; apply() }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.computeCurrentVelocity(1000); val vx = vt?.xVelocity ?: 0f
                vt?.recycle(); vt = null
                var tgt = Math.round(pos)
                if (vx < -800f) tgt = Math.ceil(pos.toDouble()).toInt() else if (vx > 800f) tgt = Math.floor(pos.toDouble()).toInt()
                drag = false; goTo(tgt)
            }
        }
        return true
    }
}

// ---------- UI base ----------
abstract class Base : Activity() {
    lateinit var t: Th
    private var ai = -1
    private var rootV: View? = null

    fun dp(x: Int): Float = x * resources.displayMetrics.density
    fun dpi(x: Int): Int = dp(x).toInt()
    private fun tIdx() = Core.prefs(this).getInt("theme", 0).coerceIn(0, THEMES.size - 1)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ai = tIdx(); t = THEMES[ai]
        window.statusBarColor = t.bg1; window.navigationBarColor = t.bg2
        if (!t.dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    override fun onResume() { super.onResume(); if (tIdx() != ai) recreate() }

    fun show(v: FrameLayout) {
        v.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.bg1, t.bg2))
        v.isFocusableInTouchMode = true; rootV = v; setContentView(v)
    }
    fun shape(c: Int, r: Float) = GradientDrawable().apply { setColor(c); cornerRadius = r }
    fun tv(x: String, size: Float, color: Int = t.text, bold: Boolean = false) = TextView(this).apply {
        text = x; textSize = size; setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }
    fun sec(x: String) = tv(x, 12f, t.sub, true).apply { letterSpacing = 0.15f }
    fun btn(label: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; stateListAnimator = null
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) t.btnText else t.text)
        val base = if (primary) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(t.ac1, t.ac2)).apply { cornerRadius = dp(28) }
                   else shape(t.card, dp(28))
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), base, null)
        setPadding(dpi(20), dpi(14), dpi(20), dpi(14))
        setOnClickListener { click() }
    }
    fun mini(label: String, primary: Boolean, click: () -> Unit) =
        btn(label, primary, click).apply { textSize = 14f; setPadding(dpi(10), dpi(8), dpi(10), dpi(8)) }
    fun sw(label: String, on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = label; setTextColor(t.text); textSize = 15f; isChecked = on
        thumbTintList = ColorStateList.valueOf(t.ac1); trackTintList = ColorStateList.valueOf(t.sub)
        setOnCheckedChangeListener { _, v -> f(v) }
    }
    fun field(hint: String, value: String) = EditText(this).apply {
        setText(value); setHint(hint); setTextColor(t.text); setHintTextColor(t.sub); textSize = 15f; isSingleLine = true
        background = shape(if (t.dark) 0x22FFFFFF else 0x14000000, dp(14)); setPadding(dpi(14), dpi(12), dpi(14), dpi(12))
    }
    fun LinearLayout.put(v: View, top: Int = 12, h: Int = -2) {
        addView(v, LinearLayout.LayoutParams(-1, h).apply { topMargin = dpi(top) })
    }
    fun card(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = shape(t.card, dp(24))
        setPadding(dpi(18), dpi(16), dpi(18), dpi(16))
        v.forEachIndexed { i, x -> put(x, if (i == 0) 0 else 10) }
    }
    fun row(a: View, b: View, w1: Float = 1f, w2: Float = 1f) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, -2, w1).apply { marginEnd = dpi(6) })
        addView(b, LinearLayout.LayoutParams(0, -2, w2).apply { marginStart = dpi(6) })
    }
    fun flash(v: TextView, x: String) { val o = v.text; v.text = x; v.postDelayed({ v.text = o }, 1500) }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val f = currentFocus
            if (f is EditText) {
                val r = Rect(); f.getGlobalVisibleRect(r)
                if (!r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    f.clearFocus(); rootV?.requestFocus()
                    getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(f.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

// ---------- Alarm screen ----------
class AlarmActivity : Base() {
    private val h = Handler(Looper.getMainLooper())
    private val chk = object : Runnable { override fun run() { if (!Alarm.ringing) finish() else h.postDelayed(this, 500) } }
    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val p = Core.prefs(this)
        val left = 2 - p.getInt("snz", 0)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dpi(28), dpi(28), dpi(28), dpi(28)) }
        col.put(tv("WAKE UP", 18f, t.sub, true).apply { letterSpacing = 0.3f; gravity = Gravity.CENTER }, 0)
        col.put(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"; textSize = 84f; setTextColor(t.text)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.CENTER
        }, 8)
        if (Core.appMode(this)) {
            val nm = p.getString("stop_name", "the app")!!
            col.put(tv("Open $nm to stop the alarm", 18f, t.text, true).apply { gravity = Gravity.CENTER }, 40)
            col.put(btn("Open $nm", true) { openApp() }.apply { textSize = 20f }, 24, dpi(104))
        } else {
            col.put(btn("I'M UP", true) { up() }.apply { textSize = 20f }, 48, dpi(104))
            if (left > 0) col.put(btn("Snooze 5 min  ($left left)", false) { snooze() }.apply { textSize = 20f }, 14, dpi(104))
        }
        val root = FrameLayout(this); root.addView(col, FrameLayout.LayoutParams(-1, -1)); show(root)
        h.postDelayed(chk, 1500)
    }
    private fun openApp() {
        val pk = Core.prefs(this).getString("stop_pkg", "")!!
        try { packageManager.getLaunchIntentForPackage(pk)?.let { startActivity(it) } } catch (e: Exception) {}
        if (!Core.hasUsage(this)) { Core.finishWake(this); finish() }
    }
    private fun up() { Core.finishWake(this); finish() }
    private fun snooze() {
        Alarm.stop(this)
        val p = Core.prefs(this)
        val at = System.currentTimeMillis() + 5 * 60 * 1000L
        p.edit().putInt("snz", p.getInt("snz", 0) + 1).putLong("snzu", at + 10 * 60 * 1000L).apply()
        Core.log(this, 2, "You snoozed")
        try {
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(at, open), snoozePi(this))
        } catch (e: Exception) {}
        finish()
    }
}

// ---------- Main: 5 swipeable tabs ----------
class MainActivity : Base() {
    companion object { var keepPage = 2; var keepY = 0; var keepX = 0 }
    private lateinit var pager: Pager
    private lateinit var draw: DrawView
    private lateinit var grace: EditText
    private lateinit var histBox: LinearLayout
    private lateinit var mine: EditText
    private lateinit var theirs: EditText
    private lateinit var musicLbl: TextView
    private lateinit var stopLbl: TextView
    private lateinit var mlLbl: TextView
    private var mlNote = ""
    private var permDlg: Dialog? = null
    private var permRows: (() -> Unit)? = null
    private lateinit var dndBtn: Button
    private lateinit var tField: EditText
    private lateinit var bField: EditText
    private lateinit var preview: ImageView
    private lateinit var tplBox: LinearLayout
    private lateinit var nStatus: TextView
    private var img: Uri? = null
    private val argb = ArgbEvaluator()
    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { refreshDnd(); updateNf(); ui.postDelayed(this, 15000) } }

    private fun saveGrace() { Core.prefs(this).edit().putInt("grace", grace.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: 15).apply() }
    private fun saveCodes() {
        Core.prefs(this).edit().putString("mine", mine.text.toString().trim()).putString("theirs", theirs.text.toString().trim()).apply()
    }
    override fun onPause() { saveGrace(); saveCodes(); draw.saveLocal(); Backup.save(this); if (!BuildConfig.MACRO) Backup.saveLog(this); super.onPause() }
    override fun onDestroy() { Hooks.onLog = null; Board.view = null; ui.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onResume() { super.onResume(); Core.channels(this); Core.armWatchdog(this); refreshDnd(); updateNf(); autoRestore(); permRows?.invoke() }

    private fun updateTabs(tabs: List<ImageView>, pos: Float) {
        tabs.forEachIndexed { i, v ->
            val k = 1f - Math.min(1f, Math.abs(i - pos))
            val sc = 1f + 0.6f * k
            v.scaleX = sc; v.scaleY = sc; v.translationY = -dpi(7) * k
            v.setColorFilter(argb.evaluate(k, t.sub, t.ac1) as Int)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Backup.restoreIfFresh(this)
        Core.channels(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val p = Core.prefs(this)
        if (p.getString("mine", "").isNullOrEmpty()) {
            val c = "abcdefghijkmnpqrstuvwxyz23456789"
            p.edit().putString("mine", "wake-" + (1..12).map { c.random() }.joinToString("")).apply()
        }
        if (!BuildConfig.MACRO && !p.getBoolean("ml_purged1", false)) { p.edit().putBoolean("ml_purged1", true).apply(); Thread { MacroLog.purge(this) }.start() }
        val pages = listOf(drawPage(), notifyPage(), wakePage(p), histPage(), settingsPage(p))
        Hooks.onLog = { renderHist() }

        val tabs = ArrayList<ImageView>()
        pager = Pager(this, { pos -> updateTabs(tabs, pos) }, { i -> if (i == 3) renderHist() })
        pager.pos = keepPage.toFloat()
        pages.forEach { pager.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = shape(t.card, dp(32)); clipChildren = false; clipToPadding = false
        }
        val icons = intArrayOf(R.drawable.ic_brush, R.drawable.ic_bell, R.drawable.ic_alarm, R.drawable.ic_history, R.drawable.ic_settings)
        icons.forEachIndexed { i, r ->
            val v = ImageView(this).apply { setImageResource(r); scaleType = ImageView.ScaleType.CENTER_INSIDE; setPadding(dpi(20), dpi(20), dpi(20), dpi(20)); setOnClickListener { pager.goTo(i) } }
            tabs.add(v); bar.addView(v, LinearLayout.LayoutParams(0, dpi(68), 1f))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        col.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dpi(14), dpi(6), dpi(14), dpi(14)) })
        val root = FrameLayout(this); root.clipChildren = false
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        show(root)
        keepPage = 2
        if (!ListenService.running || !ListenService.alive) startForegroundService(Intent(this, ListenService::class.java))
        ui.post(tick)
        if (!p.getBoolean("perm_seen", false)) { p.edit().putBoolean("perm_seen", true).apply(); ui.postDelayed({ showPerms() }, 700) }
    }

    // ----- shared: "Sent" then "Delivered" -----
    private fun track(status: TextView, id: String) {
        Core.watch(id) { dnd -> status.text = if (dnd) "Delivered (their DND is on)" else "Delivered" }
    }
    private fun sentText(status: TextView) {
        status.text = "Sent"
        status.postDelayed({ if (status.text == "Sent") status.text = "Sent. Not delivered yet" }, 15000)
    }

    // ----- tab 0: drawing pad -----
    private fun drawPage(): View {
        draw = DrawView(this); Board.view = draw
        val frame = AspectFrame(this).apply { background = shape(PAPER, dp(20)); clipToOutline = true; addView(draw, FrameLayout.LayoutParams(-1, -1)) }
        val status = tv("", 13f, t.sub).apply { gravity = Gravity.CENTER }
        val colors = listOf("#111111", "#FFFFFF", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA")
        val dots = ArrayList<View>()
        val eraseBtn = mini("Eraser", false) {}
        fun refresh() {
            dots.forEachIndexed { i, v ->
                val cc = Color.parseColor(colors[i]); val sel = !draw.eraser && draw.color == cc
                v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cc); setStroke(dpi(if (sel) 3 else 1), if (sel) t.ac1 else t.sub) }
            }
            eraseBtn.text = if (draw.eraser) "Eraser on" else "Eraser"
        }
        val dotRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colors.forEach { cs ->
            val v = Sq(this); dots.add(v)
            v.setOnClickListener { draw.color = Color.parseColor(cs); draw.eraser = false; refresh() }
            dotRow.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dpi(3), 0, dpi(3), 0) })
        }
        eraseBtn.setOnClickListener { draw.eraser = !draw.eraser; refresh() }
        refresh()
        val undoBtn = mini("Undo", false) { draw.undo() }
        val clearBtn = mini("Clear", false) { draw.clear() }
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(eraseBtn, undoBtn, clearBtn).forEachIndexed { i, x -> tools.addView(x, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dpi(if (i == 0) 0 else 4), 0, dpi(if (i == 2) 0 else 4), 0) }) }
        val seek = SeekBar(this).apply {
            max = 56; progress = 10
            progressTintList = ColorStateList.valueOf(t.ac1); thumbTintList = ColorStateList.valueOf(t.ac1)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, u: Boolean) { draw.size = 4f + v }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        val save = btn("Save & sync", true) {
            draw.saveLocal()
            if (Core.prefs(this).getString("theirs", "").isNullOrBlank()) status.text = "Saved here. Add your partner's code in Settings to sync."
            else {
                status.text = "Sending..."
                val id = Core.sendBoard(this, draw.snapshot()) { ok -> if (ok) sentText(status) else status.text = "Couldn't send: " + Core.whyFailed() }
                track(status, id)
            }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(8)) }
        col.put(dotRow, 0); col.put(tools, 10); col.put(seek, 4)
        col.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dpi(6); gravity = Gravity.CENTER_HORIZONTAL })
        col.put(status, 6); col.put(save, 6)
        return col
    }

    // ----- tab 1: custom notification -----
    private fun tpls(): JSONArray = try { JSONArray(Core.prefs(this).getString("tpl", "[]")) } catch (e: Exception) { JSONArray() }
    private fun renderTpl() {
        tplBox.removeAllViews()
        val a = tpls()
        if (a.length() == 0) tplBox.addView(tv("No templates yet. Write a notification and tap Save template.", 13f, t.sub))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val nm = o.optString("title").ifBlank { o.optString("body") }.take(26)
            val use = btn(nm, false) { tField.setText(o.optString("title")); bField.setText(o.optString("body")) }
            val del = btn("Delete", false) {
                val n = JSONArray()
                for (j in 0 until a.length()) if (j != i) n.put(a.get(j))
                Core.prefs(this).edit().putString("tpl", n.toString()).apply(); renderTpl()
            }
            tplBox.put(row(use, del, 3f, 2f), if (i == 0) 0 else 8)
        }
    }
    private fun notifyPage(): View {
        val p = Core.prefs(this)
        nStatus = tv("", 14f, t.sub).apply { gravity = Gravity.CENTER }
        tField = field("Title", "").apply { id = 1001 }
        bField = field("Message", "").apply {
            id = 1002; isSingleLine = false; minLines = 3; gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        preview = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        val add = btn("Add photo", false) { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 8) }
        val rem = btn("Remove photo", false) { img = null; preview.visibility = View.GONE }
        val clearBtn = btn("Clear", false) { tField.setText(""); bField.setText(""); nStatus.text = "" }
        val send = btn("Send", true) {
            val ti = tField.text.toString().trim(); val bo = bField.text.toString().trim()
            if (p.getString("theirs", "").isNullOrBlank()) nStatus.text = "Add your partner's code in Settings"
            else if (ti.isEmpty() && bo.isEmpty() && img == null) nStatus.text = "Write something or add a photo first"
            else {
                nStatus.text = "Sending..."
                val id = Core.sendCustom(this, ti, bo, img) { ok -> if (ok) sentText(nStatus) else nStatus.text = "Couldn't send: " + Core.whyFailed() }
                track(nStatus, id)
            }
        }
        val save = btn("Save template", false) {
            val ti = tField.text.toString().trim(); val bo = bField.text.toString().trim()
            if (ti.isEmpty() && bo.isEmpty()) nStatus.text = "Nothing to save yet"
            else {
                val a = tpls(); a.put(JSONObject().put("title", ti).put("body", bo))
                p.edit().putString("tpl", a.toString()).apply(); renderTpl(); nStatus.text = "Template saved"
            }
        }
        tplBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(16)) }
        col.put(tv("Custom notification", 28f, t.text, true), 0)
        col.put(card(sec("TITLE"), tField, sec("MESSAGE"), bField, preview, row(add, rem), nStatus, send, save, clearBtn), 16)
        preview.layoutParams = (preview.layoutParams as LinearLayout.LayoutParams).apply { height = dpi(180) }
        col.put(card(sec("TEMPLATES"), tplBox), 14)
        col.put(tv("Photos are sent through ntfy.sh and stay available for about 3 hours. Templates save text only.", 12f, t.sub), 10)
        renderTpl()
        return ScrollView(this).apply { addView(col) }
    }

    // ----- tab 2: wake up alarm -----
    private fun refreshDnd() {
        if (!::dndBtn.isInitialized) return
        val left = Core.prefs(this).getLong("dnd_until", 0L) - System.currentTimeMillis()
        if (left > 0) {
            val m = (left + 59999) / 60000
            dndBtn.text = "DND  ·  " + (if (m >= 60) "%dh %dm".format(m / 60, m % 60) else "%dm".format(m)); dndBtn.setTextColor(t.ac1)
        } else { dndBtn.text = "DND"; dndBtn.setTextColor(t.text) }
    }
    private fun themedDialog(content: View): Dialog {
        val d = Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dpi(22), dpi(22), dpi(22), dpi(22))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.bg1, t.bg2)).apply { cornerRadius = dp(28) }
            addView(content)
        }
        d.setContentView(wrapper)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        return d
    }
    private fun dndDialog() {
        val p = Core.prefs(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var d: Dialog? = null
        val mins = intArrayOf(15, 30, 60, 120); val names = arrayOf("15 min", "30 min", "1 hour", "2 hours")
        fun pick(i: Int) { p.edit().putLong("dnd_until", System.currentTimeMillis() + mins[i] * 60000L).apply(); refreshDnd(); d?.dismiss() }
        box.put(tv("Do not disturb", 22f, t.text, true).apply { gravity = Gravity.CENTER }, 0)
        box.put(row(btn(names[0], true) { pick(0) }, btn(names[1], true) { pick(1) }), 18)
        box.put(row(btn(names[2], true) { pick(2) }, btn(names[3], true) { pick(3) }), 10)
        if (Core.dndOn(this)) box.put(btn("Turn off", false) { p.edit().putLong("dnd_until", 0L).apply(); refreshDnd(); d?.dismiss() }, 10)
        val dlg = themedDialog(box); d = dlg; dlg.show()
    }
    private fun wakePage(p: SharedPreferences): View {
        val status = tv("", 14f, t.sub).apply { gravity = Gravity.CENTER }
        fun go(type: String) {
            if (p.getString("theirs", "").isNullOrBlank()) { status.text = "Add your partner's code in Settings"; return }
            status.text = "Sending..."
            val id = Core.send(this, type) { ok -> if (ok) sentText(status) else status.text = "Couldn't send: " + Core.whyFailed() }
            track(status, id)
        }
        dndBtn = mini("DND", false) { dndDialog() }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        head.addView(dndBtn, LinearLayout.LayoutParams(-2, -2))
        val ringV = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((t.ac1 and 0x00FFFFFF) or 0x33000000) } }
        val big = Button(this).apply {
            text = "WAKE UP"; textSize = 26f; isAllCaps = false; stateListAnimator = null
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = 0.08f; setTextColor(t.btnText)
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(t.ac1, t.ac2)).apply { shape = GradientDrawable.OVAL }, null)
            setOnClickListener { go("wake") }
        }
        val wrap = FrameLayout(this)
        wrap.addView(ringV, FrameLayout.LayoutParams(1, 1, Gravity.CENTER)); wrap.addView(big, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        wrap.addOnLayoutChangeListener { _, l, tp, r, bt, _, _, _, _ ->
            val m = minOf(r - l, bt - tp); val bs = (m * 0.78f).toInt()
            if (m > 0 && big.layoutParams.width != bs) {
                big.layoutParams = FrameLayout.LayoutParams(bs, bs, Gravity.CENTER); ringV.layoutParams = FrameLayout.LayoutParams(m, m, Gravity.CENTER)
            }
        }
        ObjectAnimator.ofPropertyValuesHolder(ringV, PropertyValuesHolder.ofFloat("scaleX", 0.92f, 1f), PropertyValuesHolder.ofFloat("scaleY", 0.92f, 1f)).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }
        grace = field("min", p.getInt("grace", 15).toString()).apply {
            id = 1005; inputType = InputType.TYPE_CLASS_NUMBER
            onFocusChangeListener = View.OnFocusChangeListener { _, f -> if (!f) saveGrace() }
        }
        val tb = btn("", false) {}
        fun lbl() { tb.text = "Wake-up time   %02d:%02d".format(p.getInt("sh", 8), p.getInt("sm", 0)) }
        lbl()
        tb.setOnClickListener {
            try {
                TimePickerDialog(this, { _, h, m -> p.edit().putInt("sh", h).putInt("sm", m).apply(); lbl(); Core.scheduleNext(this) },
                    p.getInt("sh", 8), p.getInt("sm", 0), true).show()
            } catch (e: Exception) {}
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(24), dpi(36), dpi(24), dpi(8)) }
        col.addView(head, LinearLayout.LayoutParams(-1, -2))
        col.addView(wrap, LinearLayout.LayoutParams(-1, 0, 1f))
        col.put(status, 4)
        col.put(btn("Are you awake?", false) { go("polite") }, 10)
        col.put(btn("Nyaaa", false) { go("meow") }, 8)
        col.put(card(
            sw("Alarm never stops", p.getBoolean("forever", true)) { v -> p.edit().putBoolean("forever", v).apply() },
            sw("Scheduled wake-up", p.getBoolean("sched_on", false)) { v -> p.edit().putBoolean("sched_on", v).apply(); Core.scheduleNext(this) },
            row(tb, grace, 3f, 1f),
            tv("Wake-up time, and minutes before notifying my partner", 12f, t.sub)), 12)
        return col
    }

    // ----- tab 3: history -----
    private fun histPage(): View {
        histBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val clear = btn("Clear history", false) { Core.prefs(this).edit().putString("hist", "[]").apply(); renderHist() }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(8)) }
        col.put(tv("History", 30f, t.text, true), 0)
        col.addView(ScrollView(this).apply { addView(histBox) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dpi(14) })
        col.put(clear, 10)
        renderHist()
        return col
    }
    private fun renderHist() {
        histBox.removeAllViews()
        val a = try { JSONArray(Core.prefs(this).getString("hist", "[]")) } catch (e: Exception) { JSONArray() }
        if (a.length() == 0) { histBox.addView(tv("Nothing yet. Wake-ups, messages and drawings will show up here.", 14f, t.sub)); return }
        val f = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i); val k = o.optInt("k"); val st = o.optInt("s")
            val who = when (k) {
                0 -> when (st) { 1 -> "DELIVERED"; 3 -> "DELIVERED · THEIR DND ON"; 2 -> "FAILED"; else -> "SENT" }
                1 -> if (st == 4) "RECEIVED · MUTED BY DND" else "RECEIVED"
                else -> "YOU"
            }
            val col = when (k) { 0 -> if (st == 2) Color.parseColor("#E53935") else t.ac1; 1 -> t.ac2; else -> t.sub }
            histBox.put(card(tv(who + "  ·  " + f.format(java.util.Date(o.optLong("t"))), 12f, col, true), tv(o.optString("x"), 15f)), if (i == 0) 0 else 8)
        }
    }

    // ----- tab 4: settings -----
    private fun stopText(p: SharedPreferences): String { val n = p.getString("stop_name", ""); return if (n.isNullOrEmpty()) "No app chosen" else "App: $n" }
    private fun updateNf() {
        if (!::mlLbl.isInitialized) return
        val p = Core.prefs(this)
        val f = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
        val txt = if (!BuildConfig.MACRO) {
            val last = p.getLong("ml_last", 0L)
            "Logged: " + p.getInt("ml_count", 0) + "\nLast received: " + (if (last == 0L) "never" else f.format(java.util.Date(last)))
        } else {
            val en = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
            val last = p.getLong("nf_last", 0L); val err = p.getString("nf_err", "") ?: ""
            (if (!en) "Notification access is off\n" else "") + "Queued: " + MacroQueue.size(this) + "\nLast sent: " +
                (if (last == 0L) "never" else f.format(java.util.Date(last))) + (if (err.isNotEmpty()) "\nError: $err" else "")
        }
        mlLbl.text = txt + (if (mlNote.isNotEmpty()) "\n" + mlNote else "")
    }
    private fun checkNow() {
        mlNote = "Checking..."; updateNf()
        Thread {
            val r = if (BuildConfig.MACRO) {
                val ok = try { NotifForward.flushNow(applicationContext) } catch (e: Exception) { false }
                if (ok) "Done" else "Couldn't send"
            } else (ListenService.poller?.invoke(true) ?: "The listener isn't running. Reopen the app.")
            ui.post { mlNote = r; updateNf() }
        }.start()
    }
    private fun autoRestore() {
        if (Backup.available() && Backup.hasFile() && !Core.prefs(this).getBoolean("bk_ok", false)) {
            if (Backup.restore(this)) { permDlg?.dismiss(); keepPage = 4; recreate() }
        }
    }
    private fun pickApp() {
        val pm = packageManager
        val ri = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }.sortedBy { it.loadLabel(pm).toString().lowercase() }
        val names = ri.map { it.loadLabel(pm).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose app").setItems(names) { _, i ->
            val pr = Core.prefs(this)
            pr.edit().putString("stop_pkg", ri[i].activityInfo.packageName).putString("stop_name", names[i]).apply()
            stopLbl.text = stopText(pr)
        }.show()
    }
    private fun oemIntent(): Intent? {
        val m = Build.MANUFACTURER.lowercase()
        val c: Pair<String, String>? = when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> Pair("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> Pair("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            m.contains("vivo") -> Pair("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            m.contains("huawei") || m.contains("honor") -> Pair("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            m.contains("samsung") -> Pair("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
            else -> null
        }
        return c?.let { Intent().setComponent(ComponentName(it.first, it.second)) }
    }
    private fun showPerms() {
        permDlg?.dismiss()
        val upd = ArrayList<() -> Unit>()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.put(tv("Permissions", 22f, t.text, true).apply { gravity = Gravity.CENTER }, 0)
        fun add(label: String, btnLabel: String, granted: (() -> Boolean)?, action: () -> Unit) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setOnClickListener { action() } }
            val b = mini(btnLabel, true) { action() }
            val st = tv("Allowed", 15f, t.ac1, true).apply { visibility = View.GONE }
            line.addView(tv(label, 15f, t.text), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(b, LinearLayout.LayoutParams(-2, -2)); line.addView(st, LinearLayout.LayoutParams(-2, -2))
            box.put(line, 16)
            if (granted != null) upd.add { val g = granted(); b.visibility = if (g) View.GONE else View.VISIBLE; st.visibility = if (g) View.VISIBLE else View.GONE }
        }
        val pm = getSystemService(PowerManager::class.java)
        add("Background running", "Allow", { try { pm.isIgnoringBatteryOptimizations(packageName) } catch (e: Exception) { false } }) {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (e: Exception) {}
        }
        add("File access", "Allow", { Backup.available() }) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) } catch (e: Exception) {}
        }
        add("Background data", "Allow", { try { getSystemService(ConnectivityManager::class.java).restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED } catch (e: Exception) { true } }) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:$packageName"))) } catch (e: Exception) {}
        }
        val oem = oemIntent()
        if (oem != null) add(if (Build.MANUFACTURER.lowercase().contains("samsung")) "Never sleeping apps (Samsung)" else "Autostart (" + Build.MANUFACTURER + ")", "Open", null) {
            try { startActivity(oem) } catch (e: Exception) {
                try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } catch (e2: Exception) {}
            }
        }
        if (BuildConfig.MACRO) add("Notification access", "Allow", { Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true }) {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) {}
        }
        if (BuildConfig.MACRO) add("Main phone's code", "Enter", { !Core.prefs(this).getString("theirs", "").isNullOrBlank() }) { permDlg?.dismiss(); pager.goTo(4) }
        add("Override Do Not Disturb", "Allow", { try { getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted } catch (e: Exception) { false } }) {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (e: Exception) {}
        }
        add("Restore history", "Restore", null) {
            val ok = Backup.available()
            AlertDialog.Builder(this).setTitle("Restore history?")
                .setMessage(if (ok) "This replaces your current history, codes and settings with the backup." else "Allow file access first.")
                .setPositiveButton(if (ok) "Restore" else "OK") { _, _ -> if (ok && Backup.restore(this)) { permDlg?.dismiss(); keepPage = 4; recreate() } }
                .setNegativeButton("Cancel", null).show()
        }
        box.put(btn("Done", false) { permDlg?.dismiss() }, 20)
        val d = themedDialog(box); permDlg = d
        permRows = { upd.forEach { it() } }; permRows?.invoke()
        d.setOnDismissListener { if (permDlg === d) { permDlg = null; permRows = null } }
        d.show()
    }
    private fun settingsPage(p: SharedPreferences): View {
        val sScroll = ScrollView(this); val tScroll = HScroll(this)
        mine = field("Your code", p.getString("mine", "")!!).apply { id = 1003 }
        theirs = field("Paste your partner's code", p.getString("theirs", "")!!).apply { id = 1004 }
        for (e in listOf(mine, theirs)) e.onFocusChangeListener = View.OnFocusChangeListener { _, f -> if (!f) saveCodes() }
        val copy = btn("Copy", false) {}
        copy.setOnClickListener { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", mine.text.toString())); flash(copy, "Copied") }
        val share = btn("Share", true) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "My WakeUp code: " + mine.text.toString()), null))
        }
        musicLbl = tv(if (p.getString("tone", "").isNullOrEmpty()) "Default alarm sound" else "Custom music selected", 14f, t.sub)
        val pick = btn("Choose music", true) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 7)
        }
        val reset = btn("Use default", false) { p.edit().remove("tone").apply(); musicLbl.text = "Default alarm sound" }
        stopLbl = tv(stopText(p), 13f, t.sub)
        val cur = p.getInt("theme", 0).coerceIn(0, THEMES.size - 1)
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        THEMES.forEachIndexed { i, th ->
            val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(th.bg1, th.ac1, th.ac2)).apply {
                shape = GradientDrawable.OVAL; if (i == cur) setStroke(dpi(3), t.text)
            }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dpi(6), dpi(4), dpi(6), dpi(4))
                addView(View(this@MainActivity).apply { background = d }, LinearLayout.LayoutParams(dpi(56), dpi(56)))
                addView(tv(th.name, 12f, t.sub).apply { gravity = Gravity.CENTER })
                setOnClickListener {
                    keepPage = 4; keepY = sScroll.scrollY; keepX = tScroll.scrollX
                    p.edit().putInt("theme", i).apply(); this@MainActivity.recreate(); overridePendingTransition(0, 0)
                }
            }
            themeRow.addView(item)
        }
        tScroll.isHorizontalScrollBarEnabled = false; tScroll.addView(themeRow)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(40), dpi(20), dpi(16)) }
        col.put(tv("Settings", 30f, t.text, true), 0)
        col.put(card(sec("YOUR CODE"), mine, row(copy, share), tv("Send this to your partner so they can paste it into their app.", 13f, t.sub)), 16)
        col.put(card(sec("PARTNER'S CODE"), theirs, tv("Paste the code your partner sent you.", 13f, t.sub)), 14)
        col.put(card(sec("ALARM MUSIC"), musicLbl, row(pick, reset)), 14)
        col.put(card(sec("STOP ALARM WITH AN APP"),
            sw("Alarm only stops when I open an app", p.getBoolean("stop_app", false)) { v -> p.edit().putBoolean("stop_app", v).apply() },
            stopLbl,
            row(btn("Choose app", true) { pickApp() }, btn("Usage access", false) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }),
            tv("Usage access lets the app notice you opened it. Without it, the alarm stops when you tap the Open button.", 12f, t.sub)), 14)
        col.put(card(sec("THEME"), tScroll), 14)
        if (!BuildConfig.MACRO) {
            mlLbl = tv("", 15f, t.text); updateNf()
            col.put(card(mlLbl, sw("Also show as notifications", p.getBoolean("ml_notify", false)) { v -> p.edit().putBoolean("ml_notify", v).apply() },
                row(btn("Check now", false) { checkNow() }, btn("Open log", true) { startActivity(Intent(this, MacroLogActivity::class.java)) })), 14)
        }
        col.put(btn("Permissions", false) { showPerms() }, 14)
        sScroll.addView(col)
        val y = keepY; val x = keepX; keepY = 0; keepX = 0
        if (y > 0 || x > 0) sScroll.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (sScroll.height == 0) return
                sScroll.viewTreeObserver.removeOnGlobalLayoutListener(this); sScroll.scrollTo(0, y); tScroll.scrollTo(x, 0)
            }
        })
        return sScroll
    }

    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        val u = d?.data ?: return
        if (c != RESULT_OK) return
        if (r == 7) {
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            Core.prefs(this).edit().putString("tone", u.toString()).apply(); musicLbl.text = "Custom music selected"
        } else if (r == 8) { img = u; preview.setImageURI(u); preview.visibility = View.VISIBLE }
    }
}
