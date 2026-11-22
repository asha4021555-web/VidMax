package com.vidmax.app

import android.app.Activity
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

    // A native PeerTube feed: no YouTube/Instagram WebView and no browser chrome.
    private val apiBase = "https://peertube2.cpy.re"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        buildShell()
        showFeed("trending")
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
        val sort = when (mode) {
            "latest" -> "-publishedAt"
            "views" -> "-views"
            else -> "-trending"
        }
        var endpoint = "$apiBase/api/v1/videos?count=30&sort=$sort&nsfw=false&skipCount=true"
        if (!query.isNullOrBlank()) {
            endpoint += "&search=" + URLEncoder.encode(query, "UTF-8")
        }
        executor.execute {
            val videos = runCatching {
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 12000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/json")
                try {
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val data = json.optJSONArray("data")
                    buildList {
                        if (data != null) for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i) ?: continue
                            val files = item.optJSONArray("files")
                            var stream = ""
                            if (files != null && files.length() > 0) {
                                var chosen = files.optJSONObject(0)
                                for (j in 0 until files.length()) {
                                    val f = files.optJSONObject(j) ?: continue
                                    val height = f.optJSONObject("resolution")?.optInt("id", 0) ?: 0
                                    val oldHeight = chosen?.optJSONObject("resolution")?.optInt("id", 0) ?: 0
                                    if (height in 1..720 && (oldHeight == 0 || height > oldHeight)) chosen = f
                                }
                                stream = chosen?.optString("fileUrl").orEmpty()
                            }
                            if (stream.startsWith("https://")) add(
                                VideoItem(
                                    title = item.optString("name", "فيديو بدون عنوان"),
                                    channel = item.optJSONObject("account")?.optString("displayName").orEmpty(),
                                    thumbnail = item.optString("thumbnailPath").let { path ->
                                        if (path.startsWith("http")) path else apiBase + path
                                    },
                                    stream = stream
                                )
                            )
                        }
                    }
                } finally { connection.disconnect() }
            }.getOrElse { emptyList() }
            runOnUiThread {
                content.removeAllViews()
                content.addView(label(heading, 18f, white, true).apply {
                    setPadding(dp(3), dp(5), dp(3), dp(10))
                })
                if (videos.isEmpty()) {
                    content.addView(label(
                        "تعذر تحميل الفيديوهات الآن. تأكد من اتصال الإنترنت وحاول مرة أخرى.",
                        14f, muted, false
                    ).apply { setPadding(dp(8), dp(28), dp(8), dp(20)) })
                    val retry = actionButton("إعادة المحاولة")
                    retry.setOnClickListener { showFeed(mode, query) }
                    content.addView(retry, LinearLayout.LayoutParams(-1, dp(46)))
                } else {
                    videos.forEach { addVideoCard(it) }
                }
            }
        }
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
        download.setOnClickListener { downloadVideo(video) }
        card.addView(info)
        card.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_MEDIA_URI, video.stream))
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
                .setAllowedOverMetered(true)
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
        val refresh = actionButton("تحديث")
        refresh.setOnClickListener { showDownloads() }
        content.addView(refresh, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query())
        var count = 0
        cursor.use {
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            while (it.moveToNext()) {
                count++
                val title = if (titleCol >= 0) it.getString(titleCol) ?: "فيديو" else "فيديو"
                val status = if (statusCol >= 0) it.getInt(statusCol) else -1
                val statusText = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "اكتمل"
                    DownloadManager.STATUS_FAILED -> "فشل"
                    DownloadManager.STATUS_RUNNING -> "جارٍ التنزيل"
                    DownloadManager.STATUS_PAUSED -> "متوقف مؤقتًا"
                    else -> "في الانتظار"
                }
                content.addView(label("$title\n$statusText", 14f, white, false).apply {
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = rounded(surface, 12)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        }
        if (count == 0) content.addView(label("لا توجد تنزيلات حتى الآن", 14f, muted, false))
    }

    private fun showSettings() {
        content.removeAllViews()
        content.addView(label("إعدادات VidMax", 20f, white, true))
        addSetting("إشعار عند اكتمال التنزيل", "download_notification", true)
        content.addView(label(
            "الفيديوهات تُعرض داخل مشغل VidMax مباشرة. مصدر الموجز الحالي شبكة PeerTube العامة؛ وقد يختلف المحتوى وتوفره حسب الخادم.",
            13f, muted, false
        ).apply { setPadding(0, dp(16), 0, 0) })
    }

    private fun addSetting(title: String, key: String, default: Boolean) {
        val toggle = Switch(this).apply {
            text = title
            textSize = 14f
            setTextColor(white)
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
        }
        content.addView(toggle, LinearLayout.LayoutParams(-1, dp(58)))
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
        val stream: String
    )
}
