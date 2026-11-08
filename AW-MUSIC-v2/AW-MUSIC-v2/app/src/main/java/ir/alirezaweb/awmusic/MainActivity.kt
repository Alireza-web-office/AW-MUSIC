package ir.alirezaweb.awmusic

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.util.Locale

private data class Track(val id: Long, val title: String, val artist: String, val duration: Long, val uri: Uri)

class MainActivity : Activity() {
    private val tracks = mutableListOf<Track>()
    private var shown = listOf<Track>()
    private var player: MediaPlayer? = null
    private var currentId = -1L
    private var tab = "all"
    private var english = false
    private var shuffle = false
    private var repeat = false
    private val favorites = mutableSetOf<Long>()
    private val recent = mutableListOf<Long>()
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private lateinit var count: TextView
    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var playButton: TextView
    private lateinit var seek: SeekBar
    private lateinit var elapsed: TextView
    private lateinit var total: TextView

    private val bg = Color.rgb(7, 11, 19)
    private val panel = Color.rgb(17, 24, 39)
    private val cyan = Color.rgb(0, 217, 255)
    private val purple = Color.rgb(164, 92, 255)
    private val white = Color.rgb(240, 245, 255)
    private val muted = Color.rgb(155, 169, 190)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("aw_music", MODE_PRIVATE)
        favorites.addAll(prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() })
        recent.addAll(prefs.getString("recent", "")!!.split(",").mapNotNull { it.toLongOrNull() })
        english = prefs.getBoolean("english", false)
        buildUi()
        askPermission()
    }

    private fun tr(fa: String, en: String) = if (english) en else fa
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, stroke: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat(); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke) }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    private fun button(value: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = value; textSize = 14f; gravity = Gravity.CENTER; setTextColor(cyan)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(Color.rgb(22, 34, 51), Color.rgb(42, 66, 91))
            setOnClickListener { action() }
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        brand.addView(text("AW MUSIC", 28f, cyan, true).apply { letterSpacing = .12f })
        brand.addView(text("ALIREZA WEB  •  OFFLINE PLAYER", 10f, muted))
        header.addView(brand)
        header.addView(button(if (english) "FA" else "EN") {
            english = !english
            getSharedPreferences("aw_music", MODE_PRIVATE).edit().putBoolean("english", english).apply()
            buildUi()
            render()
        })
        root.addView(header)
        root.addView(space(14))

        search = EditText(this).apply {
            hint = tr("جست‌وجوی آهنگ یا خواننده", "Search songs or artists")
            setTextColor(white); setHintTextColor(muted); textSize = 14f
            maxLines = 1; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(panel, Color.rgb(42, 66, 91))
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(space(12))

        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nav.addView(navButton("all", tr("全部", "All")))
        nav.addView(navButton("favorites", tr("收藏", "Favorites")))
        nav.addView(navButton("recent", tr("最近", "Recent")))
        nav.addView(navButton("about", tr("درباره من", "About me")))
        root.addView(nav)
        root.addView(space(10))

        count = text(tr("در حال آماده‌سازی...", "Preparing library..."), 12f, muted)
        root.addView(count)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val playerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(9))
            background = rounded(panel, Color.rgb(47, 65, 91))
        }
        val now = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        now.addView(TextView(this).apply {
            text = "♫"; textSize = 26f; gravity = Gravity.CENTER; setTextColor(cyan)
            background = rounded(bg, purple)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        nowTitle = text(tr("هنوز آهنگی انتخاب نشده", "No song selected"), 14f, white, true)
        nowArtist = text(tr("یک آهنگ از فهرست انتخاب کن", "Choose a song from your library"), 11f, muted)
        names.addView(nowTitle); names.addView(nowArtist)
        now.addView(names)
        playerBox.addView(now)
        seek = SeekBar(this).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
            thumbTintList = android.content.res.ColorStateList.valueOf(cyan)
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    if (user) player?.let { if (it.duration > 0) it.seekTo((it.duration * p / 1000)) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        playerBox.addView(seek)
        val times = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        elapsed = text("0:00", 10f, muted)
        total = text("0:00", 10f, muted)
        times.addView(elapsed, LinearLayout.LayoutParams(0, -2, 1f))
        times.addView(total)
        playerBox.addView(times)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }
        controls.addView(button("⤨") { shuffle = !shuffle; toast(if (shuffle) tr("پخش تصادفی روشن شد", "Shuffle on") else tr("پخش تصادفی خاموش شد", "Shuffle off")) })
        controls.addView(button("|◀") { step(-1) })
        playButton = button("▶") { toggle() }
        controls.addView(playButton)
        controls.addView(button("▶|") { step(1) })
        controls.addView(button("↻") { repeat = !repeat; toast(if (repeat) tr("تکرار روشن شد", "Repeat on") else tr("تکرار خاموش شد", "Repeat off")) })
        playerBox.addView(controls)
        root.addView(space(8))
        root.addView(playerBox)
        setContentView(root)

        search.addTextChangedListenerSimple { render() }
        render()
    }

    private fun space(h: Int) = android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    private fun navButton(id: String, label: String) = button(label) {
        tab = id
        render()
    }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) } }

    private fun EditText.addTextChangedListenerSimple(onChange: () -> Unit) {
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChange() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun askPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 42)
        } else loadTracks()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 42 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) loadTracks()
        else toast(tr("برای نمایش آهنگ‌ها اجازه دسترسی لازم است.", "Audio permission is required to show songs."))
    }

    private fun loadTracks() {
        tracks.clear()
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        try {
            contentResolver.query(collection, columns, "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val key = c.getLong(id)
                    tracks.add(Track(key, c.getString(title) ?: "Unknown",
                        c.getString(artist) ?: "Unknown artist", c.getLong(duration),
                        ContentUris.withAppendedId(collection, key)))
                }
            }
        } catch (_: Exception) {
            toast(tr("خواندن فهرست موسیقی ناموفق بود.", "Could not read music library."))
        }
        render()
    }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        if (tab == "about") {
            count.text = tr("درباره سازنده", "About the creator")
            showAbout()
            return
        }
        val query = if (::search.isInitialized) search.text.toString().trim().lowercase(Locale.getDefault()) else ""
        val source = when (tab) {
            "favorites" -> tracks.filter { favorites.contains(it.id) }
            "recent" -> recent.mapNotNull { id -> tracks.find { it.id == id } }
            else -> tracks
        }
        shown = source.filter {
            it.title.lowercase(Locale.getDefault()).contains(query) ||
                it.artist.lowercase(Locale.getDefault()).contains(query)
        }
        count.text = tr("آهنگ‌ها  •  ${shown.size}", "TRACKS  •  ${shown.size}")
        if (shown.isEmpty()) {
            list.addView(text(
                if (tracks.isEmpty()) tr("هنوز آهنگی پیدا نشده؛ فایل موسیقی به گوشی اضافه کن.", "No music found. Add audio files to your phone.")
                else tr("نتیجه‌ای پیدا نشد.", "No results found."),
                13f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(40), 0, dp(40)) })
            return
        }
        shown.forEach { track ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(10), dp(10))
                background = rounded(panel)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
            }
            val symbol = text("♫", 22f, cyan, true).apply { gravity = Gravity.CENTER }
            row.addView(symbol, LinearLayout.LayoutParams(dp(40), dp(42)))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(9), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            info.addView(text(track.title, 13f, white, true).apply { maxLines = 1 })
            info.addView(text("${track.artist}  •  ${format(track.duration)}", 10f, muted))
            row.addView(info)
            row.addView(button(if (favorites.contains(track.id)) "♥" else "♡") { toggleFavorite(track) })
            row.setOnClickListener { play(track) }
            list.addView(row)
        }
    }

    private fun showAbout() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = rounded(panel, purple)
        }
        box.addView(text("AW", 42f, cyan, true).apply { gravity = Gravity.CENTER; letterSpacing = .18f })
        box.addView(text("ALIREZA WEB", 18f, white, true).apply { gravity = Gravity.CENTER })
        box.addView(space(12))
        box.addView(text(tr("درباره من", "About me"), 17f, cyan, true))
        box.addView(text(
            tr("من علیرضا مزارعی هستم؛ سازنده AW MUSIC و بنیان‌گذار ALIREZA WEB. به طراحی وب، فناوری و ساخت ابزارهای کاربردی علاقه دارم.",
               "I'm Alireza Mazarei, creator of AW MUSIC and founder of ALIREZA WEB. I'm interested in web design, technology, and building useful tools."),
            13f, white).apply { setPadding(0, dp(8), 0, dp(14)) })
        box.addView(text(tr("درباره برنامه", "About the app"), 15f, cyan, true))
        box.addView(text(
            tr("AW MUSIC یک پخش‌کننده موسیقی آفلاین برای پخش فایل‌های صوتی موجود در دستگاه است.",
               "AW MUSIC is an offline music player for audio files stored on your device."),
            12f, muted).apply { setPadding(0, dp(7), 0, dp(14)) })
        box.addView(text("Version 2.0.0", 11f, muted))
        box.addView(text("Designed & developed by ALIREZA WEB", 10f, muted))
        list.addView(box)
    }

    private fun toggleFavorite(track: Track) {
        if (!favorites.add(track.id)) favorites.remove(track.id)
        getSharedPreferences("aw_music", MODE_PRIVATE).edit()
            .putStringSet("favorites", favorites.map { it.toString() }.toSet()).apply()
        render()
    }

    private fun play(track: Track) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, track.uri)
                setOnCompletionListener {
                    if (repeat) play(track) else step(1)
                }
                prepare()
                start()
            }
            currentId = track.id
            nowTitle.text = track.title
            nowArtist.text = track.artist
            playButton.text = "Ⅱ"
            recent.remove(track.id)
            recent.add(0, track.id)
            if (recent.size > 30) recent.removeAt(recent.lastIndex)
            getSharedPreferences("aw_music", MODE_PRIVATE).edit()
                .putString("recent", recent.joinToString(",")).apply()
            seek.progress = 0
            total.text = format(track.duration)
            elapsed.text = "0:00"
        } catch (_: Exception) {
            player?.release(); player = null; playButton.text = "▶"
            toast(tr("پخش این فایل ممکن نیست.", "This file cannot be played."))
        }
    }

    private fun toggle() {
        val p = player
        if (p == null) {
            tracks.firstOrNull()?.let { play(it) }
        } else if (p.isPlaying) {
            p.pause(); playButton.text = "▶"
        } else {
            p.start(); playButton.text = "Ⅱ"
        }
    }

    private fun step(delta: Int) {
        if (tracks.isEmpty()) return
        val index = tracks.indexOfFirst { it.id == currentId }
        val next = if (shuffle && tracks.size > 1) {
            (tracks.indices - index).random()
        } else if (index < 0) 0 else (index + delta + tracks.size) % tracks.size
        play(tracks[next])
    }

    private fun format(ms: Long): String {
        val seconds = ms / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
