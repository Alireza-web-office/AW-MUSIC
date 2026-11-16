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
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val enabled = CheckBox(this).apply { text = "فعال‌سازی اکولایزر"; setTextColor(Color.WHITE); isChecked = eq.enabled; setOnCheckedChangeListener { _, checked -> eq.enabled = checked } }
        panel.addView(enabled)
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
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) try { eq.setBandLevel(band.toShort(), (progress + range[0]).toShort()) } catch (_: Exception) {} }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            row.addView(bar); panel.addView(row)
        }
        val bass = CheckBox(this).apply { text = "Bass Booster"; setTextColor(Color.WHITE); isChecked = bassBoost?.enabled == true; setOnCheckedChangeListener { _, checked -> try { bassBoost?.enabled = checked } catch (_: Exception) {} } }
        val virtual = CheckBox(this).apply { text = "Virtualizer"; setTextColor(Color.WHITE); isChecked = virtualizer?.enabled == true; setOnCheckedChangeListener { _, checked -> try { virtualizer?.enabled = checked } catch (_: Exception) {} } }
        panel.addView(bass); panel.addView(virtual)
        android.app.AlertDialog.Builder(this).setTitle("تنظیمات صدا").setView(panel).setPositiveButton("تمام", null).show()
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
                    val newName = input.text.toString().trim(); if (newName.isNotBlank() && (newName == name || !playlists.containsKey(newName))) { playlists[newName] = playlists.remove(name) ?: mutableListOf(); saveLibrary(); showPlaylists() }
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
        aboutCard.addView(label("AW MUSIC 4.0 • ULTIMATE", 13f, Color.WHITE).apply { setPadding(0, dp(4), 0, dp(14)) })
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
