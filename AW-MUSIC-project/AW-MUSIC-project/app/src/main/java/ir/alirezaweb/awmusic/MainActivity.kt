package ir.alirezaweb.awmusic
import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import java.util.Locale

data class Track(val id: Long, val title: String, val artist: String, val duration: Long, val uri: Uri)

class MainActivity : Activity() {
 private val tracks = mutableListOf<Track>()
 private var shown = listOf<Track>()
 private var player: MediaPlayer? = null
 private var current = -1
 private lateinit var rows: LinearLayout
 private lateinit var nowTitle: TextView
 private lateinit var nowArtist: TextView
 private lateinit var playButton: Button
 private lateinit var count: TextView
 private val bg = Color.rgb(7,11,19)
 private val panel = Color.rgb(17,24,39)
 private val cyan = Color.rgb(0,217,255)
 private val pale = Color.rgb(240,245,255)
 private val muted = Color.rgb(155,169,190)

 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); ui(); permission() }
 private fun ui() {
  val root = LinearLayout(this).apply { orientation=1; setPadding(16,20,16,12); setBackgroundColor(bg) }
  val scroll = ScrollView(this)
  val body = LinearLayout(this).apply { orientation=1 }
  scroll.addView(body)
  body.addView(TextView(this).apply { text="AW MUSIC"; textSize=29f; setTextColor(cyan); typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; letterSpacing=.12f })
  body.addView(TextView(this).apply { text="ALIREZA WEB  •  OFFLINE PLAYER"; textSize=10f; setTextColor(muted); gravity=Gravity.CENTER; setPadding(0,5,0,18) })
  val search = EditText(this).apply { hint="جست‌وجوی آهنگ یا خواننده"; setTextColor(pale); setHintTextColor(muted); issingleLine=true; setPadding(14,8,14,8); setBackgroundColor(panel) }
  body.addView(search, LinearLayout.LayoutParams(-1,dp(48)).apply { bottomMargin=dp(14) })
  count = TextView(this).apply { text="آهنگ‌های دستگاه"; textSize=15f; setTextColor(pale); typeface=Typeface.DEFAULT_BOLD; setPadding(2,4,2,10) }
  body.addView(count)
  rows = LinearLayout(this).apply { orientation=1 }; body.addView(rows)
  val bottom = LinearLayout(this).apply { orientation=1; gravity=Gravity.CENTER; setPadding(12,10,12,8); setBackgroundColor(panel) }
  nowTitle = TextView(this).apply { text="هنوز آهنگی انتخاب نشده"; textSize=15f; setTextColor(pale); typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER }
  nowArtist = TextView(this).apply { text="یک آهنگ از فهرست انتخاب کن"; textSize=12f; setTextColor(muted); gravity=Gravity.CENTER; setPadding(0,4,0,6) }
  val controls = LinearLayout(this).apply { gravity=Gravity.CENTER }
  val prev = button("|◀"); playButton=button("▶"); val next=button("▶|")
  controls.addView(prev); controls.addView(playButton); controls.addView(next)
  prev.setOnClickListener { step(-1) }; next.setOnClickListener { step(1) }; playButton.setOnClickListener { toggle() }
  bottom.addView(nowTitle); bottom.addView(nowArtist); bottom.addView(controls)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); root.addView(bottom); setContentView(root)
  search.addTextChangedListener(object: TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
    val q=s?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
    shown=tracks.filter { it.title.lowercase(Locale.getDefault()).contains(q) || it.artist.lowercase(Locale.getDefault()).contains(q) }; render()
   }
   override fun afterTextChanged(s:Editable?) {}
  })
 }
 private fun permission() {
  val p=if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
  if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(p),41) else load()
 }
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray) { super.onRequestPermissionsResult(r,p,g); if(r==41 && g.isNotEmpty() && g[0]==PackageManager.PERMISSION_GRANTED) load() else Toast.makeText(this,"برای نمایش موسیقی‌ها اجازه دسترسی لازم است.",Toast.LENGTH_LONG).show() }
 private fun load() {
  tracks.clear()
  val collection=if(Build.VERSION.SDK_INT>=29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  val cols=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION)
  try {
   contentResolver.query(collection,cols,"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,"${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
    val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val t=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val a=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val d=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
    while(c.moveToNext()) { val key=c.getLong(id); tracks.add(Track(key,c.getString(t)?:"بدون عنوان",c.getString(a)?:"ناشناس",c.getLong(d),ContentUris.withAppendedId(collection,key))) }
   }
  } catch(_:Exception) { Toast.makeText(this,"خطا در خواندن فهرست موسیقی.",Toast.LENGTH_LONG).show() }
  shown=tracks.toList(); render()
 }
 private fun render() {
  rows.removeAllViews(); count.text="آهنگ‌ها  •  ${shown.size}"
  if(shown.isEmpty()) { rows.addView(TextView(this).apply { text=if(tracks.isEmpty()) "آهنگی پیدا نشد؛ فایل موسیقی به دستگاه اضافه کن." else "نتیجه‌ای پیدا نشد."; setTextColor(muted); gravity=Gravity.CENTER; setPadding(10,28,10,28) }); return }
  shown.forEach { track ->
   val row=LinearLayout(this).apply { orientation=1; setPadding(13,11,13,11); setBackgroundColor(panel); layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) } }
   row.addView(TextView(this).apply { text=track.title; setTextColor(pale); textSize=14f; typeface=Typeface.DEFAULT_BOLD; maxLines=1 })
   row.addView(TextView(this).apply { text="${track.artist}  •  ${time(track.duration)}"; setTextColor(muted); textSize=11f; setPadding(0,4,0,0) })
   row.setOnClickListener { val i=tracks.indexOfFirst { it.id==track.id }; if(i>=0) playAt(i) }; rows.addView(row)
  }
 }
 private fun playAt(i:Int) {
  if(i !in tracks.indices) return
  try {
   player?.release()
   player=MediaPlayer().apply { setDataSource(this@MainActivity,tracks[i].uri); setOnCompletionListener { step(1) }; prepare(); start() }
   current=i; nowTitle.text=tracks[i].title; nowArtist.text=tracks[i].artist; playButton.text="Ⅱ"
  } catch(_:Exception) { player?.release(); player=null; playButton.text="▶"; Toast.makeText(this,"پخش این فایل ممکن نیست یا فرمت آن پشتیبانی نمی‌شود.",Toast.LENGTH_SHORT).show() }
 }
 private fun toggle() { val p=player; if(p==null) { if(tracks.isNotEmpty()) playAt(if(current>=0) current else 0) } else if(p.isPlaying) { p.pause(); playButton.text="▶" } else { p.start(); playButton.text="Ⅱ" } }
 private fun step(delta:Int) { if(tracks.isEmpty()) return; playAt(if(current<0) 0 else (current+delta+tracks.size)%tracks.size) }
 private fun button(s:String)=Button(this).apply { text=s; textSize=16f; setTextColor(cyan); isAllCaps=false; setBackgroundColor(Color.TRANSPARENT); minWidth=dp(62) }
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun time(ms:Long):String { val s=ms/1000; return "%d:%02d".format(s/60,s%60) }
 override fun onDestroy() { player?.release(); player=null; super.onDestroy() }
}
