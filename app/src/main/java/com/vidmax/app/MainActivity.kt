package com.vidmax.app

import android.app.Activity
import android.accounts.AccountManager
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newFixedThreadPool(4)
    private val prefs by lazy { getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE) }
    private val navy = Color.rgb(12, 18, 30)
    private val surface = Color.rgb(25, 35, 52)
    private val accent = Color.rgb(38, 210, 169)
    private val white = Color.WHITE
    private val muted = Color.rgb(166, 181, 200)
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private var currentMode = "trending"
    private val googleAccountRequest = 4207

    // A native PeerTube feed: no YouTube/Instagram WebView and no browser chrome.
    private val apiBases = listOf("https://peertube.cpy.re", "https://peertube2.cpy.re")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        buildShell()
        showFeed("trending")
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == googleAccountRequest) {
            if (resultCode == RESULT_OK) {
                val accountName = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME).orEmpty()
                if (accountName.isNotBlank()) {
                    prefs.edit().putString("google_account_name", accountName).apply()
                    toast("تم اختيار حساب Google داخل VidMax")
                } else toast("لم يتم استلام بيانات الحساب")
            }
            showSettings()
        }
    }

    private fun chooseGoogleAccount() {
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"),
                "اختَر حساب Google الذي تريد ربطه بواجهة VidMax", null, null, null
            )
            startActivityForResult(intent, googleAccountRequest)
        } catch (_: Exception) {
            toast("تعذر فتح اختيار حساب Google على هذا الجهاز")
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(8), dp(12), dp(5))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("VidMax", 25f, accent, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(label("شاهد مباشرة", 12f, muted, false))
        root.addView(header)

        val searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        search = EditText(this).apply {
            hint = "ابحث عن فيديو"
            textSize = 14f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(surface, 14)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setOnEditorActionListener { _, _, _ ->
                searchVideos(search.text.toString())
                true
            }
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(46), 1f))
        val searchButton = actionButton("بحث")
        searchButton.setOnClickListener { searchVideos(search.text.toString()) }
        searchRow.addView(searchButton, LinearLayout.LayoutParams(dp(62), dp(46)).apply { marginStart = dp(7) })
        root.addView(searchRow)

        val tabs = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(9))
        }
        listOf(
            "الرائج" to { showFeed("trending") },
            "الأحدث" to { showFeed("latest") },
            "الأكثر مشاهدة" to { showFeed("views") },
            "التنزيلات" to { showDownloads() },
            "الإعدادات" to { showSettings() }
        ).forEach { item ->
            val b = actionButton(item.first)
            b.textSize = 10f
            tabs.addView(b, LinearLayout.LayoutParams(0, dp(39), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
            b.setOnClickListener { item.second() }
        }
        root.addView(tabs)

        val scroll = ScrollView(this).apply { setFillViewport(true) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(12))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showFeed(mode: String, query: String? = null) {
        currentMode = mode
        content.removeAllViews()
        val heading = when (mode) {
            "latest" -> "أحدث الفيديوهات"
            "views" -> "الأكثر مشاهدة"
            "search" -> "نتائج البحث"
            else -> "فيديوهات رائجة"
        }
        content.addView(label(heading, 18f, white, true).apply {
            setPadding(dp(3), dp(5), dp(3), dp(10))
        })
        val progress = ProgressBar(this)
        content.addView(progress, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
            gravity = Gravity.CENTER
            topMargin = dp(35); bottomMargin = dp(35)
        })
        executor.execute {
            val videos = fetchVideos(mode, query)
            runOnUiThread {
                content.removeAllViews()
                content.addView(label(heading, 18f, white, true).apply { setPadding(dp(3), dp(5), dp(3), dp(10)) })
                if (videos.isEmpty()) {
                    content.addView(label("لم تصل فيديوهات من الخوادم الآن. جرّب إعادة المحاولة.", 14f, muted, false).apply {
                        setPadding(dp(8), dp(28), dp(8), dp(20))
                    })
                    val retry = actionButton("إعادة المحاولة")
                    retry.setOnClickListener { showFeed(mode, query) }
                    content.addView(retry, LinearLayout.LayoutParams(-1, dp(46)))
                } else videos.forEach { addVideoCard(it) }
            }
        }
    }

    private fun fetchVideos(mode: String, query: String?): List<VideoItem> {
        val sort = when (mode) {
            "latest" -> "-publishedAt"
            "views" -> "-views"
            else -> "-trending"
        }
        for (base in apiBases) {
            val result = runCatching {
                var endpoint = "$base/api/v1/videos?count=30&sort=$sort&nsfw=false"
                if (!query.isNullOrBlank()) endpoint += "&search=" + URLEncoder.encode(query, "UTF-8")
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "VidMax/2.1 Android")
                try {
                    if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val data = json.optJSONArray("data") ?: return@runCatching emptyList<VideoItem>()
                    buildList {
                        for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i) ?: continue
                            val files = item.optJSONArray("files")
                            var selected = ""
                            var selectedHeight = -1
                            if (files != null) for (j in 0 until files.length()) {
                                val file = files.optJSONObject(j) ?: continue
                                val height = file.optJSONObject("resolution")?.optInt("id", 0) ?: 0
                                val raw = file.optString("fileUrl").ifBlank { file.optString("fileDownloadUrl") }
                                val resolved = resolveServerUrl(base, raw)
                                if (resolved.isNotBlank() && (height <= 720 && height > selectedHeight)) {
                                    selected = resolved
                                    selectedHeight = height
                                } else if (selected.isBlank() && resolved.isNotBlank()) selected = resolved
                            }
                            if (selected.startsWith("https://") || selected.startsWith("http://")) {
                                add(VideoItem(
                                    title = item.optString("name", "فيديو بدون عنوان"),
                                    channel = item.optJSONObject("account")?.optString("displayName").orEmpty(),
                                    thumbnail = resolveServerUrl(base, item.optString("thumbnailPath")),
                                    stream = selected,
                                    serverBase = base
                                ))
                            }
                        }
                    }
                } finally { connection.disconnect() }
            }.getOrNull()
            if (!result.isNullOrEmpty()) return result
        }
        return emptyList()
    }

    private fun resolveServerUrl(base: String, path: String): String {
        if (path.isBlank()) return ""
        if (path.startsWith("https://") || path.startsWith("http://")) return path
        return base.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun addVideoCard(video: VideoItem) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surface, 16)
            clipToOutline = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            contentDescription = "صورة الفيديو"
        }
        card.addView(image, LinearLayout.LayoutParams(-1, dp(190)))
        if (video.thumbnail.isNotBlank()) loadImage(video.thumbnail, image)

        val info = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(7), dp(2), 0)
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(video.title, 15f, white, true))
        if (video.channel.isNotBlank()) texts.addView(label(video.channel, 11f, muted, false))
        info.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        val download = TextView(this).apply {
            text = "⬇"
            textSize = 25f
            gravity = Gravity.CENTER
            contentDescription = "تنزيل الفيديو"
            setTextColor(navy)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
        }
        info.addView(download, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        download.setOnClickListener {
            if (prefs.getBoolean("confirm_download", false)) {
                android.app.AlertDialog.Builder(this)
                    .setMessage("تنزيل الفيديو: ${video.title}؟")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("تنزيل") { _, _ -> downloadVideo(video) }
                    .show()
            } else downloadVideo(video)
        }
        card.addView(info)
        card.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_MEDIA_URI, video.stream)
                .putExtra(PlayerActivity.EXTRA_AUTOPLAY, prefs.getBoolean("autoplay", true)))
        }
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
    }

    private fun loadImage(url: String, target: ImageView) {
        executor.execute {
            val bitmap = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 10000
                try { android.graphics.BitmapFactory.decodeStream(connection.inputStream) }
                finally { connection.disconnect() }
            }.getOrNull()
            if (bitmap != null) runOnUiThread { target.setImageBitmap(bitmap) }
        }
    }

    private fun searchVideos(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) {
            toast("اكتب اسم الفيديو الأول")
            return
        }
        showFeed("search", q)
    }

    private fun downloadVideo(video: VideoItem) {
        try {
            val name = video.title.replace(Regex("[^A-Za-z0-9ء-ي._ -]"), "")
                .trim().take(70).ifBlank { "VidMax_video" } + ".mp4"
            val request = DownloadManager.Request(Uri.parse(video.stream))
                .setTitle(name)
                .setDescription("تنزيل بواسطة VidMax")
                .setNotificationVisibility(
                    if (prefs.getBoolean("download_notification", true))
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    else DownloadManager.Request.VISIBILITY_HIDDEN
                )
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "VidMax/$name")
                .setAllowedOverMetered(!prefs.getBoolean("wifi_only", false))
                .setAllowedOverRoaming(false)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast("بدأ تنزيل الفيديو")
        } catch (_: Exception) {
            toast("تعذر بدء التنزيل")
        }
    }

    private fun showDownloads() {
        content.removeAllViews()
        content.addView(label("التنزيلات", 20f, white, true))
        val refresh = actionButton("تحديث القائمة")
        refresh.setOnClickListener { showDownloads() }
        content.addView(refresh, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query())
        var count = 0
        cursor.use {
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val totalCol = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val doneCol = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val reasonCol = it.getColumnIndex(DownloadManager.COLUMN_REASON)
            while (it.moveToNext()) {
                count++
                val title = if (titleCol >= 0) it.getString(titleCol) ?: "فيديو" else "فيديو"
                val status = if (statusCol >= 0) it.getInt(statusCol) else -1
                val total = if (totalCol >= 0) it.getLong(totalCol) else -1L
                val done = if (doneCol >= 0) it.getLong(doneCol) else 0L
                val reason = if (reasonCol >= 0) it.getInt(reasonCol) else 0
                val statusText = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "اكتمل التنزيل"
                    DownloadManager.STATUS_FAILED -> "فشل التنزيل (رمز $reason)"
                    DownloadManager.STATUS_RUNNING -> "جارٍ التنزيل"
                    DownloadManager.STATUS_PAUSED -> "متوقف مؤقتًا"
                    DownloadManager.STATUS_PENDING -> "في الانتظار"
                    else -> "حالة غير معروفة"
                }
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(surface, 12)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                card.addView(label(title, 14f, white, true))
                card.addView(label(statusText, 12f, muted, false))
                if (total > 0) {
                    val percent = ((done * 100L) / total).toInt().coerceIn(0, 100)
                    card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        progress = percent
                        max = 100
                    }, LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = dp(8) })
                    card.addView(label("$percent% • ${formatBytes(done)} / ${formatBytes(total)}", 11f, muted, false))
                } else if (status == DownloadManager.STATUS_RUNNING) {
                    card.addView(label("تم تنزيل ${formatBytes(done)}", 11f, muted, false))
                }
                content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        }
        if (count == 0) content.addView(label("لا توجد تنزيلات حتى الآن. ابدأ تنزيل فيديو من زر السهم ⬇.", 14f, muted, false).apply {
            setPadding(dp(4), dp(24), dp(4), dp(12))
        })
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
        return String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
    }

    private fun showSettings() {
        content.removeAllViews()
        content.addView(label("إعدادات VidMax", 20f, white, true))
        content.addView(label("حساب Google", 14f, accent, true).apply { setPadding(0, dp(14), 0, dp(4)) })
        val selectedGoogleAccount = prefs.getString("google_account_name", null)
        content.addView(label(
            if (selectedGoogleAccount.isNullOrBlank()) "لم يتم اختيار حساب Google" else "الحساب المختار: $selectedGoogleAccount",
            13f, muted, false
        ).apply { setPadding(0, dp(3), 0, dp(8)) })
        val chooseAccount = actionButton(if (selectedGoogleAccount.isNullOrBlank()) "اختيار حساب Google" else "تغيير حساب Google")
        chooseAccount.setOnClickListener { chooseGoogleAccount() }
        content.addView(chooseAccount, LinearLayout.LayoutParams(-1, dp(46)))
        if (!selectedGoogleAccount.isNullOrBlank()) {
            val disconnect = actionButton("فصل الحساب من VidMax")
            disconnect.setOnClickListener {
                prefs.edit().remove("google_account_name").apply()
                showSettings()
                toast("تم فصل الحساب من VidMax فقط")
            }
            content.addView(disconnect, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
        }
        content.addView(label(
            "تنبيه: اختيار الحساب يحفظ عنوانه داخل التطبيق فقط. Google لا تتيح عبر واجهة YouTube الرسمية قراءة سجل المشاهدة الخاص تلقائيًا.",
            11f, muted, false
        ).apply { setPadding(0, dp(8), 0, dp(4)) })
        content.addView(label("التنزيلات", 14f, accent, true).apply { setPadding(0, dp(14), 0, dp(4)) })
        addSetting("إظهار إشعارات التنزيل", "download_notification", true)
        addSetting("التنزيل عبر Wi-Fi فقط", "wifi_only", false)
        addSetting("التأكيد قبل بدء التنزيل", "confirm_download", false)
        content.addView(label("تشغيل الفيديو", 14f, accent, true).apply { setPadding(0, dp(14), 0, dp(4)) })
        addSetting("تشغيل الفيديو تلقائيًا عند فتحه", "autoplay", true)
        content.addView(label("مصدر الفيديوهات: PeerTube العام. قد يختلف المحتوى وتوفره حسب الخادم واتصال الإنترنت.", 12f, muted, false)
            .apply { setPadding(0, dp(16), 0, 0) })
    }

    private fun addSetting(title: String, key: String, default: Boolean) {
        val toggle = Switch(this).apply {
            text = title
            textSize = 14f
            setTextColor(white)
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
        }
        content.addView(toggle, LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        textDirection = View.TEXT_DIRECTION_RTL
    }

    private fun actionButton(value: String) = Button(this).apply {
        text = value
        textSize = 12f
        isAllCaps = false
        setTextColor(white)
        background = rounded(surface, 12)
        minHeight = dp(38)
        minimumHeight = dp(38)
        stateListAnimator = null
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()

    private data class VideoItem(
        val title: String,
        val channel: String,
        val thumbnail: String,
        val stream: String,
        val serverBase: String
    )
}
