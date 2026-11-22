package com.vidmax.app

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var browser: WebView
    private lateinit var address: EditText
    private lateinit var pageHost: LinearLayout
    private var currentPage = "home"
    private val navy by lazy { getColor(R.color.navy) }
    private val surface by lazy { getColor(R.color.surface) }
    private val accent by lazy { getColor(R.color.accent) }
    private val white by lazy { getColor(R.color.white) }
    private val muted by lazy { getColor(R.color.muted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        buildShell()
        configureBrowser()
        showHome()
        acceptIncomingLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIncomingLink(intent)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (currentPage == "browser" && browser.canGoBack()) browser.goBack()
        else if (currentPage != "home") showHome()
        else super.onBackPressed()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("VIDMAX", 23f, accent, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        val home = smallButton("الرئيسية")
        home.setOnClickListener { showHome() }
        header.addView(home)
        root.addView(header)

        val addressRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(8)) }
        address = EditText(this).apply {
            hint = "ابحث أو الصق رابط فيديو"
            textSize = 14f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(white); setHintTextColor(muted)
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(surface)
            textDirection = View.TEXT_DIRECTION_LTR
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_SEARCH) {
                    navigate(address.text.toString()); true
                } else false
            }
        }
        addressRow.addView(address, LinearLayout.LayoutParams(0, dp(46), 1f))
        val go = smallButton("اذهب")
        go.setOnClickListener { navigate(address.text.toString()) }
        addressRow.addView(go, LinearLayout.LayoutParams(dp(68), dp(46)).apply { marginStart = dp(6) })
        root.addView(addressRow)

        pageHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(navy) }
        root.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }
        listOf("الرئيسية" to "home", "المتصفح" to "browser", "التنزيلات" to "downloads", "الإعدادات" to "settings").forEach { item ->
            val b = smallButton(item.first)
            b.setOnClickListener {
                when (item.second) {
                    "home" -> showHome()
                    "browser" -> showBrowser()
                    "downloads" -> showDownloads()
                    else -> showSettings()
                }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun configureBrowser() {
        browser = WebView(this)
        browser.setBackgroundColor(Color.WHITE)
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.mediaPlaybackRequiresUserGesture = true
        browser.settings.loadsImagesAutomatically = true
        browser.settings.javaScriptCanOpenWindowsAutomatically = false
        browser.webChromeClient = WebChromeClient()
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return !(url.startsWith("https://") || url.startsWith("http://"))
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (!url.isNullOrBlank()) address.setText(url)
            }
        }
        browser.setDownloadListener(DownloadListener { url, userAgent, disposition, mime, _ ->
            enqueueWebDownload(url, userAgent, disposition, mime)
        })
    }

    private fun showHome() {
        currentPage = "home"
        address.setText("")
        pageHost.removeAllViews()
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(12), dp(8), dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        content.addView(text("كل فيديوهاتك في مكان واحد", 21f, white, true), match())
        content.addView(text("افتح موقعك المفضل وشاهد الفيديو داخل VidMax. استخدم زر التنزيل عندما يوفّر الموقع ملفًا قابلًا للتنزيل.", 14f, muted, false).apply {
            setPadding(0, dp(8), 0, dp(18))
        }, match())
        listOf(
            "YouTube" to "https://m.youtube.com/",
            "TikTok" to "https://www.tiktok.com/",
            "Facebook" to "https://m.facebook.com/",
            "Instagram" to "https://www.instagram.com/",
            "X / تويتر" to "https://x.com/"
        ).forEach { site ->
            val b = makeButton("فتح " + site.first)
            b.setOnClickListener { address.setText(site.second); showBrowser(); browser.loadUrl(site.second) }
            content.addView(b, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(7) })
        }
        val paste = makeButton("لصق رابط من الحافظة وفتحه")
        paste.setOnClickListener {
            val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val value = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
            if (value.isBlank()) toast("الحافظة فارغة") else { address.setText(value); navigate(value) }
        }
        content.addView(paste, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(16) })
        scroll.addView(content)
        pageHost.addView(scroll, match())
    }

    private fun showBrowser() {
        currentPage = "browser"
        pageHost.removeAllViews()
        if (browser.parent != null) (browser.parent as android.view.ViewGroup).removeView(browser)
        pageHost.addView(browser, match())
        if (browser.url.isNullOrBlank()) browser.loadUrl("https://m.youtube.com/")
    }

    private fun showDownloads() {
        currentPage = "downloads"
        pageHost.removeAllViews()
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(12), dp(8), dp(12))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        list.addView(text("مدير التنزيلات", 21f, white, true), match())
        val refresh = makeButton("تحديث القائمة")
        refresh.setOnClickListener { showDownloads() }
        list.addView(refresh, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query())
        var count = 0
        cursor.use {
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            while (it.moveToNext()) {
                count++
                val title = if (titleCol >= 0) it.getString(titleCol) ?: "ملف بدون اسم" else "ملف"
                val status = if (statusCol >= 0) it.getInt(statusCol) else -1
                val statusText = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "اكتمل"
                    DownloadManager.STATUS_FAILED -> "فشل"
                    DownloadManager.STATUS_RUNNING -> "جارٍ التنزيل"
                    DownloadManager.STATUS_PAUSED -> "متوقف مؤقتًا"
                    DownloadManager.STATUS_PENDING -> "في الانتظار"
                    else -> "حالة غير معروفة"
                }
                list.addView(text("$title\n$statusText", 15f, white, false).apply {
                    setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(surface)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        }
        if (count == 0) list.addView(text("لا توجد تنزيلات حتى الآن", 15f, muted, false).apply {
            setPadding(0, dp(20), 0, 0)
        }, match())
        scroll.addView(list)
        pageHost.addView(scroll, match())
    }

    private fun showSettings() {
        currentPage = "settings"
        pageHost.removeAllViews()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(14), dp(10), dp(10))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        content.addView(text("الإعدادات", 21f, white, true), match())
        content.addView(text("لغة التطبيق: العربية\nمكان الحفظ: Downloads/VidMax\nإشعارات التنزيل: من خلال نظام Android.", 15f, muted, false).apply {
            setPadding(0, dp(14), 0, dp(14))
        }, match())
        content.addView(text("تنزيل الفيديو يعتمد على إتاحة الموقع لرابط ملف قابل للتنزيل. بعض المنصات تمنع التنزيل أو تشترط استخدام أدواتها الرسمية؛ لا يتجاوز VidMax هذه القيود.", 14f, muted, false), match())
        pageHost.addView(content, match())
    }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) { toast("اكتب كلمة بحث أو رابطًا"); return }
        val target = if (value.startsWith("https://") || value.startsWith("http://")) value
            else if (value.contains(".") && !value.contains(" ")) "https://$value"
            else "https://www.google.com/search?q=" + Uri.encode(value)
        val uri = runCatching { Uri.parse(target) }.getOrNull()
        if (uri?.host.isNullOrBlank() || (uri?.scheme != "https" && uri?.scheme != "http")) {
            toast("الرابط غير صحيح"); return
        }
        showBrowser()
        browser.loadUrl(target)
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken, 0)
    }

    private fun enqueueWebDownload(url: String, userAgent: String, disposition: String, mime: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri?.host.isNullOrBlank() || (uri.scheme != "https" && uri.scheme != "http")) {
            toast("نوع الرابط غير مدعوم للتنزيل"); return
        }
        val fileName = android.webkit.URLUtil.guessFileName(url, disposition, mime)
            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        try {
            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setDescription("تنزيل بواسطة VidMax")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "VidMax/$fileName")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            if (userAgent.isNotBlank()) request.addRequestHeader("User-Agent", userAgent)
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) request.addRequestHeader("Cookie", cookie)
            request.addRequestHeader("Referer", browser.url ?: url)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast("أُضيف الملف إلى التنزيلات")
        } catch (_: Exception) { toast("تعذر بدء التنزيل من هذا الموقع") }
    }

    private fun acceptIncomingLink(intent: Intent?) {
        val shared = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (!shared.isNullOrBlank()) { address.setText(shared.trim()); navigate(shared) }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = Gravity.RIGHT; textDirection = View.TEXT_DIRECTION_RTL
    }
    private fun makeButton(value: String) = Button(this).apply {
        text = value; textSize = 14f; isAllCaps = false
        setTextColor(navy); setBackgroundColor(accent)
    }
    private fun smallButton(value: String) = Button(this).apply {
        text = value; textSize = 11f; isAllCaps = false
        setTextColor(white); setBackgroundColor(surface)
        minHeight = dp(40); minimumHeight = dp(40)
    }
    private fun match() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}