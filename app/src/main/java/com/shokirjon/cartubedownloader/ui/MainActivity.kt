package com.shokirjon.cartubedownloader.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shokirjon.cartubedownloader.core.*
import com.shokirjon.cartubedownloader.db.DownloadTask
import com.shokirjon.cartubedownloader.db.TrackedPlaylist
import com.shokirjon.cartubedownloader.diagnostics.DiagnosticLog
import com.shokirjon.cartubedownloader.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }
    private lateinit var root: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var page = 0
    private var playlistOffset = 0
    private var playlistId: String? = null
    private var search = ""
    private var libraryVideo = false
    private var playlistUrl = ""
    private val exportLog = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(DiagnosticLog.read(this)) }; toast("Diagnostics exported.") }
        catch (e: Exception) { toast("Could not export diagnostics: ${e.message}") }
    }
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); vm.setFolder(uri.toString()) }
            catch (e: Exception) { toast("Folder permission: ${e.message}") }
        }
    }
    private val panel = Color.rgb(17, 32, 45)
    private val ink = Color.rgb(235, 245, 250)
    private val accent = Color.rgb(65, 202, 187)
    private val portrait get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getInt("page") ?: 0
        playlistId = savedInstanceState?.getString("playlistId")
        playlistOffset = savedInstanceState?.getInt("playlistOffset") ?: 0
        search = savedInstanceState?.getString("search").orEmpty()
        libraryVideo = savedInstanceState?.getBoolean("libraryVideo") ?: false
        playlistUrl = savedInstanceState?.getString("playlistUrl").orEmpty()
        window.statusBarColor = Color.rgb(9, 20, 29)
        window.navigationBarColor = Color.rgb(9, 20, 29)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(9, 20, 29)) }
        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(panel) }
        listOf("Yuklash", "Navbat", "Fayllar", "Pleylist", "Sozlamalar").forEachIndexed { i, name ->
            tabs.addView(button(name) { navigate(i) }.apply {
                minWidth = 0
                minimumWidth = 0
                textSize = if (portrait) 10f else 13f
                setPadding(1.dp, 0, 1.dp, 0)
                alpha = if (page == i) 1f else 0.7f
            }, LinearLayout.LayoutParams(0, 56.dp, 1f))
        }
        if (!portrait) root.addView(tabs)
        status = label("Havolani qo‘ying va tahlil qiling", 14f).apply { setPadding(14.dp, 6.dp, 14.dp, 6.dp); maxLines = 3 }
        root.addView(status)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14.dp, 8.dp, 14.dp, 20.dp) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (portrait) root.addView(tabs)
        setContentView(root)
        if (savedInstanceState == null && intent?.action == Intent.ACTION_SEND) vm.inputText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        render()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.tasks.collect { if (page == 1 || page == 2) render() } }
            launch { vm.previews.collect { if (page == 0) render() } }
            launch { vm.playlists.collect { if (page == 3 && playlistId == null) render() } }
            launch { vm.settings.collect { if (page == 4) render() } }
            launch { vm.message.collect { if (it.isNotBlank()) status.text = it } }
            launch { vm.busy.collect { if (page == 0) render() } }
        } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", page)
        outState.putString("playlistId", playlistId)
        outState.putInt("playlistOffset", playlistOffset)
        outState.putString("search", search)
        outState.putBoolean("libraryVideo", libraryVideo)
        outState.putString("playlistUrl", playlistUrl)
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        when (page) { 0 -> download(); 1 -> queue(); 2 -> library(); 3 -> playlists(); else -> settings() }
    }
    private fun navigate(destination: Int) {
        page = destination
        for (i in 0 until tabs.childCount) tabs.getChildAt(i).alpha = if (i == page) 1f else 0.7f
        render()
    }
    private fun download() {
        heading("YouTube’dan yuklash")
        paragraph("1. O‘zingizga ruxsat etilgan ommaviy video havolasini joylashtiring. Bir nechta bo‘lsa, har birini alohida qatorga yozing.")
        val input = EditText(this).apply {
            setText(vm.inputText); hint = "YouTube havolasini shu yerga qo‘ying"; setTextColor(ink); setHintTextColor(0xff99aab2.toInt())
            minLines = 2; maxLines = 5; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setBackgroundColor(panel); setPadding(12.dp, 8.dp, 12.dp, 8.dp)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { vm.inputText = s.toString() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        content.addView(input, LinearLayout.LayoutParams(-1, -2))
        row {
            addView(button("Joylash va tahlil qilish") {
                val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                val text = clip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty().trim()
                if (text.isBlank()) toast("Nusxalangan havola topilmadi")
                else { input.setText(text); vm.inputText = text; hideKeyboard(input); vm.analyze() }
            }.apply { isEnabled = !vm.busy.value })
            addView(button("1 · Havolani tahlil qilish") {
                vm.inputText = input.text.toString().trim(); hideKeyboard(input); vm.analyze()
            }.apply { isEnabled = !vm.busy.value })
        }
        if (vm.busy.value) {
            paragraph("Havola tekshirilmoqda, biroz kuting…")
            content.addView(ProgressBar(this).apply { isIndeterminate = true })
        }
        val previews = vm.previews.value
        if (previews.isEmpty() && !vm.busy.value) paragraph("Havola qo‘yilgach tahlil boshlanadi. Natija chiqqanidan keyin MP3 yoki MP4 tanlab, yuklash tugmasini bosing.")
        if (vm.playlistReady.value) content.addView(button("Pleylist elementlarini ko‘rish →") { playlistId = null; navigate(3) })
        if (previews.isNotEmpty()) {
            heading("2 · Format va sifatni tanlang")
            outputControls()
            paragraph("Kerakli videolarni belgilang. So‘ng pastdagi yuklash tugmasini bosing.")
            if (previews.size > 1) row {
                addView(button("Barchasini belgilash") { vm.selectAll(true) })
                addView(button("Belgilashni olib tashlash") { vm.selectAll(false) })
            }
        }
        previews.forEachIndexed { index, preview ->
            val info = preview.info
            row {
                addView(CheckBox(this@MainActivity).apply { isChecked = preview.selected; setOnCheckedChangeListener { _, checked -> vm.select(index, checked) } })
                val thumb = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                addView(thumb, LinearLayout.LayoutParams(92.dp, 56.dp))
                info.thumbnail?.let { loadThumbnail(it, thumb) }
                addView(label("${info.title}\n${info.channel} · ${info.duration?.let { "${it / 60}m ${it % 60}s" } ?: "duration unknown"} · ${info.size?.let { "~${it / 1048576} MB" } ?: "size unknown"}", 15f), LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        if (previews.isNotEmpty()) {
            val selected = previews.count { it.selected }
            content.addView(button("3 · Tanlanganlarni yuklab olish ($selected)") {
                if (!vm.analysisMatchesInput()) { toast("Havola o‘zgardi. Qayta tahlil qiling."); return@button }
                vm.queueSelected()
                navigate(1)
            }.apply { isEnabled = selected > 0 && !vm.busy.value }, LinearLayout.LayoutParams(-1, 56.dp))
            paragraph("Yuklash holati Navbat bo‘limida ko‘rinadi. Fayl tugagach Fayllar bo‘limida ochiladi.")
            content.addView(button("Belgilanganlarni ro‘yxatdan olish") { vm.removeSelected() })
        }
    }
    private fun outputControls() {
        row {
            addView(button(if (vm.outputKind == OutputKind.MP3) "● MP3" else "MP3") { vm.outputKind = OutputKind.MP3; render() })
            addView(button(if (vm.outputKind == OutputKind.MP4) "● MP4" else "MP4") { vm.outputKind = OutputKind.MP4; render() })
            if (vm.outputKind == OutputKind.MP3) enumSpinner(AudioQuality.entries.map { "${it.kbps} kbps" }, vm.audioQuality.ordinal) { vm.audioQuality = AudioQuality.entries[it] }
            else enumSpinner(VideoQuality.entries.map { "${it.height}p" }, vm.videoQuality.ordinal) { vm.videoQuality = VideoQuality.entries[it] }
        }
        if (vm.outputKind == OutputKind.MP3) paragraph("Bitrate is an encoding choice; it cannot improve the source audio.")
    }
    private fun queue() {
        val tasks = vm.tasks.value
        heading("Queue · ${tasks.count { it.state == "COMPLETED" }} completed · ${tasks.count { it.state == "FAILED" }} failed")
        row { addView(button("Retry failed") { tasks.filter { it.state == "FAILED" || it.state == "INTERRUPTED" }.forEach { vm.queueAction(it.id, "retry") } }); addView(button("Cancel remaining") { vm.cancelRemaining() }); addView(button("Clear completed") { vm.clearCompleted() }); addView(button("Clear failed") { vm.clearFailed() }) }
        tasks.forEach { t ->
            val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8.dp, 7.dp, 8.dp, 7.dp); setBackgroundColor(panel) }
            block.addView(label("${t.title} · ${t.kind} ${t.quality} · ${t.state}", 17f))
            block.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; progress = (t.progress * 10).toInt().coerceIn(0, 1000) }, LinearLayout.LayoutParams(-1, 8.dp))
            val speed = t.speed?.let { " · ${it / 1024} KB/s" }.orEmpty()
            val eta = t.etaSeconds?.let { " · ETA ${it}s" }.orEmpty()
            block.addView(label("${"%.1f".format(t.progress)}% · ${t.downloadedBytes / 1048576} MB${t.totalBytes?.let { "/${it / 1048576} MB" }.orEmpty()}$speed$eta · ${t.stage.orEmpty()}", 13f))
            t.errorMessage?.let { block.addView(label(it, 14f).apply { setTextColor(0xffffb5ad.toInt()) }) }
            val actions = LinearLayout(this).apply { orientation = if (portrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
            if (t.state in listOf("DOWNLOADING", "PROCESSING", "ANALYZING")) actions.addView(button("Pause") { vm.queueAction(t.id, "pause") })
            if (t.state in listOf("QUEUED", "PENDING", "DOWNLOADING", "PROCESSING", "ANALYZING")) actions.addView(button("Cancel") { vm.queueAction(t.id, "cancel") })
            if (t.state in listOf("FAILED", "PAUSED", "INTERRUPTED", "CANCELLED")) actions.addView(button("Resume / Retry") { vm.queueAction(t.id, "retry") })
            if (t.state in listOf("PENDING", "QUEUED")) { actions.addView(button("↑") { vm.queueAction(t.id, "up") }); actions.addView(button("↓") { vm.queueAction(t.id, "down") }) }
            if (t.state !in listOf("DOWNLOADING", "PROCESSING", "ANALYZING")) actions.addView(button("Remove") { vm.queueAction(t.id, "remove") })
            block.addView(actions)
            content.addView(block, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp })
        }
        if (tasks.isEmpty()) paragraph("No downloads yet.")
    }
    private fun library() {
        heading("Offline library")
        row { addView(button(if (!libraryVideo) "● Music" else "Music") { libraryVideo = false; render() }); addView(button(if (libraryVideo) "● Videos" else "Videos") { libraryVideo = true; render() }) }
        val field = EditText(this).apply { hint = "Search saved files"; setText(search); setTextColor(ink); setSingleLine(true) }
        content.addView(field)
        content.addView(button("Search") { search = field.text.toString(); hideKeyboard(field); render() })
        val files = vm.tasks.value.filter { it.state == "COMPLETED" && it.fileUri != null && it.kind == if (libraryVideo) "MP4" else "MP3" }.filter { it.title.contains(search, true) }.sortedByDescending { it.updatedAt }
        files.forEach { t -> row {
            addView(label("${t.title}\n${t.channel} · ${DateFormat.getDateInstance().format(Date(t.updatedAt))}", 16f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("Play") {
                if (libraryVideo) AlertDialog.Builder(this@MainActivity).setTitle("Parked-only video").setMessage("Only play video when safely parked. Confirm you are parked.").setPositiveButton("I am parked") { _, _ -> startActivity(PlayerActivity.intent(this@MainActivity, t.fileUri!!, true, true)) }.setNegativeButton("Cancel", null).show()
                else startActivity(PlayerActivity.intent(this@MainActivity, t.fileUri!!, false))
            })
            addView(button("Delete") { AlertDialog.Builder(this@MainActivity).setMessage("Delete ${t.title} from device?").setPositiveButton("Delete") { _, _ -> vm.removeLibrary(t.id) }.setNegativeButton("Cancel", null).show() })
        } }
        if (files.isEmpty()) paragraph("No matching saved media.")
    }
    private fun playlists() {
        val id = playlistId
        if (id != null) { playlistItems(id); return }
        heading("Public playlists")
        val url = EditText(this).apply { hint = "https://www.youtube.com/playlist?list=…"; setText(playlistUrl); setSingleLine(true); setTextColor(ink) }
        content.addView(url)
        val interval = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Manual", "Daily", "Weekly")) }
        row { addView(label("Check: ", 16f)); addView(interval); addView(button("Add / update") { playlistUrl = url.text.toString(); vm.addPlaylist(playlistUrl, listOf("MANUAL", "DAILY", "WEEKLY")[interval.selectedItemPosition], true, vm.settings.value.autoAdd, vm.outputKind, if (vm.outputKind == OutputKind.MP3) vm.audioQuality.name else vm.videoQuality.name); hideKeyboard(url) }) }
        vm.playlists.value.forEach { p -> row {
            addView(label("${p.title}\n${p.knownCount} items · ${p.newCount} new · ${p.interval} · ${p.lastChecked?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "never checked"}", 15f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("View") { playlistId = p.id; playlistOffset = 0; render() })
            addView(button("Check") { vm.syncPlaylist(p.id) })
            addView(button("×") { AlertDialog.Builder(this@MainActivity).setMessage("Remove playlist ${p.title}?").setPositiveButton("Remove") { _, _ -> vm.removePlaylist(p.id) }.setNegativeButton("Cancel", null).show() })
        } }
        if (vm.playlists.value.isEmpty()) paragraph("Add a playlist and choose its items for download.")
    }
    private fun playlistItems(id: String) {
        val p = vm.playlists.value.firstOrNull { it.id == id }
        heading(p?.title ?: "Playlist")
        row { addView(button("← Playlists") { playlistId = null; render() }); addView(button("All") { vm.playlistSelectAll(id, true); loadPlaylistPage(id) }); addView(button("None") { vm.playlistSelectAll(id, false); loadPlaylistPage(id) }); addView(button("Queue selected") { vm.queuePlaylist(id) }) }
        paragraph("${p?.knownCount ?: 0} discovered · ${p?.newCount ?: 0} new · page ${playlistOffset / 20 + 1}")
        loadPlaylistPage(id)
    }
    private fun loadPlaylistPage(id: String) {
        val offset = playlistOffset
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { vm.playlistPage(id, offset) }
            if (page != 3 || playlistId != id || playlistOffset != offset) return@launch
            val marker = content.findViewWithTag<LinearLayout>("playlist_list")
            if (marker != null) content.removeView(marker)
            val list = LinearLayout(this@MainActivity).apply { tag = "playlist_list"; orientation = LinearLayout.VERTICAL }
            items.forEach { item ->
                val checkbox = CheckBox(this@MainActivity).apply { text = "${item.position + 1}. ${item.title}${if (item.queued) " · queued" else ""}"; setTextColor(ink); isChecked = item.selected; isEnabled = !item.queued; setOnCheckedChangeListener { _, checked -> vm.playlistSelect(id, item.videoId, checked) } }
                list.addView(checkbox)
            }
            val nav = LinearLayout(this@MainActivity)
            if (offset > 0) nav.addView(button("Previous 20") { playlistOffset = (offset - 20).coerceAtLeast(0); render() })
            if (items.size == 20) nav.addView(button("Next 20") { playlistOffset += 20; render() })
            list.addView(nav)
            content.addView(list)
        }
    }
    private fun settings() {
        heading("Settings")
        val s = vm.settings.value
        content.addView(CheckBox(this).apply { text = "Download on Wi-Fi only"; setTextColor(ink); isChecked = s.wifiOnly; setOnCheckedChangeListener { _, value -> vm.setWifi(value) } })
        content.addView(CheckBox(this).apply { text = "Auto-add new playlist items (only where permitted)"; setTextColor(ink); isChecked = s.autoAdd; setOnCheckedChangeListener { _, value -> vm.setAutoAdd(value) } })
        paragraph("Default MP3 quality: ${s.audioQuality.removePrefix("K")} kbps · MP4: ${s.videoQuality.removePrefix("P")}p")
        row { AudioQuality.entries.forEach { q -> addView(button("${q.kbps}k") { vm.audioQuality = q; vm.setAudio(q.name) }) }; VideoQuality.entries.forEach { q -> addView(button("${q.height}p") { vm.videoQuality = q; vm.setVideo(q.name) }) } }
        paragraph("Destination: ${s.folderUri ?: "MediaStore · Music/CarTube and Movies/CarTube"}")
        row { addView(button("Choose USB/SD folder") { pickFolder.launch(null) }); addView(button("Use internal storage") { vm.setFolder(null) }) }
        val stat = StatFs(cacheDir.absolutePath)
        paragraph("Free local space: ${stat.availableBytes / 1048576} MB · safety reserve: ${s.reserveBytes / 1048576} MB")
        row { addView(button("Check engine") { vm.checkHealth() }); addView(button("Clear cache") { vm.clearCache() }); addView(button("Export log") { exportLog.launch("cartube-diagnostics.txt") }) }
        paragraph("CarTube Downloader 0.1.1 · ARM v7a / ARM64 · yt-dlp, EJS, QuickJS, FFmpeg. Diagnostics are visible in queue errors. GPL-3.0 source and dependency notices in the package repository.")
    }
    override fun onBackPressed() {
        if (page == 3 && playlistId != null) { playlistId = null; render() }
        else if (page != 0) navigate(0)
        else super.onBackPressed()
    }
    private fun heading(t: String) { content.addView(label(t, 21f).apply { setTextColor(accent); setPadding(0, 4.dp, 0, 10.dp) }) }
    private fun paragraph(t: String) { content.addView(label(t, 14f).apply { setPadding(0, 6.dp, 0, 7.dp) }) }
    private fun label(t: String, size: Float) = TextView(this).apply { text = t; textSize = size; setTextColor(ink); gravity = Gravity.CENTER_VERTICAL }
    private fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; textSize = 12f; minHeight = 48.dp; isAllCaps = false; setOnClickListener { f() } }
    private fun row(build: LinearLayout.() -> Unit) {
        content.addView(LinearLayout(this).apply {
            orientation = if (portrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            build()
            if (portrait) for (i in 0 until childCount) {
                val child = getChildAt(i)
                val params = child.layoutParams as LinearLayout.LayoutParams
                params.width = LinearLayout.LayoutParams.MATCH_PARENT
                params.weight = 0f
                child.layoutParams = params
            }
        })
    }
    private fun LinearLayout.enumSpinner(items: List<String>, chosen: Int, onChosen: (Int) -> Unit) {
        addView(Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items); setSelection(chosen); onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { onChosen(position) }
        } })
    }
    private fun loadThumbnail(address: String, view: ImageView) {
        lifecycleScope.launch {
            val image = withContext(Dispatchers.IO) { try { val conn = URL(address).openConnection(); conn.connectTimeout = 5000; conn.readTimeout = 5000; conn.getInputStream().use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } }
            if (view.parent != null) view.setImageBitmap(image)
        }
    }
    private fun hideKeyboard(view: View) { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0); view.clearFocus() }
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
