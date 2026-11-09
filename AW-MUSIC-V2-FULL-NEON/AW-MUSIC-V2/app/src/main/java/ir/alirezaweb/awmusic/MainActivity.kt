package ir.alirezaweb.awmusic

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val albumId: Long, val album: String)

class MainActivity : Activity() {
    private val tracks = mutableListOf<Track>()
    private val recent = mutableListOf<Long>()
    private val favorites = mutableSetOf<Long>()
    private var player: MediaPlayer? = null
    private var current = -1
    private var isEnglish = false
    private var shuffle = false
    private var repeat = false
    private var filterMode = "all"
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var playButton: TextView
    private lateinit var seek: SeekBar
    private lateinit var timeText: TextView
    private lateinit var countText: TextView
    private lateinit var searchBox: EditText
    private val prefs by lazy { getSharedPreferences("aw_music_v3", Context.MODE_PRIVATE) }
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isEnglish = prefs.getBoolean("english", false)
        favorites.addAll(prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() })
        recent.addAll(prefs.getString("recent", "")!!.split(",").mapNotNull { it.toLongOrNull() })
        buildUi()
        requestAudioPermission()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(fa: String, en: String) = if (isEnglish) en else fa
    private fun shape(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun button(value: String, action: () -> Unit) = TextView(this).apply {
        text = value; textSize = 14f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); setPadding(dp(13), dp(10), dp(13), dp(10))
        background = shape(Color.rgb(25, 31, 49), 13, Color.rgb(0, 205, 240))
        setOnClickListener { action() }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.rgb(7, 9, 20))
            layoutDirection = if (isEnglish) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = text("A W   M U S I C", 28f, Color.CYAN, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(2))
            setShadowLayer(dp(14).toFloat(), 0f, 0f, Color.CYAN)
        }
        content.addView(header, LinearLayout.LayoutParams(-1, dp(48)))
        val tagline = text("NEON AUDIO EXPERIENCE  •  ALIREZA WEB", 9f, Color.rgb(255, 45, 195)).apply {
            gravity = Gravity.CENTER; letterSpacing = .16f
        }
        content.addView(tagline)
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val heading = text(t("کتابخانه موسیقی", "MUSIC LIBRARY"), 21f, Color.WHITE, true)
        top.addView(heading, LinearLayout.LayoutParams(0, dp(52), 1f))
        top.addView(button(if (isEnglish) "فارسی" else "EN") {
            isEnglish = !isEnglish; prefs.edit().putBoolean("english", isEnglish).apply()
            buildUi(); renderTracks(searchBox.text.toString())
        })
        top.addView(button(t("درباره", "ABOUT")) { showAbout() }.apply { setPadding(dp(9), dp(10), dp(9), dp(10)) })
        content.addView(top)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
            background = shape(Color.rgb(16, 22, 40), 20, Color.rgb(0, 205, 240))
        }
        hero.addView(text(t("موسیقی خودت، با حال‌وهوای نئون", "YOUR MUSIC. YOUR NEON."), 15f, Color.WHITE, true))
        hero.addView(text(t("تمام آهنگ‌های گوشی در یک کتابخانه", "Your local tracks in one library"), 11f, Color.LTGRAY).apply { setPadding(0, dp(5), 0, 0) })
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        searchBox = EditText(this).apply {
            hint = t("جست‌وجوی آهنگ، خواننده یا آلبوم…", "Search title, artist or album…")
            setSingleLine(true); textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            background = shape(Color.rgb(17, 22, 36), 14, Color.rgb(115, 65, 175))
            setPadding(dp(14), 0, dp(14), 0)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderTracks(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        content.addView(searchBox, LinearLayout.LayoutParams(-1, dp(46)))
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(6)) }
        listOf("all" to t("همه", "ALL"), "favorites" to t("علاقه‌مندی", "LIKED"), "recent" to t("اخیر", "RECENT")).forEach { (key, name) ->
            tabs.addView(button(name) { filterMode = key; renderTracks(searchBox.text.toString()) },
                LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        }
        content.addView(tabs)
        val listHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        listHeader.addView(text(t("آهنگ‌ها", "TRACKS"), 16f, Color.WHITE, true), LinearLayout.LayoutParams(0, dp(40), 1f))
        countText = text("0", 11f, Color.CYAN)
        listHeader.addView(countText)
        content.addView(listHeader)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val playerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(10), dp(13), dp(8))
            background = shape(Color.rgb(17, 22, 38), 19, Color.rgb(255, 45, 190))
        }
        nowTitle = text(t("آهنگی انتخاب نشده", "NO TRACK SELECTED"), 14f, Color.WHITE, true)
        nowArtist = text("ALIREZA WEB", 11f, Color.LTGRAY)
        playerPanel.addView(nowTitle); playerPanel.addView(nowArtist)
        seek = SeekBar(this); seek.max = 1000
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, from: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { player?.let { if (it.duration > 0) it.seekTo(it.duration * seek.progress / 1000) } }
        })
        playerPanel.addView(seek)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        controls.addView(button("⏮") { previousTrack() })
        playButton = button("▶") { togglePlay() }.apply { textSize = 19f }
        controls.addView(playButton)
        controls.addView(button("⏭") { nextTrack() })
        controls.addView(button("♡") { toggleFavorite() })
        controls.addView(button("⇄") { shuffle = !shuffle; toast(t("تصادفی ", "Shuffle ") + if (shuffle) "ON" else "OFF") })
        controls.addView(button("↻") { repeat = !repeat; toast(t("تکرار ", "Repeat ") + if (repeat) "ON" else "OFF") })
        playerPanel.addView(controls)
        timeText = text("00:00 / 00:00", 10f, Color.LTGRAY).apply { gravity = Gravity.CENTER }
        playerPanel.addView(timeText)
        root.addView(playerPanel)
        setContentView(root)
        startTicker()
        animateHeader(header)
    }

    private fun animateHeader(view: TextView) {
        val colors = intArrayOf(Color.CYAN, Color.MAGENTA, Color.rgb(125, 90, 255), Color.GREEN)
        var i = 0
        val run = object : Runnable {
            override fun run() {
                val from = colors[i]; i = (i + 1) % colors.size
                val anim = android.animation.ValueAnimator.ofArgb(from, colors[i])
                anim.duration = 1000
                anim.addUpdateListener { val c = it.animatedValue as Int; view.setTextColor(c); view.setShadowLayer(dp(14).toFloat(), 0f, 0f, c) }
                anim.start(); handler.postDelayed(this, 1000)
            }
        }
        handler.post(run)
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(permission), 10)
        else loadTracks()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 10 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) loadTracks()
        else toast(t("برای نمایش آهنگ‌ها دسترسی صوتی لازم است.", "Audio permission is required."))
    }
    private fun loadTracks() {
        tracks.clear()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ALBUM)
        try {
            contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                while (c.moveToNext()) tracks.add(Track(c.getLong(id), c.getString(title) ?: "Unknown", c.getString(artist) ?: "Unknown", ContentUris.withAppendedId(collection, c.getLong(id)), c.getLong(albumId), c.getString(album) ?: ""))
            }
        } catch (_: Exception) {}
        renderTracks()
    }

    private fun renderTracks(query: String = "") {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val filtered = tracks.filter { track ->
            val match = track.title.contains(query, true) || track.artist.contains(query, true) || track.album.contains(query, true)
            match && when (filterMode) {
                "favorites" -> favorites.contains(track.id)
                "recent" -> recent.contains(track.id)
                else -> true
            }
        }.let { items -> if (filterMode == "recent") items.sortedBy { recent.indexOf(it.id) } else items }
        countText.text = "${filtered.size} ${t("آهنگ", "tracks")}"
        if (filtered.isEmpty()) {
            list.addView(text(if (tracks.isEmpty()) t("هنوز آهنگی پیدا نشده؛ فایل صوتی روی گوشی داشته باش.", "No local audio files found.") else t("موردی پیدا نشد.", "Nothing found."), 12f, Color.LTGRAY).apply { gravity = Gravity.CENTER; setPadding(0, dp(26), 0, dp(26)) })
            return
        }
        filtered.forEachIndexed { index, track ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = shape(Color.rgb(16, 21, 36), 16, if (index % 2 == 0) Color.rgb(0, 190, 230) else Color.rgb(130, 65, 170))
                setOnClickListener { playTrack(track) }
            }
            val art = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = shape(Color.rgb(27, 35, 55), 12)
                setImageResource(android.R.drawable.ic_media_play)
                try {
                    val albumUri = Uri.parse("content://media/external/audio/albumart/${track.albumId}")
                    contentResolver.openInputStream(albumUri)?.use { stream -> BitmapFactory.decodeStream(stream)?.let { setImageBitmap(it) } }
                } catch (_: Exception) {}
            }
            card.addView(art, LinearLayout.LayoutParams(dp(54), dp(54)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, dp(6), 0) }
            info.addView(text(track.title, 13f, Color.WHITE, true).apply { maxLines = 1 })
            info.addView(text(track.artist, 10f, Color.LTGRAY).apply { maxLines = 1 })
            if (track.album.isNotBlank()) info.addView(text(track.album, 9f, Color.rgb(0, 205, 230)).apply { maxLines = 1 })
            card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(text(if (favorites.contains(track.id)) "♥" else "♫", 19f, Color.rgb(255, 45, 190)))
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun playTrack(track: Track) {
        try {
            player?.release()
            current = tracks.indexOf(track)
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, track.uri)
                setOnCompletionListener { if (repeat) playTrack(track) else nextTrack() }
                prepare(); start()
            }
            recent.remove(track.id); recent.add(0, track.id)
            if (recent.size > 100) recent.removeAt(recent.lastIndex)
            saveLibrary()
            nowTitle.text = track.title; nowArtist.text = track.artist
            playButton.text = "Ⅱ"
            renderTracks(searchBox.text.toString())
        } catch (_: Exception) { toast(t("پخش این فایل ممکن نیست.", "Unable to play this file.")) }
    }
    private fun togglePlay() {
        val p = player
        if (p == null) { if (tracks.isNotEmpty()) playTrack(tracks.first()); return }
        if (p.isPlaying) { p.pause(); playButton.text = "▶" } else { p.start(); playButton.text = "Ⅱ" }
    }
    private fun nextTrack() {
        if (tracks.isEmpty()) return
        val idx = if (shuffle) tracks.indices.random() else (if (current + 1 >= tracks.size) 0 else current + 1)
        playTrack(tracks[idx])
    }
    private fun previousTrack() {
        if (tracks.isEmpty()) return
        val idx = if (current <= 0) tracks.lastIndex else current - 1
        playTrack(tracks[idx])
    }
    private fun toggleFavorite() {
        if (current !in tracks.indices) return
        val id = tracks[current].id
        if (!favorites.add(id)) favorites.remove(id)
        saveLibrary(); renderTracks(searchBox.text.toString())
        toast(t("علاقه‌مندی ذخیره شد.", "Favorites saved."))
    }
    private fun saveLibrary() {
        prefs.edit().putStringSet("favorites", favorites.map { it.toString() }.toSet())
            .putString("recent", recent.joinToString(",")).apply()
    }
    private fun showAbout() {
        android.app.AlertDialog.Builder(this)
            .setTitle(t("درباره AW MUSIC", "ABOUT AW MUSIC"))
            .setMessage("AW MUSIC 2.1\n\n" + t("طراحی و توسعه: علیرضا مزارعی", "Designed and developed by Alireza Mazarei") + "\nALIREZA WEB\nalireza-mazarei.ir\nInstagram: @alireza_web_office")
            .setPositiveButton("OK", null).show()
    }
    private fun startTicker() {
        handler.post(object : Runnable {
            override fun run() {
                player?.let { p ->
                    try {
                        if (p.duration > 0) {
                            seek.progress = (p.currentPosition * 1000L / p.duration).toInt()
                            timeText.text = "${fmt(p.currentPosition)} / ${fmt(p.duration)}"
                        }
                    } catch (_: Exception) {}
                }
                handler.postDelayed(this, 500)
            }
        })
    }
    private fun fmt(ms: Int) = String.format(Locale.US, "%02d:%02d", ms / 60000, (ms / 1000) % 60)
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { player?.release(); player = null; handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
