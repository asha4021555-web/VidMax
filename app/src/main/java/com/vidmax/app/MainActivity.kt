package com.vidmax.app

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var linkInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.navy)
        window.navigationBarColor = getColor(R.color.navy)
        buildScreen()
        acceptIncomingLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIncomingLink(intent)
    }

    private fun acceptIncomingLink(intent: Intent?) {
        val shared = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (!shared.isNullOrBlank() && ::linkInput.isInitialized) {
            linkInput.setText(shared.trim())
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            setBackgroundColor(getColor(R.color.navy))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(content)

        val brand = TextView(this).apply {
            text = "VIDMAX"
            textSize = 30f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(R.color.accent))
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(4))
        }
        content.addView(brand, matchWrap())

        val tagline = label("حمّل واحتفظ بمقاطعك بسهولة", 16f, R.color.white, true)
        tagline.gravity = Gravity.CENTER
        content.addView(tagline, matchWrap())

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundColor(getColor(R.color.surface))
        }
        val panelParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(30) }
        content.addView(panel, panelParams)

        panel.addView(label("رابط الفيديو", 17f, R.color.white, true), matchWrap())
        linkInput = EditText(this).apply {
            hint = "الصق رابطًا مباشرًا للفيديو هنا"
            textSize = 14f
            singleLine = false
            minLines = 2
            maxLines = 3
            setTextColor(getColor(R.color.white))
            setHintTextColor(getColor(R.color.muted))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(getColor(R.color.surface_light))
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
        }
        val inputParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }
        panel.addView(linkInput, inputParams)

        val paste = makeButton("لصق الرابط")
        paste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val value = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
            if (value.isNullOrBlank()) toast("الحافظة فارغة") else linkInput.setText(value)
        }
        val pasteParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        ).apply { topMargin = dp(12) }
        panel.addView(paste, pasteParams)

        val download = makeButton("بدء التحميل")
        download.setOnClickListener { startDirectDownload() }
        val downloadParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(10) }
        panel.addView(download, downloadParams)

        val note = label(
            "ملاحظة: الإصدار الأول يقبل روابط ملفات الفيديو المباشرة التي يسمح مصدرها بتنزيلها. روابط صفحات YouTube وTikTok وغيرها ليست روابط ملفات مباشرة.",
            13f, R.color.muted, false
        )
        note.setPadding(dp(4), dp(18), dp(4), 0)
        content.addView(note, matchWrap())

        val features = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(getColor(R.color.surface))
        }
        val featureParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(22) }
        content.addView(features, featureParams)
        features.addView(label("مميزات VidMax", 17f, R.color.white, true), matchWrap())
        features.addView(label("• واجهة عربية بالكامل", 14f, R.color.muted, false), withTop(10))
        features.addView(label("• استقبال الروابط من التطبيقات الأخرى عبر المشاركة", 14f, R.color.muted, false), withTop(8))
        features.addView(label("• التنزيل في الخلفية عبر مدير تنزيلات Android", 14f, R.color.muted, false), withTop(8))
        features.addView(label("• دعم أجهزة ARM ‏32-bit و64-bit في حزمة البناء", 14f, R.color.muted, false), withTop(8))

        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val footer = label("VIDMAX  •  الإصدار 1.0.0", 12f, R.color.muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(footer, matchWrap())
        setContentView(root)
    }

    private fun startDirectDownload() {
        val raw = linkInput.text?.toString()?.trim().orEmpty()
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (uri == null || (uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank()) {
            toast("من فضلك أدخل رابطًا صحيحًا يبدأ بـ https أو http")
            return
        }
        val fileName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.contains('.') && it.length <= 120 }
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?: "vidmax_${System.currentTimeMillis()}.mp4"

        try {
            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setDescription("جارٍ التنزيل بواسطة VidMax")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "VidMax/$fileName")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(linkInput.windowToken, 0)
            toast("بدأ التنزيل. ستجد الملف داخل مجلد Downloads/VidMax")
        } catch (_: Exception) {
            toast("تعذر بدء التنزيل. تأكد من صلاحية الرابط واتصال الإنترنت")
        }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(getColor(color))
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
        }

    private fun makeButton(text: String) = Button(this).apply {
        this.text = text
        textSize = 15f
        isAllCaps = false
        setTextColor(getColor(R.color.navy))
        setBackgroundColor(getColor(R.color.accent))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun withTop(top: Int) = matchWrap().apply { topMargin = dp(top) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
