package ir.alirezaweb.awmusic

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import android.animation.ValueAnimator

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri
)

class MainActivity : Activity() {

    private val bg = Color.rgb(9, 9, 16)
    private val panel = Color.rgb(22, 22, 35)
    private val cyan = Color.rgb(0, 220, 255)
    private val purple = Color.rgb(170, 70, 255)
    private val white = Color.WHITE
    private val muted = Color.LTGRAY

    private val tracks = mutableListOf<Track>()
    private val favorites = mutableSetOf<Long>()
    private val recent = mutableListOf<Long>()

    private var player: MediaPlayer? = null
    private var currentTrack: Track? = null
    private var currentTab = "all"
    private var english = false
    private var shuffle = false
    private var repeat = false
    private var isPlaying = false

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var miniTitle: TextView
    private lateinit var playButton: Button
    private lateinit var progress: SeekBar
    private lateinit var elapsed: TextView
    private lateinit var total: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && isPlaying) {
                try {
                    progress.progress = p.currentPosition
                    elapsed.text = format(p.currentPosition.toLong())
                } catch (_: Exception) {
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("aw_music", MODE_PRIVATE)
        english = prefs.getBoolean("english", false)

        favorites.addAll(
            prefs.getStringSet("favorites", emptySet())?.mapNotNull {
                it.toLongOrNull()
            } ?: emptyList()
        )

        buildUi()
        askPermission()
        handler.post(progressRunnable)
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }

        val scroll = ScrollView(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(panel, 18)
        }

        val logo = TextView(this).apply {
            text = "AW"
            textSize = 27f
            setTextColor(cyan)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = rounded(Color.BLACK, 14)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        brand.addView(label("AW MUSIC", 20f, white, true))
        brand.addView(label("ALIREZA WEB", 11f, muted))

        header.addView(logo)
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        val language = button(if (english) "FA" else "EN")

        language.setOnClickListener {
            english = !english

            getSharedPreferences("aw_music", MODE_PRIVATE)
                .edit()
                .putBoolean("english", english)
                .apply()

            buildUi()
        }

        header.addView(language)
        content.addView(header)

        ValueAnimator.ofArgb(
            cyan,
            purple,
            Color.rgb(255, 50, 160),
            cyan
        ).apply {
            duration = 3500
            repeatCount = ValueAnimator.INFINITE

            addUpdateListener {
                logo.setTextColor(it.animatedValue as Int)
            }

            start()
        }

        content.addView(space(18))

        search = EditText(this).apply {
            hint = if (english) "Search music..." else "جستجوی آهنگ..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            isSingleLine = true
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = rounded(panel, 14)
        }

        content.addView(search)
        content.addView(space(15))

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val tabNames = listOf(
            "all" to if (english) "All" else "همه آهنگ‌ها",
            "favorites" to if (english) "Favorites" else "علاقه‌مندی",
            "recent" to if (english) "Recent" else "اخیر",
            "about" to if (english) "About" else "درباره من"
        )

        tabNames.forEach { item ->
            val b = button(item.second)
            b.textSize = 11f

            b.setOnClickListener {
                currentTab = item.first
                render()
            }

            tabs.addView(
                b,
                LinearLayout.LayoutParams(0, dp(43), 1f)
            )
        }

        content.addView(tabs)
        content.addView(space(18))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                render()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        val mini = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(panel, 16)
        }

        miniTitle = label(
            if (english) "No music playing" else "آهنگی در حال پخش نیست",
            13f,
            white
        )

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val previous = button("⏮")
        playButton = button("▶")
        val next = button("⏭")

        previous.setOnClickListener { step(-1) }
        playButton.setOnClickListener { toggle() }
        next.setOnClickListener { step(1) }

        controls.addView(previous)
        controls.addView(playButton)
        controls.addView(next)

        mini.addView(miniTitle)
        mini.addView(controls)

        progress = SeekBar(this).apply {
            max = 100
            progressTintList =
                android.content.res.ColorStateList.valueOf(cyan)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(purple)
        }

        elapsed = label("0:00", 11f, muted)
        total = label("0:00", 11f, muted)

        progress.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    value: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        try {
                            player?.seekTo(value)
                        } catch (_: Exception) {}
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        val timeRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        timeRow.addView(elapsed)

        timeRow.addView(
            View(this),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        timeRow.addView(total)

        mini.addView(progress)
        mini.addView(timeRow)

        root.addView(mini)

        setContentView(root)
        render()
    }

    private fun askPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (
            Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(permission), 100)
        } else {
            loadTracks()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            loadTracks()
        } else {
            toast(
                if (english) "Audio permission required"
                else "برای نمایش آهنگ‌ها باید دسترسی بدهی"
            )
        }
    }

    private fun loadTracks() {
        tracks.clear()

        try {
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->

                val idCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID
                )

                val titleCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE
                )

                val artistCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST
                )

                val durationCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DURATION
                )

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)

                    tracks.add(
                        Track(
                            id,
                            cursor.getString(titleCol) ?: "Unknown",
                            cursor.getString(artistCol) ?: "Unknown",
                            cursor.getLong(durationCol),
                            Uri.withAppendedPath(collection, id.toString())
                        )
                    )
                }
            }

        } catch (e: Exception) {
            toast("Error: ${e.message}")
        }

        render()
    }

    private fun render() {
        if (!::content.isInitialized) return

        val startIndex = 3

        while (content.childCount > startIndex) {
            content.removeViewAt(startIndex)
        }

        if (currentTab == "about") {
            showAbout()
            return
        }

        val query = search.text.toString().lowercase()

        val filtered = when (currentTab) {
            "favorites" -> tracks.filter {
                favorites.contains(it.id)
            }

            "recent" -> recent.mapNotNull { id ->
                tracks.find { it.id == id }
            }

            else -> tracks
        }.filter {
            it.title.lowercase().contains(query) ||
            it.artist.lowercase().contains(query)
        }

        val heading = when (currentTab) {
            "favorites" ->
                if (english) "Favorites" else "علاقه‌مندی‌ها"

            "recent" ->
                if (english) "Recently Played" else "پخش‌های اخیر"

            else ->
                if (english) "All Music" else "تمام آهنگ‌ها"
        }

        content.addView(label(heading, 20f, cyan, true))
        content.addView(space(12))

        if (filtered.isEmpty()) {
            content.addView(
                label(
                    if (english) "No music found" else "آهنگی پیدا نشد",
                    14f,
                    muted
                )
            )
            return
        }

        filtered.forEach { track ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(8), dp(12))
                background = rounded(panel, 14)
            }

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            info.addView(label(track.title, 14f, white, true))
            info.addView(label(track.artist, 11f, muted))
            info.setOnClickListener { play(track) }

            row.addView(
                info,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            val favorite = button(
                if (favorites.contains(track.id)) "♥" else "♡"
            )

            favorite.setTextColor(
                if (favorites.contains(track.id)) purple else white
            )

            favorite.setOnClickListener {
                toggleFavorite(track)
            }

            row.addView(favorite)
            row.setOnClickListener { play(track) }

            content.addView(row)
            content.addView(space(7))
        }
    }

    private fun showAbout() {
        content.addView(
            label(
                if (english) "About AW MUSIC" else "درباره AW MUSIC",
                21f,
                cyan,
                true
            )
        )

        content.addView(space(15))

        val about = if (english) {
            """
                AW MUSIC

                A music player created by Alireza Mazarei.

                Developer: Alireza Mazarei
                Brand: ALIREZA WEB

                Interested in web design, technology
                and software development.

                Designed with passion for music
                and a neon RGB visual style.
            """.trimIndent()
        } else {
            """
                AW MUSIC

                یک موزیک‌پلیر با طراحی اختصاصی.

                سازنده: علیرضا مزارعی

                برند: ALIREZA WEB

                علاقه‌مند به طراحی وب، فناوری
                و توسعه نرم‌افزار.

                ساخته‌شده با تمرکز بر تجربه
                موسیقی و طراحی نئونی RGB.
            """.trimIndent()
        }

        val card = TextView(this).apply {
            text = about
            textSize = 15f
            setTextColor(white)
            setLineSpacing(dp(7).toFloat(), 1f)
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = rounded(panel, 18)
        }

        content.addView(card)
    }

    private fun play(track: Track) {
        try {
            player?.release()
            player = MediaPlayer()

            currentTrack = track
            isPlaying = false

            miniTitle.text = "${track.title} - ${track.artist}"
            total.text = format(track.duration)
            elapsed.text = "0:00"

            player?.setDataSource(this, track.uri)

            player?.setOnPreparedListener { mp ->
                mp.start()
                isPlaying = true
                playButton.text = "Ⅱ"
                progress.max = mp.duration
            }

            player?.setOnCompletionListener {
                isPlaying = false
                playButton.text = "▶"

                if (repeat) {
                    play(track)
                } else {
                    step(1)
                }
            }

            player?.prepareAsync()

            recent.remove(track.id)
            recent.add(0, track.id)

            render()

        } catch (e: Exception) {
            toast("Playback error")
        }
    }

    private fun toggle() {
        val p = player

        if (p == null) {
            toast(
                if (english) "Select a song first"
                else "ابتدا یک آهنگ انتخاب کن"
            )
            return
        }

        try {
            if (p.isPlaying) {
                p.pause()
                isPlaying = false
                playButton.text = "▶"
            } else {
                p.start()
                isPlaying = true
                playButton.text = "Ⅱ"
            }
        } catch (_: Exception) {
        }
    }

    private fun step(direction: Int) {
        if (tracks.isEmpty()) return

        val currentIndex = tracks.indexOfFirst {
            it.id == currentTrack?.id
        }

        val nextIndex = if (currentIndex < 0) {
            0
        } else if (shuffle) {
            tracks.indices.random()
        } else {
            (currentIndex + direction + tracks.size) % tracks.size
        }

        play(tracks[nextIndex])
    }

    private fun toggleFavorite(track: Track) {
        if (favorites.contains(track.id)) {
            favorites.remove(track.id)
        } else {
            favorites.add(track.id)
        }

        getSharedPreferences("aw_music", MODE_PRIVATE)
            .edit()
            .putStringSet(
                "favorites",
                favorites.map { it.toString() }.toSet()
            )
            .apply()

        render()
    }

    private fun format(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()

        return String.format(
            "%d:%02d",
            seconds / 60,
            seconds % 60
        )
    }

    private fun label(
        textValue: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun button(textValue: String): Button {
        return Button(this).apply {
            text = textValue
            textSize = 12f
            setTextColor(cyan)
            isAllCaps = false
            background = rounded(panel, 12)
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(height)
            )
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        player?.release()
        player = null
        super.onDestroy()
    }
}
