package com.are.did

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Store {
    private const val PREFS = "donk_prefs"
    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) { prefs(c).edit().putBoolean("enabled", v).apply() }
    fun interval(c: Context) = prefs(c).getInt("interval", 3)
    fun setInterval(c: Context, v: Int) { prefs(c).edit().putInt("interval", v).apply() }
    fun count(c: Context) = prefs(c).getLong("count", 0L)
    fun setCount(c: Context, v: Long) { prefs(c).edit().putLong("count", v).apply() }
    fun addCount(c: Context) = setCount(c, count(c) + 1)
    fun resetCount(c: Context) = setCount(c, 0L)
}

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var countView: TextView
    private lateinit var intervalLabel: TextView
    private lateinit var toggleBtn: Button
    private var receiver: BroadcastReceiver? = null

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, radius: Float, strokeColor: Int = 0, strokeW: Float = 0f): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(radius).toFloat()
        if (strokeColor != 0) d.setStroke(dp(strokeW), strokeColor)
        return d
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF0E0E14.toInt()
        window.navigationBarColor = 0xFF0E0E14.toInt()
        buildUI()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0E0E14.toInt())
            setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
        }

        val title = TextView(this).apply {
            text = "Donk"
            setTextColor(0xFFD4AF37.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "مضاعف مشاهدات TikTok"
            setTextColor(0xFF8B8B99.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2f), 0, dp(20f))
        }
        root.addView(title)
        root.addView(subtitle)

        statusView = TextView(this).apply {
            setTextColor(0xFFF0EAD6.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(0xFF1A1A24.toInt(), 14f, 0xFF2A2A36.toInt(), 1f)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        statusCard.addView(statusView, LinearLayout.LayoutParams(MATCH, WRAP))
        val openA11yBtn = Button(this).apply {
            text = "فتح إعدادات الوصول"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        statusCard.addView(openA11yBtn, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(10f) })
        root.addView(statusCard, LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(14f) })

        val toggleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(0xFF1A1A24.toInt(), 14f, 0xFF2A2A36.toInt(), 1f)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        toggleBtn = Button(this).apply {
            text = ""
            setOnClickListener { toggleAutomation() }
        }
        toggleCard.addView(toggleBtn, LinearLayout.LayoutParams(MATCH, dp(52f)))
        root.addView(toggleCard, LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(14f) })

        val intervalCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(0xFF1A1A24.toInt(), 14f, 0xFF2A2A36.toInt(), 1f)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        intervalLabel = TextView(this).apply {
            setTextColor(0xFFF0EAD6.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            text = "الفاصل بين التمريرات: ${Store.interval(this@MainActivity)} ثانية"
        }
        val sb = SeekBar(this).apply {
            max = 29
            progress = Store.interval(this@MainActivity) - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + 1
                    Store.setInterval(this@MainActivity, v)
                    intervalLabel.text = "الفاصل بين التمريرات: $v ثانية"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        intervalCard.addView(intervalLabel)
        intervalCard.addView(sb, LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(6f) })
        root.addView(intervalCard, LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(14f) })

        val countCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(0xFF1A1A24.toInt(), 14f, 0xFF2A2A36.toInt(), 1f)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        val countTitle = TextView(this).apply {
            text = "عدد التمريرات"
            setTextColor(0xFF8B8B99.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        countView = TextView(this).apply {
            setTextColor(0xFFD4AF37.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("serif", Typeface.BOLD)
            text = Store.count(this@MainActivity).toString()
        }
        val resetBtn = Button(this).apply {
            text = "تصفير العداد"
            setOnClickListener {
                Store.resetCount(this@MainActivity)
                countView.text = "0"
            }
        }
        countCard.addView(countTitle)
        countCard.addView(countView)
        countCard.addView(resetBtn, LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = dp(6f) })
        root.addView(countCard, LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(14f) })

        val launchBtn = Button(this).apply {
            text = "افتح TikTok"
            setTextColor(0xFF0E0E14.toInt())
            background = bg(0xFFD4AF37.toInt(), 12f)
            setOnClickListener { launchTikTok() }
        }
        root.addView(launchBtn, LinearLayout.LayoutParams(MATCH, dp(52f)))

        val footer = TextView(this).apply {
            text = "بعد التفعيل: افتح TikTok ودع التطبيق يقلّب الفيديوهات تلقائياً."
            setTextColor(0xFF8B8B99.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(14f), 0, 0)
        }
        root.addView(footer)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        registerUpdateReceiver()
        refreshUI()
    }

    private fun toggleAutomation() {
        val now = !Store.enabled(this)
        Store.setEnabled(this, now)
        if (now && !isServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("تفعيل خدمة الوصول")
                .setMessage("لتشغيل التقليب التلقائي، فعّل خدمة Donk في إعدادات الوصول.")
                .setPositiveButton("فتح الإعدادات") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
        refreshUI()
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (info in list) {
            val si = info.resolveInfo?.serviceInfo ?: continue
            if (si.packageName == packageName && si.name == AutoSwipeService::class.java.name) return true
        }
        return false
    }

    private fun refreshUI() {
        val on = Store.enabled(this)
        val svc = isServiceEnabled()
        statusView.text = "الخدمة: " + (if (svc) "مفعّلة ✓" else "غير مفعّلة ✗")
        statusView.setTextColor(if (svc) 0xFF66CC66.toInt() else 0xFFE05555.toInt())
        toggleBtn.text = if (on) "إيقاف التقليب التلقائي" else "تشغيل التقليب التلقائي"
        toggleBtn.background = bg(if (on) 0xFF8B2A2A.toInt() else 0xFF2A6B2A.toInt(), 12f)
        toggleBtn.setTextColor(0xFFF0EAD6.toInt())
        countView.text = Store.count(this).toString()
    }

    private fun launchTikTok() {
        val candidates = arrayOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go"
        )
        for (p in candidates) {
            val i = packageManager.getLaunchIntentForPackage(p)
            if (i != null) { startActivity(i); return }
        }
        Toast.makeText(this, "TikTok غير مثبّت", Toast.LENGTH_SHORT).show()
    }

    private fun registerUpdateReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                countView.text = Store.count(this@MainActivity).toString()
            }
        }
        receiver = r
        val f = IntentFilter("com.are.did.SWIPE_UPDATE")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(r, f)
    }

    override fun onResume() { super.onResume(); refreshUI() }

    override fun onDestroy() {
        receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        super.onDestroy()
    }
}

class AutoSwipeService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
            setCapabilities(AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES)
            notificationTimeout = 50
        }
        running = true
        scheduleNext()
    }

    private fun scheduleNext() {
        if (!running) return
        if (!Store.enabled(this)) { running = false; return }
        val delayMs = Store.interval(this) * 1000L
        handler.postDelayed({
            if (!Store.enabled(this)) { running = false; return@postDelayed }
            performSwipe()
            Store.addCount(this)
            sendBroadcast(Intent("com.are.did.SWIPE_UPDATE").setPackage(packageName))
            scheduleNext()
        }, delayMs)
    }

    private fun performSwipe() {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val startY = dm.heightPixels * 0.82f
        val endY = dm.heightPixels * 0.18f
        val path = Path().apply {
            moveTo(cx, startY)
            lineTo(cx, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 280L))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { running = false }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}