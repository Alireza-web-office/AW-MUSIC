package ir.alirezaweb.awmusic

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.media.PlaybackParams
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import java.util.Locale

data class Track(
    val id: Long, val title: String, val artist: String,
    val uri: Uri, val albumId: Long, val album: String
)

class MainActivity : Activity() {
    private val tracks = mutableListOf<Track>()
    private val recent = mutableListOf<Long>()
    private val favorites = mutableSetOf<Long>()
    private val playlists = linkedMapOf<String, MutableList<Long>>()
    private val playQueue = mutableListOf<Long>()
    private var sortMode = "title"
    private var sleepRunnable: Runnable? = null
    private var player: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private val artworkCache = android.util.LruCache<Long, Bitmap>(12)
    private var playbackSpeed = 1.0f
    private var playerPrepared = false
    private val lyricsByTrack = mutableMapOf<Long, String>()
    private var currentTrackId: Long? = null
    private var shuffle = false
    // 0 = off, 1 = repeat all, 2 = repeat one
    private var repeatMode = 0
    private var filterMode = "all"
    private var screen = "library"
    private lateinit var root: FrameLayout
    private lateinit var page: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var countText: TextView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: TextView
    private lateinit var seek: SeekBar
    private lateinit var timeText: TextView
    private lateinit var detailTitle: TextView
    private lateinit var detailArtist: TextView
    private lateinit var detailArt: ImageView
    private lateinit var detailPlay: TextView
    private lateinit var favoriteButton: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("aw_music_final", Context.MODE_PRIVATE) }
    private val cyan = Color.rgb(0, 225, 255)
    private val pink = Color.rgb(255, 45, 205)
    private val bg = Color.rgb(7, 9, 20)
    private val panel = Color.rgb(17, 23, 40)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackSpeed = prefs.getFloat("playback_speed", 1.0f)
        favorites.addAll(prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() })
        recent.addAll(prefs.getString("recent", "")!!.split(",").mapNotNull { it.toLongOrNull() })
        prefs.getString("playlists", "")!!.split("\n").filter { it.contains("::") }.forEach { row ->
            val parts = row.split("::", limit = 2)
            if (parts.size == 2) playlists[parts[0]] = parts[1].split(",").mapNotNull { it.toLongOrNull() }.toMutableList()
        }
        playQueue.addAll(prefs.getString("queue", "")!!.split(",").mapNotNull { it.toLongOrNull() })
        buildShell()
        showLibrary()
        showSplash()
        requestAudioPermission()
        startTicker()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun actionButton(value: String, action: () -> Unit, accent: Int = cyan) =
        TextView(this).apply {
            text = value
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            minWidth = dp(48)
            minHeight = dp(46)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = shape(Color.rgb(25, 32, 52), 15, accent)
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun params(width: Int = -1, height: Int = -2, bottom: Int = 0) =
        LinearLayout.LayoutParams(width, height).apply { if (bottom > 0) bottomMargin = dp(bottom) }

    private fun buildShell() {
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        window.statusBarColor = bg
        window.navigationBarColor = bg
    }

    private fun showSplash() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            alpha = 1f
        }
        val logo = label("A W", 58f, cyan, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = .12f
            setShadowLayer(dp(24).toFloat(), 0f, 0f, cyan)
        }
        splash.addView(logo, params(-1, dp(100)))
        splash.addView(label("M U S I C", 19f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = .25f
        })
        splash.addView(label("ALIREZA WEB  •  NEON AUDIO", 10f, pink).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
            letterSpacing = .12f
        })
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        splash.scaleX = .94f
        splash.scaleY = .94f
        splash.animate().scaleX(1f).scaleY(1f).setDuration(650).setInterpolator(DecelerateInterpolator()).start()
        splash.animate().alpha(0f).setStartDelay(1450).setDuration(450).withEndAction {
            root.removeView(splash)
        }.start()
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 10)
        } else loadTracks()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 10 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) loadTracks()
        else toast("برای نمایش موسیقی‌ها، اجازه دسترسی به فایل‌های صوتی لازم است.")
    }

    private fun loadTracks() {
        tracks.clear()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM
        )
        try {
            contentResolver.query(
                collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                while (c.moveToNext()) {
                    tracks.add(
                        Track(
                            c.getLong(id), c.getString(title) ?: "بدون عنوان",
                            c.getString(artist)?.takeUnless { it == "<unknown>" } ?: "خواننده ناشناس",
                            ContentUris.withAppendedId(collection, c.getLong(id)),
                            c.getLong(albumId), c.getString(album) ?: ""
                        )
                    )
                }
            }
        } catch (_: Exception) {
            toast("خواندن کتابخانه موسیقی ممکن نشد.")
        }
        renderTracks()
    }

    private fun clearPage() {
        page.removeAllViews()
    }

    private fun header(title: String, back: Boolean = false) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (back) row.addView(actionButton("‹", { showLibrary() }, pink), params(dp(48), dp(46)))
        val titleView = label(title, 22f, Color.WHITE, true)
        row.addView(titleView, LinearLayout.LayoutParams(0, dp(54), 1f))
        row.addView(label("AW", 22f, cyan, true).apply {
            setShadowLayer(dp(12).toFloat(), 0f, 0f, cyan)
            gravity = Gravity.CENTER
        }, params(dp(52), dp(48)))
        page.addView(row, params(bottom = 8))
    }

    private fun showLibrary() {
        screen = "library"
        clearPage()
        header("کتابخانه موسیقی")
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = shape(panel, 20, cyan)
        }
        brand.addView(label("موسیقی خودت؛ با امضای AW", 17f, Color.WHITE, true))
        brand.addView(label("یک تجربه نئونی برای مجموعه موسیقی گوشی تو", 11f, Color.LTGRAY).apply {
            setPadding(0, dp(7), 0, 0)
        })
        page.addView(brand, params(bottom = 14))

        searchBox = EditText(this).apply {
            hint = "جست‌وجوی آهنگ، خواننده یا آلبوم"
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            background = shape(Color.rgb(18, 24, 40), 14, Color.rgb(115, 65, 175))
            setPadding(dp(14), 0, dp(14), 0)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderTracks(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        page.addView(searchBox, params(-1, dp(48), 10))

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("all" to "همه آهنگ‌ها", "favorites" to "علاقه‌مندی‌ها", "recent" to "اخیر").forEach { (key, title) ->
            val tab = actionButton(title, {
                filterMode = key
                renderTracks(searchBox.text.toString())
            }, if (filterMode == key) cyan else Color.DKGRAY).apply { textSize = 12f }
            tabs.addView(tab, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(5)
            })
        }
        page.addView(tabs, params(bottom = 8))
        val toolsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        toolsRow.addView(actionButton("▶ صف پخش", { showQueue() }), LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        toolsRow.addView(actionButton("♫ پلی‌لیست‌ها", { showPlaylists() }, pink), LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
        toolsRow.addView(actionButton("مرتب‌سازی", { chooseSort() }, Color.rgb(120, 90, 190)), LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
        page.addView(toolsRow, params(bottom = 8))
        val listHeading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listHeading.addView(label("آهنگ‌ها", 16f, Color.WHITE, true), LinearLayout.LayoutParams(0, dp(42), 1f))
        countText = label("۰", 11f, cyan)
        listHeading.addView(countText)
        page.addView(listHeading)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        buildMiniPlayer()
        renderTracks()
    }

    private fun renderTracks(query: String = "") {
        if (!::list.isInitialized || screen != "library") return
        list.removeAllViews()
        val filtered = tracks.filter { track ->
            val match = track.title.contains(query, true) ||
                track.artist.contains(query, true) || track.album.contains(query, true)
            match && when (filterMode) {
                "favorites" -> favorites.contains(track.id)
                "recent" -> recent.contains(track.id)
                else -> true
            }
        }.let { items ->
            when (filterMode) {
                "recent" -> items.sortedByDescending { recent.indexOf(it.id) }
                else -> when (sortMode) {
                    "artist" -> items.sortedBy { it.artist.lowercase(Locale.ROOT) }
                    "album" -> items.sortedBy { it.album.lowercase(Locale.ROOT) }
                    "newest" -> items.sortedByDescending { it.id }
                    else -> items.sortedBy { it.title.lowercase(Locale.ROOT) }
                }
            }
        }
        countText.text = "${filtered.size} آهنگ"
        if (filtered.isEmpty()) {
            list.addView(label(
                if (tracks.isEmpty()) "هنوز آهنگی پیدا نشده؛ فایل صوتی روی گوشی داشته باش."
                else "در این بخش آهنگی وجود ندارد.",
                12f, Color.LTGRAY
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(28), dp(8), dp(28))
            })
            return
        }
        filtered.forEach { track ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = shape(Color.argb(225, 20, 29, 48), 3, if (track.id == currentTrackId) pink else Color.rgb(0, 190, 220))
                elevation = dp(3).toFloat()
            }
            val art = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = shape(Color.rgb(27, 35, 55), 2)
                setImageResource(android.R.drawable.ic_media_play)
                loadArtwork(track)?.let { setImageBitmap(it) }
                setOnClickListener { playTrack(track, true) }
            }
            card.addView(art, params(dp(58), dp(58)))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(11), 0, dp(5), 0)
                setOnClickListener { playTrack(track, true) }
            }
            info.addView(label(track.title, 13f, Color.WHITE, true).apply { maxLines = 1 })
            info.addView(label(track.artist, 10f, Color.LTGRAY).apply { maxLines = 1; setPadding(0, dp(3), 0, 0) })
            if (track.album.isNotBlank()) info.addView(label(track.album, 9f, cyan).apply { maxLines = 1 })
            card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(actionButton(if (favorites.contains(track.id)) "♥" else "♡", {
                toggleFavorite(track)
            }, pink).apply { textSize = 20f; minWidth = dp(44); minHeight = dp(44) }, params(dp(46), dp(46)))
            card.addView(actionButton("⋮", { showTrackActions(track) }, cyan).apply { minWidth = dp(40) }, params(dp(42), dp(46)))
            card.setOnClickListener { playTrack(track, true) }
            list.addView(card, params(-1, -2, 9))
        }
    }

    private fun currentTrack(): Track? = currentTrackId?.let { id -> tracks.find { it.id == id } }

    private fun loadArtwork(track: Track): Bitmap? {
        artworkCache.get(track.id)?.let { return it }
        return try {
            val albumUri = Uri.parse("content://media/external/audio/albumart/${track.albumId}")
            contentResolver.openInputStream(albumUri)?.use {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeStream(it, null, options)
            }?.also { artworkCache.put(track.id, it) }
        } catch (_: Exception) { null }
    }

    private fun buildMiniPlayer() {
        val mini = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = shape(panel, 18, pink)
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnClickListener { if (currentTrack() != null) showPlayer() }
        }
        miniTitle = label("آهنگی انتخاب نشده", 12f, Color.WHITE, true)
        miniArtist = label("ALIREZA WEB", 10f, Color.LTGRAY)
        info.addView(miniTitle)
        info.addView(miniArtist)
        mini.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        mini.addView(actionButton("⏮", { previousTrack() }), params(dp(46), dp(44)))
        mini.addView(actionButton("▶", { togglePlay() }).also { miniPlay = it }, params(dp(48), dp(44)))
        mini.addView(actionButton("⏭", { nextTrack() }), params(dp(46), dp(44)))
        page.addView(mini, params(-1, -2, 5))
    }

    private fun showPlayer() {
        val track = currentTrack()
        if (track == null) {
            toast("اول یک آهنگ انتخاب کن.")
            return
        }
        screen = "player"
        clearPage()
        header("در حال پخش", true)
        detailArt = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(panel, 3, cyan)
            loadArtwork(track)?.let { setImageBitmap(it) }
                ?: setImageResource(android.R.drawable.ic_media_play)
            elevation = dp(8).toFloat()
        }
        page.addView(detailArt, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            setMargins(dp(18), dp(12), dp(18), dp(18))
        })
        detailTitle = label(track.title, 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        }
        detailArtist = label(track.artist, 13f, Color.LTGRAY).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(14))
        }
        page.addView(detailTitle)
        page.addView(detailArtist)
        seek = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
            thumbTintList = android.content.res.ColorStateList.valueOf(pink)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, from: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    player?.let { p ->
                        if (playerPrepared && p.duration > 0) {
                            p.seekTo((p.duration.toLong() * seek.progress / 1000L).toInt())
                        }
                    }
                }
            })
        }
        page.addView(seek, params(-1, dp(42)))
        timeText = label("00:00  /  00:00", 11f, Color.LTGRAY).apply { gravity = Gravity.CENTER }
        page.addView(timeText, params(bottom = 12))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(actionButton("⤨", { toggleShuffle() }, if (shuffle) cyan else Color.DKGRAY), params(dp(52), dp(50)))
        controls.addView(actionButton("⏮", { previousTrack() }, pink), LinearLayout.LayoutParams(dp(58), dp(54)).apply { marginStart = dp(8) })
        detailPlay = actionButton(if (isPlayerPlaying()) "Ⅱ" else "▶", { togglePlay() }).apply { textSize = 22f }
        controls.addView(detailPlay, LinearLayout.LayoutParams(dp(68), dp(62)).apply { marginStart = dp(8) })
        controls.addView(actionButton("⏭", { nextTrack() }, pink), LinearLayout.LayoutParams(dp(58), dp(54)).apply { marginStart = dp(8) })
        controls.addView(actionButton(repeatIcon(), { cycleRepeat() }, if (repeatMode == 0) Color.DKGRAY else cyan), LinearLayout.LayoutParams(dp(52), dp(50)).apply { marginStart = dp(8) })
        page.addView(controls, params(bottom = 14))
        favoriteButton = actionButton(favoriteText(track), { toggleFavorite(track) }, pink).apply { textSize = 14f }
        page.addView(favoriteButton, params(-1, dp(48), 8))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton("افزودن به پلی‌لیست", { addTrackToPlaylist(track) }, pink), LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(4) })
        actions.addView(actionButton("افزودن به صف", { enqueue(track) }), LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(4) })
        page.addView(actions, params(-1, -2, 6))
        val actions2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions2.addView(actionButton("اشتراک‌گذاری", { shareTrack(track) }, Color.rgb(110, 70, 180)), LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        actions2.addView(actionButton("تایمر خواب", { chooseSleepTimer() }, Color.rgb(110, 70, 180)), LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        page.addView(actions2, params(-1, -2, 6))
        val audioTools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        audioTools.addView(actionButton("اکولایزر و باس", { showAudioEffects() }, cyan), LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        audioTools.addView(actionButton("سرعت پخش", { choosePlaybackSpeed() }, pink), LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        page.addView(audioTools, params(-1, -2, 6))
        page.addView(actionButton("صف پخش را مدیریت کن", { showQueue() }), params(-1, dp(44), 6))
        val metaActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        metaActions.addView(actionButton("اطلاعات آهنگ", { showTrackInfo(track) }, Color.rgb(110, 70, 180)), LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        metaActions.addView(actionButton("متن آهنگ / LRC", { chooseLyrics(track) }, pink), LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        page.addView(metaActions, params(-1, -2, 6))
        page.addView(actionButton("درباره سازنده  •  ALIREZA WEB", { showAbout() }, Color.rgb(110, 70, 180)), params(-1, dp(46), 4))
        updatePlayerLabels()
    }

    private fun favoriteText(track: Track) =
        if (favorites.contains(track.id)) "♥  حذف از علاقه‌مندی‌ها" else "♡  افزودن به علاقه‌مندی‌ها"

    private fun toggleFavorite(track: Track) {
        if (!favorites.add(track.id)) favorites.remove(track.id)
        saveLibrary()
        if (screen == "player" && currentTrackId == track.id && ::favoriteButton.isInitialized) {
            favoriteButton.text = favoriteText(track)
        }
        if (screen == "library") renderTracks(searchBox.text.toString())
        toast(if (favorites.contains(track.id)) "به علاقه‌مندی‌ها اضافه شد." else "از علاقه‌مندی‌ها حذف شد.")
    }

    private fun toggleShuffle() {
        shuffle = !shuffle
        toast(if (shuffle) "پخش تصادفی روشن شد" else "پخش تصادفی خاموش شد")
        if (screen == "player") showPlayer()
    }

    private fun repeatIcon() = when (repeatMode) { 1 -> "🔁"; 2 -> "🔂"; else -> "↻" }

    private fun cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3
        toast(when (repeatMode) {
            1 -> "تکرار همه آهنگ‌ها"
            2 -> "تکرار همین آهنگ"
            else -> "تکرار خاموش"
        })
        if (screen == "player") showPlayer()
    }

    private fun releaseAudioEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null; bassBoost = null; virtualizer = null
    }

    private fun attachAudioEffects(sessionId: Int) {
        releaseAudioEffects()
        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = prefs.getBoolean("eq_enabled", true)
                val bands = numberOfBands.toInt()
                val range = bandLevelRange
                for (band in 0 until bands) {
                    val saved = prefs.getInt("eq_band_$band", Int.MIN_VALUE)
                    if (saved != Int.MIN_VALUE) {
                        setBandLevel(band.toShort(), saved.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                    }
                }
            }
        } catch (_: Exception) {}
        try {
            bassBoost = BassBoost(0, sessionId).apply {
                setStrength(500.toShort())
                enabled = prefs.getBoolean("bass_enabled", true)
            }
        } catch (_: Exception) {}
        try {
            virtualizer = Virtualizer(0, sessionId).apply {
                setStrength(350.toShort())
                enabled = prefs.getBoolean("virt_enabled", true)
            }
        } catch (_: Exception) {}
    }

    private fun playTrack(track: Track, openDetails: Boolean = false) {
        try {
            player?.setOnPreparedListener(null)
            player?.release()
            playerPrepared = false
            currentTrackId = track.id
            val newPlayer = MediaPlayer()
            player = newPlayer
            newPlayer.setDataSource(this, track.uri)
            newPlayer.setOnPreparedListener { mp ->
                try {
                    playerPrepared = true
                    attachAudioEffects(mp.audioSessionId)
                    if (Build.VERSION.SDK_INT >= 23 && playbackSpeed != 1.0f) {
                        mp.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
                    }
                    mp.start()
                    updatePlayerLabels()
                    if (openDetails) showPlayer()
                } catch (_: Exception) { toast("شروع پخش ممکن نشد.") }
            }
            newPlayer.setOnCompletionListener {
                when (repeatMode) {
                    2 -> {
                        try { player?.seekTo(0); player?.start() } catch (_: Exception) {}
                    }
                    else -> nextTrack()
                }
            }
            newPlayer.setOnErrorListener { _, _, _ -> toast("خطا در خواندن فایل صوتی."); true }
            newPlayer.prepareAsync()
            recent.remove(track.id)
            recent.add(0, track.id)
            if (recent.size > 100) recent.removeAt(recent.lastIndex)
            saveLibrary()
            updatePlayerLabels()
            if (!openDetails && screen == "library") renderTracks(searchBox.text.toString())
        } catch (_: Exception) {
            toast("پخش این فایل ممکن نیست.")
        }
    }

    private fun updatePlayerLabels() {
        val track = currentTrack() ?: return
        if (::miniTitle.isInitialized) {
            miniTitle.text = track.title
            miniArtist.text = track.artist
            miniPlay.text = if (isPlayerPlaying()) "Ⅱ" else "▶"
        }
        if (screen == "player" && ::detailTitle.isInitialized) {
            detailTitle.text = track.title
            detailArtist.text = track.artist
            detailPlay.text = if (isPlayerPlaying()) "Ⅱ" else "▶"
        }
    }

    private fun isPlayerPlaying(): Boolean = try { playerPrepared && player?.isPlaying == true } catch (_: Exception) { false }

    private fun togglePlay() {
        val p = player
        if (p == null) {
            if (tracks.isNotEmpty()) playTrack(tracks.first(), screen == "player")
            else toast("موسیقی‌ای برای پخش پیدا نشد.")
            return
        }
        if (!playerPrepared) { toast("آهنگ در حال آماده‌سازی است."); return }
        try {
            if (p.isPlaying) p.pause() else p.start()
            updatePlayerLabels()
        } catch (_: Exception) { toast("پخش‌کننده آماده نیست.") }
    }

    private fun nextTrack() {
        if (tracks.isEmpty()) return
        val queued = playQueue.mapNotNull { id -> tracks.find { it.id == id } }
        val queueIndex = queued.indexOfFirst { it.id == currentTrackId }
        val nextQueued = when {
            queued.isEmpty() -> null
            shuffle && queued.size > 1 -> queued.filter { it.id != currentTrackId }.randomOrNull()
            queueIndex >= 0 && queueIndex + 1 < queued.size -> queued[queueIndex + 1]
            queueIndex < 0 -> queued.first()
            else -> null
        }
        if (nextQueued != null) { playTrack(nextQueued, screen == "player"); return }

        val currentIndex = tracks.indexOfFirst { it.id == currentTrackId }
        val next = when {
            shuffle && tracks.size > 1 -> tracks.indices.filter { it != currentIndex }.random()
            currentIndex >= 0 && currentIndex + 1 < tracks.size -> currentIndex + 1
            repeatMode == 1 -> 0
            else -> {
                try { player?.pause() } catch (_: Exception) {}
                updatePlayerLabels()
                return
            }
        }
        playTrack(tracks[next], screen == "player")
    }

    private fun previousTrack() {
        if (tracks.isEmpty()) return
        val p = player
        if (p != null && playerPrepared && p.currentPosition > 3000) { p.seekTo(0); return }
        val queued = playQueue.mapNotNull { id -> tracks.find { it.id == id } }
        val queueIndex = queued.indexOfFirst { it.id == currentTrackId }
        if (queueIndex > 0) { playTrack(queued[queueIndex - 1], screen == "player"); return }
        val currentIndex = tracks.indexOfFirst { it.id == currentTrackId }
        val previous = when {
            currentIndex > 0 -> currentIndex - 1
            repeatMode == 1 -> tracks.lastIndex
            else -> 0
        }
        playTrack(tracks[previous], screen == "player")
    }

    private fun saveLibrary() {
        val playlistData = playlists.entries.joinToString("\n") { (name, ids) -> "$name::${ids.joinToString(",")}" }
        prefs.edit()
            .putStringSet("favorites", favorites.map { it.toString() }.toSet())
            .putString("recent", recent.joinToString(","))
            .putString("playlists", playlistData)
            .putString("queue", playQueue.joinToString(","))
            .apply()
    }

    private fun chooseSort() {
        val options = arrayOf("نام آهنگ", "خواننده", "آلبوم", "جدیدترین")
        android.app.AlertDialog.Builder(this).setTitle("مرتب‌سازی آهنگ‌ها").setItems(options) { _, which ->
            sortMode = listOf("title", "artist", "album", "newest")[which]
            if (screen == "library") renderTracks(searchBox.text.toString())
        }.show()
    }

    private fun showTrackActions(track: Track) {
        val options = arrayOf("پخش", "افزودن به صف پخش", "افزودن به پلی‌لیست", "اشتراک‌گذاری", "اطلاعات آهنگ")
        android.app.AlertDialog.Builder(this).setTitle(track.title).setItems(options) { _, which ->
            when (which) {
                0 -> playTrack(track, true)
                1 -> enqueue(track)
                2 -> addTrackToPlaylist(track)
                3 -> shareTrack(track)
                4 -> showTrackInfo(track)
            }
        }.show()
    }

    private val lyricsPickerCode = 71
    private var lyricsTargetTrackId: Long = -1L

    private fun chooseLyrics(track: Track) {
        (lyricsByTrack[track.id] ?: prefs.getString("lyrics_${track.id}", null))?.let {
            lyricsByTrack[track.id] = it
            showLyrics(track, it)
            return
        }
        lyricsTargetTrackId = track.id
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        try { startActivityForResult(intent, lyricsPickerCode) }
        catch (_: Exception) { toast("انتخاب فایل متن آهنگ ممکن نیست.") }
    }

    @Deprecated("Deprecated by Android, retained for compatibility with minSdk 23")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == lyricsPickerCode && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            try {
                try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (text.isBlank()) toast("فایل متن خالی است.") else {
                    lyricsByTrack[lyricsTargetTrackId] = text
                    prefs.edit().putString("lyrics_$lyricsTargetTrackId", text).apply()
                    tracks.find { it.id == lyricsTargetTrackId }?.let { showLyrics(it, text) }
                }
            } catch (_: Exception) { toast("خواندن فایل متن آهنگ ممکن نشد.") }
        }
    }

    private fun showLyrics(track: Track, lyrics: String) {
        val body = TextView(this).apply {
            text = lyrics
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setLineSpacing(dp(7).toFloat(), 1f)
            gravity = Gravity.CENTER
        }
        val scroll = ScrollView(this).apply { addView(body) }
        android.app.AlertDialog.Builder(this).setTitle("متن آهنگ • ${track.title}")
            .setView(scroll).setPositiveButton("بستن", null)
            .setNeutralButton("انتخاب فایل دیگر") { _, _ -> chooseLyrics(track) }.show()
    }

    private fun showTrackInfo(track: Track) {
        val details = try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, track.uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.toInt() ?: 0
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "نامشخص"
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { "${it / 1000} kbps" } ?: "نامشخص"
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: "نامشخص"
            retriever.release()
            "مدت: ${if (duration > 0) fmt(duration) else "نامشخص"}\nفرمت: $mime\nبیت‌ریت: $bitrate\nسال: $year"
        } catch (_: Exception) { "مشخصات فنی در دسترس نیست." }
        android.app.AlertDialog.Builder(this).setTitle("اطلاعات آهنگ").setMessage(
            "نام: ${track.title}\nخواننده: ${track.artist}\nآلبوم: ${track.album.ifBlank { "نامشخص" }}\n$details\nشناسه فایل: ${track.id}"
        ).setPositiveButton("باشه", null).show()
    }

    private fun choosePlaybackSpeed() {
        val values = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val labels = values.map { "${it}×" }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("سرعت پخش").setSingleChoiceItems(labels, (values.indices.firstOrNull { values[it] == playbackSpeed } ?: 2)) { dialog, which ->
            playbackSpeed = values[which]
            try {
                if (Build.VERSION.SDK_INT >= 23) player?.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
                prefs.edit().putFloat("playback_speed", playbackSpeed).apply()
                toast("سرعت روی ${playbackSpeed}× تنظیم شد.")
            } catch (_: Exception) { toast("تغییر سرعت در این فایل پشتیبانی نمی‌شود.") }
            dialog.dismiss()
        }.setNegativeButton("بستن", null).show()
    }

    private fun showAudioEffects() {
        val eq = equalizer
        if (eq == null) { toast("ابتدا یک آهنگ پخش کن تا افکت‌های صوتی فعال شوند."); return }
        val panelView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val enabled = CheckBox(this).apply {
            text = "فعال‌سازی اکولایزر"
            setTextColor(Color.WHITE)
            isChecked = eq.enabled
            setOnCheckedChangeListener { _, checked ->
                eq.enabled = checked
                prefs.edit().putBoolean("eq_enabled", checked).apply()
            }
        }
        panelView.addView(enabled)
        val bands = try { eq.numberOfBands.toInt() } catch (_: Exception) { 0 }
        for (band in 0 until bands) {
            val range = eq.bandLevelRange
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val freq = eq.getCenterFreq(band.toShort()) / 1000
            row.addView(label("${freq} kHz", 11f, Color.LTGRAY))
            val bar = SeekBar(this).apply {
                max = (range[1] - range[0]).toInt()
                progress = (eq.getBandLevel(band.toShort()) - range[0]).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(cyan)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) try {
                            val value = progress + range[0]
                            eq.setBandLevel(band.toShort(), value.toShort())
                            prefs.edit().putInt("eq_band_$band", value.toInt()).apply()
                        } catch (_: Exception) {}
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            row.addView(bar); panelView.addView(row)
        }
        val bass = CheckBox(this).apply {
            text = "Bass Booster"
            setTextColor(Color.WHITE)
            isChecked = bassBoost?.enabled == true
            setOnCheckedChangeListener { _, checked ->
                try {
                    bassBoost?.enabled = checked
                    prefs.edit().putBoolean("bass_enabled", checked).apply()
                } catch (_: Exception) {}
            }
        }
        val virtual = CheckBox(this).apply {
            text = "Virtualizer"
            setTextColor(Color.WHITE)
            isChecked = virtualizer?.enabled == true
            setOnCheckedChangeListener { _, checked ->
                try {
                    virtualizer?.enabled = checked
                    prefs.edit().putBoolean("virt_enabled", checked).apply()
                } catch (_: Exception) {}
            }
        }
        panelView.addView(bass); panelView.addView(virtual)
        android.app.AlertDialog.Builder(this).setTitle("تنظیمات صدا").setView(panelView).setPositiveButton("تمام", null).show()
    }

    private fun shareTrack(track: Track) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "audio/*"; putExtra(android.content.Intent.EXTRA_STREAM, track.uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "اشتراک‌گذاری آهنگ"))
        } catch (_: Exception) { toast("اشتراک‌گذاری این آهنگ ممکن نیست.") }
    }

    private fun enqueue(track: Track) {
        playQueue.add(track.id); saveLibrary(); toast("به صف پخش اضافه شد.")
    }

    private fun showQueue() {
        screen = "queue"; clearPage(); header("صف پخش", true)
        val scroll = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val valid = playQueue.mapNotNull { id -> tracks.find { it.id == id } }
        if (valid.isEmpty()) box.addView(label("صف پخش خالی است.", 13f, Color.LTGRAY).apply { gravity = Gravity.CENTER; setPadding(0, dp(30), 0, dp(30)) })
        valid.forEachIndexed { index, track ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(panel, 14, cyan); setPadding(dp(8), dp(6), dp(8), dp(6)) }
            row.addView(label("${index + 1}. ${track.title}\n${track.artist}", 12f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(actionButton("↑", { if (index > 0) { val item = playQueue.removeAt(index); playQueue.add(index - 1, item); saveLibrary(); showQueue() } }), params(dp(40), dp(42)))
            row.addView(actionButton("↓", { if (index < playQueue.lastIndex) { val item = playQueue.removeAt(index); playQueue.add(index + 1, item); saveLibrary(); showQueue() } }, pink), params(dp(40), dp(42)))
            row.addView(actionButton("▶", { playTrack(track, true) }), params(dp(42), dp(42)))
            row.addView(actionButton("×", { playQueue.removeAt(index); saveLibrary(); showQueue() }, pink), params(dp(40), dp(42)))
            box.addView(row, params(-1, -2, 7))
        }
        box.addView(actionButton("پاک کردن کل صف", { playQueue.clear(); saveLibrary(); showQueue() }, pink), params(-1, dp(46), 12))
        scroll.addView(box); page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun showPlaylists() {
        screen = "playlists"; clearPage(); header("پلی‌لیست‌های من", true)
        page.addView(actionButton("＋ ساخت پلی‌لیست جدید", { createPlaylist() }, pink), params(-1, dp(48), 10))
        val scroll = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (playlists.isEmpty()) box.addView(label("هنوز پلی‌لیستی نساختی.", 13f, Color.LTGRAY).apply { gravity = Gravity.CENTER; setPadding(0, dp(28), 0, dp(28)) })
        playlists.forEach { (name, ids) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(panel, 15, cyan); setPadding(dp(10), dp(8), dp(10), dp(8)) }
            row.addView(label("$name\n${ids.size} آهنگ", 13f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(actionButton("▶", { playPlaylist(name) }), params(dp(44), dp(42)))
            row.addView(actionButton("⋮", { playlistMenu(name) }, pink), params(dp(44), dp(42)))
            row.setOnClickListener { showPlaylist(name) }; box.addView(row, params(-1, -2, 8))
        }
        scroll.addView(box); page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun createPlaylist() {
        val input = EditText(this).apply { hint = "نام پلی‌لیست"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        android.app.AlertDialog.Builder(this).setTitle("ساخت پلی‌لیست").setView(input)
            .setPositiveButton("ساخت") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank() && !playlists.containsKey(name)) { playlists[name] = mutableListOf(); saveLibrary(); showPlaylists() }
                else toast("نام خالی یا تکراری است.")
            }.setNegativeButton("لغو", null).show()
    }

    private fun addTrackToPlaylist(track: Track) {
        if (playlists.isEmpty()) { createPlaylist(); toast("ابتدا پلی‌لیست بساز؛ سپس دوباره آهنگ را اضافه کن."); return }
        val names = playlists.keys.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("افزودن به کدام پلی‌لیست؟").setItems(names) { _, which ->
            val ids = playlists[names[which]]!!; if (!ids.contains(track.id)) ids.add(track.id)
            saveLibrary(); toast("به ${names[which]} اضافه شد.")
        }.show()
    }

    private fun showPlaylist(name: String) {
        screen = "playlist"; clearPage(); header(name, true)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val ids = playlists[name] ?: mutableListOf()
        ids.toList().forEach { id -> tracks.find { it.id == id }?.let { track ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(panel, 14, cyan); setPadding(dp(8), dp(6), dp(8), dp(6)) }
            row.addView(label("${track.title}\n${track.artist}", 12f, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(actionButton("▶", { playTrack(track, true) }), params(dp(44), dp(42)))
            row.addView(actionButton("×", { ids.remove(id); saveLibrary(); showPlaylist(name) }, pink), params(dp(42), dp(42)))
            box.addView(row, params(-1, -2, 7))
        } }
        page.addView(actionButton("پخش تصادفی این پلی‌لیست", { playPlaylist(name, true) }), params(-1, dp(46), 8))
        val scroll = ScrollView(this); scroll.addView(box); page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun playPlaylist(name: String, random: Boolean = false) {
        val items = (playlists[name] ?: mutableListOf()).mapNotNull { id -> tracks.find { it.id == id } }
        if (items.isEmpty()) { toast("این پلی‌لیست آهنگی ندارد."); return }
        playQueue.clear(); playQueue.addAll(items.map { it.id }); if (random) playQueue.shuffle(); saveLibrary()
        playTrack(items.firstOrNull { it.id == playQueue.first() } ?: items.first(), true)
    }

    private fun playlistMenu(name: String) {
        val opts = arrayOf("تغییر نام", "حذف پلی‌لیست")
        android.app.AlertDialog.Builder(this).setTitle(name).setItems(opts) { _, which ->
            if (which == 0) {
                val input = EditText(this).apply { setText(name); setTextColor(Color.WHITE) }
                android.app.AlertDialog.Builder(this).setTitle("تغییر نام").setView(input).setPositiveButton("ذخیره") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotBlank() && (newName == name || !playlists.containsKey(newName))) {
                        playlists[newName] = playlists.remove(name) ?: mutableListOf()
                        saveLibrary(); showPlaylists()
                    }
                }.setNegativeButton("لغو", null).show()
            } else android.app.AlertDialog.Builder(this).setMessage("این پلی‌لیست حذف شود؟").setPositiveButton("حذف") { _, _ -> playlists.remove(name); saveLibrary(); showPlaylists() }.setNegativeButton("لغو", null).show()
        }.show()
    }

    private fun chooseSleepTimer() {
        val options = arrayOf("۵ دقیقه", "۱۰ دقیقه", "۱۵ دقیقه", "۳۰ دقیقه", "۶۰ دقیقه", "لغو تایمر")
        android.app.AlertDialog.Builder(this).setTitle("توقف خودکار پخش").setItems(options) { _, which ->
            sleepRunnable?.let { handler.removeCallbacks(it) }; sleepRunnable = null
            if (which == 5) toast("تایمر لغو شد.") else {
                val minutes = listOf(5, 10, 15, 30, 60)[which]
                sleepRunnable = Runnable { player?.pause(); updatePlayerLabels(); toast("تایمر پایان یافت.") }
                handler.postDelayed(sleepRunnable!!, minutes * 60_000L); toast("پخش پس از $minutes دقیقه متوقف می‌شود.")
            }
        }.show()
    }

    private fun showAbout() {
        screen = "about"
        clearPage()
        header("درباره سازنده", true)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(18), dp(8), dp(18))
        }
        body.addView(label("A W", 54f, cyan, true).apply {
            gravity = Gravity.CENTER
            setShadowLayer(dp(22).toFloat(), 0f, 0f, cyan)
        }, params(-1, dp(90)))
        body.addView(label("ALIREZA WEB", 18f, pink, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = .15f
        }, params(bottom = 18))
        val aboutCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = shape(panel, 20, cyan)
        }
        aboutCard.addView(label("درباره سازنده", 17f, Color.WHITE, true))
        aboutCard.addView(label(
            "AW MUSIC محصولی از مجموعه ALIREZA WEB است؛ پروژه‌ای مستقل با تمرکز بر تجربه ساده، مدرن و چشم‌نواز برای شنیدن موسیقی‌های ذخیره‌شده روی دستگاه.",
            13f, Color.LTGRAY
        ).apply { setPadding(0, dp(10), 0, dp(16)); setLineSpacing(dp(4).toFloat(), 1f) })
        aboutCard.addView(label("طراحی و توسعه", 12f, cyan, true))
        aboutCard.addView(label("علیرضا مزارعی", 16f, Color.WHITE, true).apply { setPadding(0, dp(4), 0, dp(14)) })
        aboutCard.addView(label("نسخه برنامه", 11f, cyan, true))
        aboutCard.addView(label("AW MUSIC 4.1 • ULTIMATE", 13f, Color.WHITE).apply { setPadding(0, dp(4), 0, dp(14)) })
        aboutCard.addView(actionButton("وب‌سایت رسمی  •  alireza-mazarei.ir", {
            openExternal("https://alireza-mazarei.ir/")
        }), params(-1, dp(48), 9))
        aboutCard.addView(actionButton("اینستاگرام  •  @alireza_web_office", {
            openExternal("https://www.instagram.com/alireza_web_office/")
        }, pink), params(-1, dp(48)))
        body.addView(aboutCard, params(-1, -2, 18))
        body.addView(label("MADE WITH PASSION  •  ALIREZA WEB", 10f, Color.GRAY).apply { gravity = Gravity.CENTER })
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun openExternal(url: String) {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { toast("امکان باز کردن پیوند وجود ندارد.") }
    }

    private fun startTicker() {
        handler.post(object : Runnable {
            override fun run() {
                val p = player
                if (p != null) {
                    try {
                        if (screen == "player" && p.duration > 0 && ::seek.isInitialized && ::timeText.isInitialized) {
                            seek.progress = (p.currentPosition * 1000L / p.duration).toInt()
                            timeText.text = "${fmt(p.currentPosition)}  /  ${fmt(p.duration)}"
                        }
                        updatePlayerLabels()
                    } catch (_: Exception) {}
                }
                handler.postDelayed(this, 700)
            }
        })
    }

    private fun fmt(ms: Int) = String.format(Locale.US, "%02d:%02d", ms / 60000, (ms / 1000) % 60)
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onBackPressed() {
        when (screen) {
            "player", "about", "queue", "playlists" -> showLibrary()
            "playlist" -> showPlaylists()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        playerPrepared = false
        releaseAudioEffects()
        sleepRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}
