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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONTokener

class MainActivity : Activity() {
    private lateinit var browser: WebView
    private lateinit var address: EditText
    private lateinit var pageHost: LinearLayout
    private lateinit var downloadButton: Button
    private var currentPage = "home"
    private val prefs by lazy { getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE) }
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
        if (currentPage in listOf("home", "shorts", "reels") && browser.canGoBack()) {
            browser.goBack()
        } else if (currentPage != "home") {
            showHome()
        } else {
            super.onBackPressed()
        }
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(10), dp(8), dp(10), dp(6))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("VIDMAX", 23f, accent, true), LinearLayout.LayoutParams(0, dp(46), 1f))
        header.addView(text("فيديوهاتك في مكان واحد", 12f, muted, false))
        root.addView(header)

        val searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(8))
        }
        address = EditText(this).apply {
            hint = "ابحث عن فيديو أو الصق رابطًا"
            textSize = 14f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(surface)
            textDirection = View.TEXT_DIRECTION_RTL
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_GO) {
                    searchVideos(address.text.toString())
                    true
                } else false
            }
        }
        searchRow.addView(address, LinearLayout.LayoutParams(0, dp(46), 1f))
        val search = smallButton("بحث")
        search.setOnClickListener { searchVideos(address.text.toString()) }
        searchRow.addView(search, LinearLayout.LayoutParams(dp(66), dp(46)).apply { marginStart = dp(6) })
        root.addView(searchRow)

        pageHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
        }
        root.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }
        val items = listOf(
            "الرائج" to { showHome() },
            "شورتس" to { showShorts() },
            "ريلز" to { showReels() },
            "التنزيلات" to { showDownloads() },
            "الإعدادات" to { showSettings() }
        )
        items.forEach { item ->
            val b = smallButton(item.first)
            b.textSize = 10.5f
            b.setOnClickListener { item.second() }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun configureBrowser() {
        browser = WebView(this)
        browser.setBackgroundColor(Color.BLACK)
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.mediaPlaybackRequiresUserGesture = !prefs.getBoolean("autoplay", false)
        browser.settings.loadsImagesAutomatically = true
        browser.settings.javaScriptCanOpenWindowsAutomatically = false
        browser.webChromeClient = WebChromeClient()
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme ?: return false
                // Keep normal web links inside VidMax; never launch the external browser.
                return scheme != "https" && scheme != "http"
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (!url.isNullOrBlank()) address.setText(url)
            }
        }
        browser.setDownloadListener(DownloadListener { url, userAgent, disposition, mime, _ ->
            enqueueDirectDownload(url, userAgent, disposition, mime)
        })
    }

    private fun showHome() = showFeed(
        "home",
        "https://m.youtube.com/feed/trending",
        "الفيديوهات الرائجة"
    )

    private fun showShorts() = showFeed(
        "shorts",
        "https://m.youtube.com/shorts/",
        "شورتس"
    )

    private fun showReels() = showFeed(
        "reels",
        "https://www.instagram.com/reels/",
        "ريلز"
    )

    private fun showFeed(page: String, url: String, title: String) {
        currentPage = page
        pageHost.removeAllViews()
        val heading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(6))
        }
        heading.addView(text(title, 18f, white, true), LinearLayout.LayoutParams(0, dp(38), 1f))
        downloadButton = makeButton("تنزيل الفيديو الحالي")
        downloadButton.textSize = 12f
        downloadButton.setOnClickListener { downloadCurrentVideo() }
        if (!prefs.getBoolean("show_download", true)) downloadButton.visibility = View.GONE
        heading.addView(downloadButton, LinearLayout.LayoutParams(-2, dp(40)))
        pageHost.addView(heading)
        if (page == "reels") {
            pageHost.addView(text(
                "محتوى ريلز يُعرض داخل التطبيق؛ قد يطلب Instagram تسجيل الدخول حسب سياساته.",
                11f, muted, false
            ).apply { setPadding(dp(4), 0, dp(4), dp(5)) })
        }
        if (browser.parent != null) (browser.parent as android.view.ViewGroup).removeView(browser)
        pageHost.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        if (browser.url != url) browser.loadUrl(url)
    }

    private fun searchVideos(raw: String) {
        val query = raw.trim()
        if (query.isBlank()) {
            toast("اكتب اسم الفيديو أو الصق الرابط")
            return
        }
        if (query.startsWith("https://") || query.startsWith("http://")) {
            currentPage = "home"
            showFeed("home", query, "مشاهدة الفيديو")
        } else {
            val target = "https://m.youtube.com/results?search_query=" + Uri.encode(query)
            showFeed("home", target, "نتائج البحث")
        }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
    }

    private fun downloadCurrentVideo() {
        val script = "(function(){var v=document.querySelector('video');return v?(v.currentSrc||v.src||''):'';})()"
        browser.evaluateJavascript(script) { encoded ->
            val mediaUrl = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
            if (mediaUrl.isNullOrBlank()) {
                toast("لم أجد ملف فيديو مباشرًا في الصفحة")
            } else {
                enqueueDirectDownload(mediaUrl, browser.settings.userAgentString, "", "video/mp4")
            }
        }
    }

    private fun enqueueDirectDownload(url: String, userAgent: String, disposition: String, mime: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri?.host.isNullOrBlank() || (uri?.scheme != "https" && uri?.scheme != "http")) {
            toast("المصدر لا يوفّر رابط ملف مباشرًا للتنزيل")
            return
        }
        val path = uri?.lastPathSegment?.lowercase().orEmpty()
        if (url.startsWith("blob:") || path.endsWith(".m3u8") || path.endsWith(".mpd")) {
            toast("هذا بث محمي أو مُجزّأ ولا يمكن تنزيله كملف مباشر")
            return
        }
        val fileName = android.webkit.URLUtil.guessFileName(url, disposition, mime)
            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        try {
            val request = DownloadManager.Request(uri!!)
                .setTitle(fileName)
                .setDescription("تنزيل بواسطة VidMax")
                .setNotificationVisibility(
                    if (prefs.getBoolean("download_notification", true))
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    else DownloadManager.Request.VISIBILITY_HIDDEN
                )
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "VidMax/$fileName")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            if (userAgent.isNotBlank()) request.addRequestHeader("User-Agent", userAgent)
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) request.addRequestHeader("Cookie", cookie)
            request.addRequestHeader("Referer", browser.url ?: url)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast("بدأ تنزيل الملف المباشر")
        } catch (_: Exception) {
            toast("تعذر بدء التنزيل من هذا المصدر")
        }
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
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    setBackgroundColor(surface)
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
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        content.addView(text("إعدادات التطبيق", 21f, white, true), match())
        content.addView(text("غيّر الاختيارات وسيتم حفظها تلقائيًا.", 13f, muted, false).apply {
            setPadding(0, dp(5), 0, dp(12))
        }, match())
        addSettingSwitch(content, "تشغيل الفيديو تلقائيًا", "autoplay", false) { enabled ->
            browser.settings.mediaPlaybackRequiresUserGesture = !enabled
        }
        addSettingSwitch(content, "إظهار زر تنزيل الفيديو", "show_download", true) { enabled ->
            if (::downloadButton.isInitialized) {
                downloadButton.visibility = if (enabled) View.VISIBLE else View.GONE
            }
        }
        addSettingSwitch(content, "إشعار عند اكتمال التنزيل", "download_notification", true) { }
        content.addView(text(
            "مكان الحفظ: مجلد Downloads/VidMax على جهازك.\n" +
            "ملاحظة: زر التنزيل يحفظ الملفات المباشرة التي يسمح الموقع بالوصول إليها. لا يستطيع تنزيل بث YouTube المحمي أو المقسّم إلى أجزاء.",
            13f, muted, false
        ).apply { setPadding(0, dp(16), 0, 0) }, match())
        scroll.addView(content)
        pageHost.addView(scroll, match())
    }

    private fun addSettingSwitch(
        parent: LinearLayout,
        label: String,
        key: String,
        defaultValue: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        val control = Switch(this).apply {
            text = label
            textSize = 15f
            setTextColor(white)
            isChecked = prefs.getBoolean(key, defaultValue)
            setPadding(0, dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                onChanged(checked)
            }
        }
        parent.addView(control, LinearLayout.LayoutParams(-1, dp(58)))
        val divider = View(this).apply { setBackgroundColor(surface) }
        parent.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun acceptIncomingLink(intent: Intent?) {
        val shared = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (!shared.isNullOrBlank()) {
            address.setText(shared.trim())
            searchVideos(shared.trim())
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        textDirection = View.TEXT_DIRECTION_RTL
    }

    private fun makeButton(value: String) = Button(this).apply {
        text = value
        textSize = 14f
        isAllCaps = false
        setTextColor(navy)
        setBackgroundColor(accent)
    }

    private fun smallButton(value: String) = Button(this).apply {
        text = value
        textSize = 11f
        isAllCaps = false
        setTextColor(white)
        setBackgroundColor(surface)
        minHeight = dp(40)
        minimumHeight = dp(40)
    }

    private fun match() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}
