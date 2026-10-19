package com.shokirjon.cartubedownloader.ui

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.StatFs
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.BaseAdapter
import android.widget.AbsListView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shokirjon.cartubedownloader.core.AudioQuality
import com.shokirjon.cartubedownloader.core.FormatSelector
import com.shokirjon.cartubedownloader.core.OutputKind
import com.shokirjon.cartubedownloader.core.VideoQuality
import com.shokirjon.cartubedownloader.db.DownloadTask
import com.shokirjon.cartubedownloader.db.PlaylistEntry
import com.shokirjon.cartubedownloader.diagnostics.DiagnosticLog
import com.shokirjon.cartubedownloader.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }
    private val bg = Color.rgb(8, 19, 28)
    private val surface = Color.rgb(17, 36, 48)
    private val raised = Color.rgb(24, 47, 59)
    private val ink = Color.rgb(236, 248, 249)
    private val muted = Color.rgb(151, 177, 185)
    private val accent = Color.rgb(75, 216, 195)
    private val danger = Color.rgb(255, 167, 159)
    private val portrait get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    private val images by lazy { ThumbnailLoader(this) }
    private val previewPlayer by lazy { YouTubePreview(this) { id, code -> showEmbedFallback(id, code) } }
    private val localPreview by lazy { LocalVideoPreview(this) }
    private var previewDialog: AlertDialog? = null
    private var activePreviewId: String? = null
    private var unavailablePreviewId: String? = null
    private var embedFailureCode: Int? = null
    private var modalVideo: FrameLayout? = null
    private var parkedForPreview = false
    private var playlistShell: LinearLayout? = null
    private var playlistShownId: String? = null
    private var paging: PlaylistPagingState? = null
    private var playlistList: ListView? = null
    private var playlistAdapter: BaseAdapter? = null
    private var selectionCount: TextView? = null
    private var playlistCta: TextView? = null
    private var pagingFooter: LinearLayout? = null
    private var playlistHeading: TextView? = null
    private var playlistMeta: TextView? = null
    private var playlistInputHost: LinearLayout? = null
    private var showPlaylistInput = false
    private val positions = mutableMapOf<String, Pair<Int, Int>>()
    private lateinit var root: LinearLayout
    private lateinit var workspace: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var modeTabs: LinearLayout
    private lateinit var miniPanel: LinearLayout
    private lateinit var notice: TextView
    private lateinit var queueBadge: TextView
    private var mode = 0 // 0: Single/batch videos, 1: Playlist
    private var playlistId: String? = null
    private var playlistUrl = ""
    private var playlistOffset = 0
    private var playlistInterval = 0
    private var playlistGeneration = 0
    private var libraryVideo = false
    private var librarySearch = ""
    private var libraryAlphabetical = false
    private var libraryPage = 0
    private var queuePage = 0
    private var lastQueueRefreshAt = 0L
    private var sheet: AlertDialog? = null
    private var sheetKind: String? = null
    private var sheetBody: LinearLayout? = null
    private var libraryCount = 0
    private var lastCompletedCount = 0
    private var taskByVideo: Map<String, DownloadTask> = emptyMap()
    private val exportLog = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) try {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(DiagnosticLog.read(this)) }
                ?: error("File cannot be opened")
            toast("Diagnostika saqlandi")
        } catch (e: Exception) { toast("Logni saqlab bo‘lmadi: ${e.message}") }
    }
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.setFolder(uri.toString())
        } catch (e: Exception) { toast("Papkaga ruxsat berilmadi: ${e.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = savedInstanceState?.getInt("mode") ?: 0
        playlistId = savedInstanceState?.getString("playlistId")
        playlistUrl = savedInstanceState?.getString("playlistUrl").orEmpty()
        playlistOffset = savedInstanceState?.getInt("playlistOffset") ?: 0
        savedInstanceState?.getInt("playlistFirst")?.let { positions[playlistId.orEmpty()] = it to (savedInstanceState.getInt("playlistTop")) }
        playlistInterval = savedInstanceState?.getInt("playlistInterval") ?: 0
        vm.inputText = savedInstanceState?.getString("inputText") ?: vm.inputText
        window.statusBarColor = bg
        window.navigationBarColor = bg
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = column().apply { setBackgroundColor(bg) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        buildTopBar()
        modeTabs = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(16.dp, 0, 16.dp, 7.dp) }
        root.addView(modeTabs)
        notice = text("", 13f, muted).apply { visibility = View.GONE; setPadding(18.dp, 3.dp, 18.dp, 7.dp); maxLines = 2 }
        root.addView(notice)
        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        workspace = column().apply { setPadding(16.dp, 7.dp, 16.dp, 16.dp) }
        scroll.addView(workspace)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        miniPanel = column().apply { setPadding(14.dp, 9.dp, 14.dp, 8.dp); background = shape(surface, radius = 0) }
        root.addView(miniPanel)
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        if (savedInstanceState == null) acceptShare(intent)
        renderTabs()
        renderWorkspace(resetScroll = true)
        renderMiniPanel()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.tasks.collect { tasks ->
                renderMiniPanel()
                updatePlaylistStatuses(tasks)
                if (sheetKind == "queue" && SystemClock.elapsedRealtime() - lastQueueRefreshAt >= 1200L) renderQueueSheet()
                val finished = tasks.count { it.state == "COMPLETED" }
                if (sheetKind == "library" && finished != libraryCount) renderLibrarySheet()
                if (finished != lastCompletedCount && mode == 0 && vm.previews.value.isEmpty()) renderWorkspace()
                lastCompletedCount = finished
            } }
            launch { vm.previews.collect { if (mode == 0) renderWorkspace() } }
            launch { vm.playlists.collect { if (mode == 1) {
                if (playlistId != null && playlistList == null && vm.playlists.value.any { it.id == playlistId }) renderWorkspace(resetScroll = true)
                else updatePlaylistHeader()
            } } }
            launch { vm.playlistRevision.collect { if (it > 0 && mode == 1) refreshLoadedItems() } }
            launch { vm.busy.collect { if (mode == 0) renderWorkspace() else updatePlaylistHeader() } }
            launch { vm.settings.collect { if (sheetKind == "settings") renderSettingsSheet() } }
            launch { vm.message.collect { value ->
                notice.text = value
                notice.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
            } }
            launch { vm.loadedPlaylistId.collect { id ->
                if (id != null && id != playlistId) {
                    playlistId = id; playlistOffset = 0; mode = 1
                    renderTabs(); renderWorkspace(resetScroll = true)
                }
            } }
        } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShare(intent)
        mode = 0; renderTabs(); renderWorkspace(resetScroll = true)
    }

    override fun onStop() {
        super.onStop()
        previewDialog?.dismiss()
        if (::root.isInitialized) { closePreview(); activePreviewId = null }
        parkedForPreview = false
    }

    private fun acceptShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) vm.inputText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("mode", mode)
        outState.putString("playlistId", playlistId)
        outState.putString("playlistUrl", playlistUrl)
        outState.putInt("playlistOffset", playlistOffset)
        outState.putInt("playlistInterval", playlistInterval)
        playlistList?.let { list -> outState.putInt("playlistFirst", list.firstVisiblePosition); outState.putInt("playlistTop", list.getChildAt(0)?.top ?: 0) }
        outState.putString("inputText", vm.inputText)
        super.onSaveInstanceState(outState)
    }

    private fun buildTopBar() {
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(17.dp, 9.dp, 12.dp, 7.dp) }
        val mark = text("▶", 18f, bg).apply { gravity = Gravity.CENTER; background = shape(accent, radius = 13) }
        bar.addView(mark, LinearLayout.LayoutParams(40.dp, 40.dp))
        val titles = column().apply { setPadding(10.dp, 0, 0, 0) }
        titles.addView(text("Car Tube", 21f, ink, true))
        bar.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        queueBadge = icon("☷", "Navbat") { openSheet("queue") }
        bar.addView(queueBadge, LinearLayout.LayoutParams(44.dp, 44.dp))
        bar.addView(icon("▤", "Fayllar") { openSheet("library") }, LinearLayout.LayoutParams(44.dp, 44.dp))
        bar.addView(icon("⚙", "Sozlamalar") { openSheet("settings") }, LinearLayout.LayoutParams(44.dp, 44.dp))
        root.addView(bar)
    }

    private fun renderTabs() {
        modeTabs.removeAllViews()
        val segment = LinearLayout(this).apply {
            setPadding(3.dp, 3.dp, 3.dp, 3.dp)
            background = shape(surface, radius = 21)
        }
        listOf("Video", "Pleylist").forEachIndexed { index, title ->
            segment.addView(button(title, if (mode == index) Style.SELECTED else Style.GHOST) {
                if (mode != index) { mode = index; renderTabs(); renderWorkspace(resetScroll = true) }
            }.apply { textSize = 13f }, LinearLayout.LayoutParams(if (portrait) 126.dp else 146.dp, 40.dp))
        }
        modeTabs.addView(segment)
    }

    private fun renderWorkspace(resetScroll: Boolean = false) {
        if (!::workspace.isInitialized) return
        if (mode == 1) {
            if (scroll.parent === root) {
                root.removeView(scroll)
                val shell = playlistShell ?: column().also { playlistShell = it }
                root.addView(shell, 3, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            if (playlistShownId != playlistId || playlistList == null || resetScroll) buildPlaylistWorkspace()
            else updatePlaylistHeader()
            return
        }
        if (playlistShell?.parent === root) {
            rememberPosition()
            root.removeView(playlistShell)
            root.addView(scroll, 3, LinearLayout.LayoutParams(-1, 0, 1f))
            closePreview(); activePreviewId = null
        }
        val previousY = if (resetScroll) 0 else scroll.scrollY
        playlistGeneration++
        workspace.removeAllViews()
        val left = column()
        val right = column()
        if (portrait) {
            workspace.addView(left)
            workspace.addView(right)
        } else {
            workspace.orientation = LinearLayout.HORIZONTAL
            workspace.addView(left, LinearLayout.LayoutParams(0, -2, 0.95f).apply { rightMargin = 14.dp })
            workspace.addView(right, LinearLayout.LayoutParams(0, -2, 1.05f))
        }
        if (portrait) workspace.orientation = LinearLayout.VERTICAL
        if (mode == 0) {
            singleInput(left)
            results(right)
        } else {
            playlistInput(left)
            playlistResults(right)
        }
        scroll.post { if (workspace.isAttachedToWindow) scroll.scrollTo(0, previousY) }
    }

    private fun singleInput(parent: LinearLayout) {
        parent.addView(text("Havola", 16f, ink, true), spaced(bottom = 7))
        val entry = urlCard(parent, vm.inputText, true) { vm.inputText = it }
        entry.hint = "YouTube havolasini kiriting yoki joylang"
        val actions = entry.tag as LinearLayout
        actions.addView(button("Joylash", Style.QUIET) {
            val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val pasted = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty().trim()
            if (pasted.isBlank()) toast("Nusxalangan havola yo‘q") else {
                entry.setText(pasted); vm.inputText = pasted; hideKeyboard(entry); vm.analyze()
            }
        }, LinearLayout.LayoutParams(0, 44.dp, 1f))
        actions.addView(button("Tozalash", Style.QUIET) { entry.setText(""); vm.inputText = "" }, LinearLayout.LayoutParams(0, 44.dp, 1f))
        actions.addView(button("Tahlil", Style.PRIMARY) {
            vm.inputText = entry.text.toString().trim(); hideKeyboard(entry); vm.analyze()
        }.apply { isEnabled = !vm.busy.value }, LinearLayout.LayoutParams(0, 44.dp, 1.15f))
        if (vm.busy.value) loading(parent, "Havolalar tekshirilmoqda…")
        qualityControls(parent)
    }

    private fun playlistInput(parent: LinearLayout) {
        if (playlistId != null && portrait && !showPlaylistInput) {
            parent.addView(button("Boshqa pleylist", Style.QUIET) { showPlaylistInput = true; renderPlaylistInputOnly() }, spaced(bottom = 2))
            qualityControls(parent)
            return
        }
        if (playlistId != null && portrait) parent.addView(button("↓  Ro‘yxatga qaytish", Style.QUIET) {
            showPlaylistInput = false; renderPlaylistInputOnly()
        }, spaced(bottom = 4))
        parent.addView(text("Pleylist havolasi", 16f, ink, true), spaced(bottom = 7))
        val entry = urlCard(parent, playlistUrl, false) { playlistUrl = it }
        entry.hint = "youtube.com/playlist?list=…"
        val actions = entry.tag as LinearLayout
        actions.addView(button("Joylash", Style.QUIET) {
            val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val pasted = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty().trim()
            if (pasted.isBlank()) toast("Nusxalangan havola yo‘q") else { entry.setText(pasted); playlistUrl = pasted }
        }, LinearLayout.LayoutParams(0, 44.dp, 1f))
        actions.addView(button("Tahlil", Style.PRIMARY) {
            playlistUrl = entry.text.toString().trim(); hideKeyboard(entry)
            vm.addPlaylist(playlistUrl, listOf("MANUAL", "DAILY", "WEEKLY")[playlistInterval],
                true, vm.settings.value.autoAdd, vm.outputKind,
                if (vm.outputKind == OutputKind.MP3) vm.audioQuality.name else vm.videoQuality.name)
        }.apply { isEnabled = !vm.busy.value }, LinearLayout.LayoutParams(0, 44.dp, 1.2f))
        val intervalRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        intervalRow.addView(text("Yangilarni tekshirish", 12f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        intervalRow.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Qo‘lda", "Har kuni", "Har hafta"))
            setSelection(playlistInterval)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { playlistInterval = position }
            }
        }, LinearLayout.LayoutParams(128.dp, 44.dp))
        parent.addView(intervalRow, spaced(top = 9))
        if (vm.busy.value) loading(parent, "Pleylist tekshirilmoqda…")
        qualityControls(parent)
        if (vm.playlists.value.isNotEmpty()) {
            parent.addView(text("Pleylistlar", 13f, ink, true), spaced(top = 12, bottom = 5))
            vm.playlists.value.take(3).forEach { p ->
                val item = column().apply { setPadding(12.dp, 10.dp, 12.dp, 10.dp); background = ripple(surface, 13) }
                item.addView(text(p.title, 14f, ink, true).apply { maxLines = 1 })
                item.addView(text("${p.knownCount} ta video · ${p.newCount} yangi · ${p.interval.lowercase()}", 11f, muted))
                item.setOnClickListener {
                    playlistId = p.id; playlistUrl = p.url; playlistOffset = 0; showPlaylistInput = false
                    playlistInterval = listOf("MANUAL", "DAILY", "WEEKLY").indexOf(p.interval).coerceAtLeast(0)
                    renderWorkspace(resetScroll = true)
                }
                parent.addView(item, spaced(bottom = 7))
            }
            if (vm.playlists.value.size > 3) parent.addView(button("Barcha pleylistlar", Style.QUIET) {
                val all = vm.playlists.value
                AlertDialog.Builder(this).setItems(all.map { it.title }.toTypedArray()) { _, index ->
                    playlistId = all[index].id; playlistUrl = all[index].url; showPlaylistInput = false
                    renderWorkspace(resetScroll = true)
                }.show()
            })
        }
    }

    private fun renderPlaylistInputOnly() {
        playlistInputHost?.let { it.removeAllViews(); playlistInput(it) }
    }

    private fun urlCard(parent: LinearLayout, value: String, multiline: Boolean, onText: (String) -> Unit): EditText {
        val frame = AnimatedInputFrame(this).apply { setPadding(2.dp, 2.dp, 2.dp, 2.dp) }
        val inner = column().apply { setPadding(12.dp, 9.dp, 12.dp, 7.dp); background = shape(surface, radius = 17) }
        val entry = EditText(this).apply {
            setText(value); setTextColor(ink); setHintTextColor(muted); textSize = 15f
            minLines = if (multiline) 2 else 1; maxLines = if (multiline) 4 else 2
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                (if (multiline) android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else android.text.InputType.TYPE_TEXT_VARIATION_URI)
            background = null; setPadding(3.dp, 5.dp, 3.dp, 8.dp)
            addTextChangedListener(watcher(onText))
        }
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        entry.tag = buttons
        inner.addView(entry, LinearLayout.LayoutParams(-1, -2))
        inner.addView(View(this).apply { setBackgroundColor(Color.rgb(45, 72, 82)) }, LinearLayout.LayoutParams(-1, 1.dp))
        inner.addView(buttons, spaced(top = 5))
        frame.addView(inner)
        parent.addView(frame, LinearLayout.LayoutParams(-1, -2))
        return entry
    }

    private fun qualityControls(parent: LinearLayout) {
        parent.addView(text("Format", 12f, muted, true), spaced(top = 9, bottom = 5))
        val kinds = LinearLayout(this)
        kinds.addView(button("♫  MP3", if (vm.outputKind == OutputKind.MP3) Style.SELECTED else Style.SECONDARY) {
            vm.outputKind = OutputKind.MP3; if (mode == 1) renderPlaylistInputOnly() else renderWorkspace()
        }, LinearLayout.LayoutParams(0, 42.dp, 1f).apply { rightMargin = 6.dp })
        kinds.addView(button("▶  MP4", if (vm.outputKind == OutputKind.MP4) Style.SELECTED else Style.SECONDARY) {
            vm.outputKind = OutputKind.MP4; if (mode == 1) renderPlaylistInputOnly() else renderWorkspace()
        }, LinearLayout.LayoutParams(0, 42.dp, 1f))
        parent.addView(kinds)
        val qualities = LinearLayout(this).apply { setPadding(0, 9.dp, 0, 0) }
        if (vm.outputKind == OutputKind.MP3) AudioQuality.entries.forEach { q ->
            qualities.addView(button("${q.kbps}k", if (q == vm.audioQuality) Style.SELECTED else Style.QUIET) {
                vm.audioQuality = q; if (mode == 1) renderPlaylistInputOnly() else renderWorkspace()
            }, LinearLayout.LayoutParams(0, 38.dp, 1f).apply { rightMargin = 5.dp })
        } else VideoQuality.entries.forEach { q ->
            qualities.addView(button("${q.height}p", if (q == vm.videoQuality) Style.SELECTED else Style.QUIET) {
                vm.videoQuality = q; if (mode == 1) renderPlaylistInputOnly() else renderWorkspace()
            }, LinearLayout.LayoutParams(0, 38.dp, 1f).apply { rightMargin = 5.dp })
        }
        parent.addView(qualities)
    }

    private fun results(parent: LinearLayout) {
        val previews = vm.previews.value
        if (previews.isNotEmpty()) parent.addView(text("${previews.size} ta video", 16f, ink, true), spaced(bottom = 7))
        if (previews.isEmpty()) {
            emptyState(parent, "Video havolasini kiriting", "Natija shu yerda chiqadi.")
            recent(parent)
            return
        }
        val selected = previews.count { it.selected }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(text("$selected ta tanlangan", 12f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        if (previews.size > 1) {
            toolbar.addView(button("Hammasi", Style.QUIET) { vm.selectAll(true) }, LinearLayout.LayoutParams(72.dp, 38.dp))
            toolbar.addView(button("Tozalash", Style.QUIET) { vm.selectAll(false) }, LinearLayout.LayoutParams(78.dp, 38.dp))
        }
        parent.addView(toolbar, spaced(bottom = 7))
        parent.addView(button("Yuklash ($selected)", Style.PRIMARY) {
            if (!vm.analysisMatchesInput()) { toast("Havola o‘zgardi. Qayta tahlil qiling."); return@button }
            vm.queueSelected()
        }.apply { isEnabled = selected > 0 && !vm.busy.value }, LinearLayout.LayoutParams(-1, 48.dp).apply { bottomMargin = 12.dp })
        previews.forEachIndexed { index, item ->
            val data = item.info
            val card = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(7.dp, 8.dp, 9.dp, 8.dp); background = shape(surface, radius = 14) }
            card.addView(CheckBox(this).apply { isChecked = item.selected; buttonTintList = ColorStateList.valueOf(accent); setOnCheckedChangeListener { _, value -> vm.select(index, value) } }, LinearLayout.LayoutParams(42.dp, 48.dp))
            val thumb = thumbnail(data.thumbnail, data.id)
            card.addView(thumb, LinearLayout.LayoutParams(76.dp, 52.dp).apply { rightMargin = 10.dp })
            val texts = column()
            texts.addView(text(data.title, 14f, ink, true).apply { maxLines = 2 })
            texts.addView(text(listOfNotNull(data.channel.takeIf(String::isNotBlank), data.duration?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }, data.size?.let { "~${it / 1048576} MB" }).joinToString(" · "), 11f, muted).apply { maxLines = 2 })
            val heights = FormatSelector.compatible(data.formats, 720).mapNotNull { it.height }.distinct().sorted()
            texts.addView(text(if (heights.isEmpty()) "MP4: mos format aniqlanmadi" else "MP4: ${heights.joinToString(" / ") { "${it}p" }}", 11f, accent).apply { maxLines = 1 })
            card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            parent.addView(card, spaced(bottom = 7))
        }
    }

    private fun rememberPosition() {
        val id = playlistShownId ?: return
        playlistList?.let { positions[id] = it.firstVisiblePosition to (it.getChildAt(0)?.top ?: 0) }
    }

    private fun buildPlaylistWorkspace() {
        rememberPosition()
        previewDialog?.dismiss()
        closePreview(); activePreviewId = null
        val shell = playlistShell ?: return
        shell.removeAllViews()
        shell.setPadding(14.dp, 6.dp, 14.dp, 2.dp)
        shell.orientation = if (portrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val left = column()
        val inputScroll = ScrollView(this).apply { clipToPadding = false; addView(left) }
        val right = column()
        if (portrait) {
            shell.addView(inputScroll, LinearLayout.LayoutParams(-1, -2))
            shell.addView(right, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 8.dp })
        } else {
            shell.addView(inputScroll, LinearLayout.LayoutParams(0, -1, 0.43f).apply { rightMargin = 15.dp })
            shell.addView(right, LinearLayout.LayoutParams(0, -1, 0.57f))
        }
        playlistShownId = playlistId
        playlistInputHost = left
        playlistInput(left)
        playlistResults(right)
    }

    private fun playlistResults(parent: LinearLayout) {
        val id = playlistId
        val playlist = vm.playlists.value.firstOrNull { it.id == id }
        val headingRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        playlistHeading = text(playlist?.title ?: "Pleylist", 16f, ink, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        headingRow.addView(playlistHeading, LinearLayout.LayoutParams(0, -2, 1f))
        if (id != null) {
            headingRow.addView(button("↻", Style.QUIET) { vm.syncPlaylist(id) }.apply { contentDescription = "Pleylistni tekshirish" }, LinearLayout.LayoutParams(40.dp, 40.dp))
            headingRow.addView(button("×", Style.QUIET) {
                AlertDialog.Builder(this).setMessage("${playlist?.title ?: "Pleylist"} ro‘yxatdan o‘chirilsinmi?")
                    .setPositiveButton("O‘chirish") { _, _ ->
                        vm.removePlaylist(id); playlistId = null; renderWorkspace(resetScroll = true)
                    }.setNegativeButton("Bekor", null).show()
            }.apply { contentDescription = "Pleylistni o‘chirish" }, LinearLayout.LayoutParams(40.dp, 40.dp))
        }
        parent.addView(headingRow, spaced(bottom = 2))
        playlistMeta = text("", 11f, muted)
        parent.addView(playlistMeta, spaced(bottom = 5))
        if (id == null || playlist == null) {
            parent.addView(text("Pleylist havolasini kiriting", 12f, muted), spaced(top = 7))
            playlistList = null; paging = null
            return
        }
        val actions = LinearLayout(this)
        actions.addView(button(if (playlist.totalCount != null && playlist.totalCount > playlist.knownCount) "Topilganlar" else "Barchasi", Style.QUIET) { changePlaylistSelection(id) { vm.playlistSelectAll(id, true) } }, LinearLayout.LayoutParams(0, 40.dp, 1f))
        actions.addView(button("Tozalash", Style.QUIET) { changePlaylistSelection(id) { vm.playlistSelectAll(id, false) } }, LinearLayout.LayoutParams(0, 40.dp, 1f))
        actions.addView(button("Yangilari", Style.QUIET) { changePlaylistSelection(id) { vm.playlistSelectOnlyNew(id) } }, LinearLayout.LayoutParams(0, 40.dp, 1f))
        parent.addView(actions)
        val actionRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        selectionCount = text("0 ta tanlandi", 11f, muted)
        actionRow.addView(selectionCount, LinearLayout.LayoutParams(0, -2, 1f))
        playlistCta = button("Yuklash (0)", Style.PRIMARY) { vm.queuePlaylist(id) }.apply { isEnabled = false }
        actionRow.addView(playlistCta, LinearLayout.LayoutParams(126.dp, 44.dp))
        parent.addView(actionRow, spaced(bottom = 3))
        val state = PlaylistPagingState(id)
        paging = state
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.rgb(39, 67, 78))
            dividerHeight = 1.dp
            setBackgroundColor(bg)
            cacheColorHint = bg
            isFastScrollEnabled = true
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
                override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                    if (state.items.isNotEmpty() && firstVisibleItem + visibleItemCount >= state.items.size - 6) loadNextPage(id)
                    val active = activePreviewId
                    if (active != null && visibleItemCount > 0 && previewDialog?.isShowing != true && state.items.indexOfFirst { it.videoId == active }.let { it < firstVisibleItem || it >= firstVisibleItem + visibleItemCount }) {
                        closePreview(); activePreviewId = null
                    }
                }
            })
        }
        playlistList = list
        val adapter = object : BaseAdapter() {
            override fun getCount() = state.items.size
            override fun getItem(position: Int) = state.items[position]
            override fun getItemId(position: Int) = state.items[position].videoId.hashCode().toLong()
            override fun hasStableIds() = true
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView?.tag as? PlaylistRow) ?: PlaylistRow()
                row.bind(state.items[position])
                return row.root
            }
        }
        playlistAdapter = adapter
        list.adapter = adapter
        parent.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        pagingFooter = column()
        parent.addView(pagingFooter, spaced(top = 2))
        updatePlaylistHeader()
        loadNextPage(id)
    }

    private inner class PlaylistRow {
        val root = column().apply { setPadding(0, 5.dp, 0, 5.dp); minimumHeight = 78.dp }
        private val line = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        private val check = CheckBox(this@MainActivity).apply { buttonTintList = ColorStateList.valueOf(accent); contentDescription = "Videoni tanlash" }
        private val image = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = shape(raised, 7) }
        private val duration = text("", 10f, ink).apply { setPadding(3.dp, 1.dp, 3.dp, 1.dp); background = shape(Color.rgb(10, 22, 30), 3) }
        private val frame = FrameLayout(this@MainActivity).apply {
            addView(image, FrameLayout.LayoutParams(-1, -1))
            addView(duration, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.RIGHT).apply { setMargins(0, 0, 3.dp, 3.dp) })
        }
        private val playerHost = FrameLayout(this@MainActivity).apply { visibility = View.GONE }
        private val title = text("", 13f, ink, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        private val status = text("", 11f, muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        private val retry = button("↻", Style.QUIET) {}.apply { contentDescription = "Qayta yuklash" }
        private val expand = button("⤢", Style.QUIET) {}.apply { contentDescription = "Katta oynada ko‘rish" }
        private val details = column()
        private val previewActions = column().apply { gravity = Gravity.TOP; visibility = View.GONE }
        private val progressBar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = ColorStateList.valueOf(accent)
        }
        private var boundId: String? = null

        init {
            root.tag = this
            frame.addView(playerHost, FrameLayout.LayoutParams(-1, -1))
            line.addView(check, LinearLayout.LayoutParams(45.dp, 52.dp))
            line.addView(frame, LinearLayout.LayoutParams(112.dp, 63.dp).apply { rightMargin = 9.dp })
            details.addView(title)
            val small = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            small.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
            small.addView(retry, LinearLayout.LayoutParams(38.dp, 38.dp))
            small.addView(expand, LinearLayout.LayoutParams(38.dp, 38.dp))
            details.addView(small)
            line.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            previewActions.addView(button("×", Style.QUIET) { openInline(boundId ?: return@button) }
                .apply { contentDescription = "Kichik videoni yopish" }, LinearLayout.LayoutParams(44.dp, 44.dp))
            previewActions.addView(button("⤢", Style.QUIET) {
                val id = boundId ?: return@button
                openLarge(id, title.text.toString())
            }.apply { contentDescription = "Videoni kattalashtirish" }, LinearLayout.LayoutParams(44.dp, 44.dp))
            line.addView(previewActions)
            root.addView(line)
            root.addView(progressBar, LinearLayout.LayoutParams(-1, 3.dp).apply { leftMargin = 45.dp; topMargin = 2.dp })
        }

        fun bind(entry: PlaylistEntry) {
            if (boundId != entry.videoId) images.unbind(image)
            boundId = entry.videoId
            root.setBackgroundColor(if (entry.selected) Color.rgb(19, 48, 54) else bg)
            check.setOnCheckedChangeListener(null)
            check.isChecked = entry.selected && !entry.queued
            check.isEnabled = !entry.queued
            check.contentDescription = "${entry.title}: ${if (check.isChecked) "tanlangan" else "tanlash"}"
            check.setOnCheckedChangeListener { _, checked ->
                val id = playlistId ?: return@setOnCheckedChangeListener
                paging?.update(entry.videoId) { it.copy(selected = checked) }
                root.setBackgroundColor(if (checked) Color.rgb(19, 48, 54) else bg)
                status.text = if (checked) "Tanlangan" else ""
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { vm.playlistSelect(id, entry.videoId, checked) }
                    updatePlaylistHeader()
                }
            }
            images.bind(this@MainActivity, image, entry.thumbnail, entry.videoId)
            duration.text = entry.duration?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: ""
            duration.visibility = if (entry.duration == null) View.GONE else View.VISIBLE
            title.text = entry.title
            frame.setOnClickListener { openInline(entry.videoId) }
            title.setOnClickListener { openInline(entry.videoId) }
            frame.setOnLongClickListener { openLarge(entry.videoId, entry.title); true }
            title.setOnLongClickListener { openLarge(entry.videoId, entry.title); true }
            expand.setOnClickListener { openLarge(entry.videoId, entry.title) }
            showStatus(entry)
            val inline = activePreviewId == entry.videoId && previewDialog?.isShowing != true
            val frameParams = frame.layoutParams as LinearLayout.LayoutParams
            if (inline) {
                // Replace the thumbnail in place. The official embed requires at least 200x200 CSS pixels.
                frameParams.width = 0; frameParams.height = 200.dp; frameParams.weight = 1f
                frameParams.rightMargin = 4.dp
                details.visibility = View.GONE
                previewActions.visibility = View.VISIBLE
                image.visibility = View.GONE; duration.visibility = View.GONE
                playerHost.visibility = View.VISIBLE
                if (unavailablePreviewId == entry.videoId) embedFallback(playerHost, entry.videoId)
                else attachPreview(playerHost, entry.videoId)
            } else {
                if (playerHost.childCount > 0) {
                    closePreview()
                    playerHost.removeAllViews()
                }
                playerHost.visibility = View.GONE
                image.visibility = View.VISIBLE
                frameParams.width = 112.dp; frameParams.height = 63.dp; frameParams.weight = 0f
                frameParams.rightMargin = 9.dp
                details.visibility = View.VISIBLE
                previewActions.visibility = View.GONE
            }
            frame.layoutParams = frameParams
        }

        fun showStatus(entry: PlaylistEntry) {
            val task = taskByVideo[entry.videoId]
            status.text = when {
                task != null -> "${stateLabel(task.state)}${if (task.state in listOf("DOWNLOADING", "PROCESSING")) " · ${task.progress.toInt()}% · ${task.downloadedBytes / 1048576} MB" else ""}"
                entry.selected -> "Tanlangan"
                else -> ""
            }
            progressBar.visibility = if (task?.state in setOf("DOWNLOADING", "PROCESSING")) View.VISIBLE else View.GONE
            progressBar.progress = ((task?.progress ?: 0f) * 10).toInt().coerceIn(0, 1000)
            retry.visibility = if (task?.state == "FAILED") View.VISIBLE else View.GONE
            retry.setOnClickListener { task?.id?.let { vm.queueAction(it, "retry") } }
        }
    }

    private fun loadNextPage(id: String) {
        val state = paging?.takeIf { it.playlistId == id } ?: return
        val offset = state.begin() ?: return
        showPagingFooter(state)
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { vm.playlistPage(id, offset, state.pageSize) }
                if (paging !== state) return@launch
                val list = playlistList ?: return@launch
                val first = list.firstVisiblePosition
                val top = list.getChildAt(0)?.top ?: 0
                state.append(items)
                playlistAdapter?.notifyDataSetChanged()
                val restore = positions[id]
                if (restore != null && restore.first < state.items.size) {
                    list.setSelectionFromTop(restore.first, restore.second)
                    positions.remove(id)
                } else if (restore != null && !state.exhausted) {
                    list.post { loadNextPage(id) }
                } else if (first > 0) list.setSelectionFromTop(first, top)
                showPagingFooter(state)
            } catch (_: Exception) {
                if (paging === state) { state.fail(); showPagingFooter(state) }
            }
        }
    }

    private fun showPagingFooter(state: PlaylistPagingState) {
        val footer = pagingFooter ?: return
        footer.removeAllViews()
        when {
            state.loading -> footer.addView(text("◌  Yuklanmoqda…", 11f, muted))
            state.failed -> footer.addView(button("Xatolik · Qayta urinish", Style.QUIET) {
                state.retry()
                loadNextPage(state.playlistId)
            })
            state.items.isEmpty() && state.exhausted -> footer.addView(text("Video topilmadi", 12f, muted))
        }
    }

    private fun changePlaylistSelection(id: String, change: suspend () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { change() }
            if (mode == 1 && playlistId == id) refreshLoadedItems()
        }
    }

    private fun refreshLoadedItems() {
        val state = paging ?: return
        lifecycleScope.launch {
            val replacement = withContext(Dispatchers.IO) {
                (0 until state.items.size step state.pageSize).flatMap { vm.playlistPage(state.playlistId, it, state.pageSize) }
            }
            if (paging !== state) return@launch
            val list = playlistList ?: return@launch
            val first = list.firstVisiblePosition
            val top = list.getChildAt(0)?.top ?: 0
            val byId = replacement.associateBy { it.videoId }
            state.items.replaceAll { byId[it.videoId] ?: it }
            playlistAdapter?.notifyDataSetChanged()
            list.setSelectionFromTop(first, top)
            updatePlaylistHeader()
        }
    }

    private fun updatePlaylistHeader() {
        val id = playlistId ?: return
        val playlist = vm.playlists.value.firstOrNull { it.id == id } ?: return
        playlistHeading?.text = playlist.title
        playlistMeta?.text = "${playlist.totalCount ?: playlist.knownCount} video · ${playlist.knownCount} topildi · ${playlist.newCount} yangi"
        val state = paging
        if (state?.playlistId == id && state.exhausted && playlist.knownCount > state.items.size) {
            state.allowMore()
            playlistList?.post { if (paging === state) loadNextPage(id) }
        }
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { vm.playlistSelectedCount(id) }
            if (playlistId != id) return@launch
            selectionCount?.text = "$count ta tanlandi"
            playlistCta?.text = "Yuklash ($count)"
            playlistCta?.isEnabled = count > 0 && !vm.busy.value
        }
    }

    private fun updatePlaylistStatuses(tasks: List<DownloadTask>) {
        taskByVideo = tasks.asSequence().filter { it.videoId != null }
            .sortedBy { it.updatedAt }.associateBy { it.videoId!! }
        val list = playlistList ?: return
        val state = paging ?: return
        for (i in 0 until list.childCount) {
            val position = list.firstVisiblePosition + i
            if (position in state.items.indices) (list.getChildAt(i).tag as? PlaylistRow)?.showStatus(state.items[position])
        }
    }

    private fun confirmPreview(action: () -> Unit) {
        if (parkedForPreview) { action(); return }
        AlertDialog.Builder(this).setTitle("Video faqat to‘xtab turganda")
            .setMessage("Videoni faqat xavfsiz joyda to‘xtab turganingizda tomosha qiling.")
            .setPositiveButton("Mashina to‘xtagan") { _, _ -> parkedForPreview = true; action() }
            .setNegativeButton("Bekor", null).show()
    }

    private fun openInline(id: String) = confirmPreview {
        val list = playlistList
        val first = list?.firstVisiblePosition ?: 0
        val top = list?.getChildAt(0)?.top ?: 0
        if (activePreviewId == id) {
            activePreviewId = null; closePreview()
        } else {
            closePreview(); unavailablePreviewId = null; embedFailureCode = null; activePreviewId = id
        }
        playlistAdapter?.notifyDataSetChanged()
        list?.setSelectionFromTop(first, top)
    }

    private fun openLarge(id: String, title: String) = confirmPreview {
        if (activePreviewId != id) { closePreview(); unavailablePreviewId = null; embedFailureCode = null; activePreviewId = id }
        previewDialog?.dismiss()
        val body = column().apply { setPadding(10.dp, 10.dp, 10.dp, 10.dp); background = shape(bg, 12) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(title, 14f, ink, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        val dialog = AlertDialog.Builder(this).setView(body).create()
        top.addView(button("×", Style.QUIET) { dialog.dismiss() }.apply { contentDescription = "Yopish" }, LinearLayout.LayoutParams(44.dp, 44.dp))
        body.addView(top)
        val video = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        modalVideo = video
        val width = minOf(resources.displayMetrics.widthPixels - 42.dp, 760.dp)
        body.addView(video, LinearLayout.LayoutParams(-1, (width * 9f / 16f).toInt().coerceAtLeast(200.dp)))
        dialog.setOnDismissListener {
            previewDialog = null
            val list = playlistList
            val state = paging
            val at = state?.items?.indexOfFirst { it.videoId == id } ?: -1
            if (list != null && at in list.firstVisiblePosition until (list.firstVisiblePosition + list.childCount)) {
                playlistAdapter?.notifyDataSetChanged()
            } else { closePreview(); activePreviewId = null }
        }
        dialog.show()
        previewDialog = dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(width + 20.dp, WindowManager.LayoutParams.WRAP_CONTENT)
        if (unavailablePreviewId == id) embedFallback(video, id) else attachPreview(video, id)
    }

    private fun attachPreview(parent: FrameLayout, id: String) {
        val local = taskByVideo[id]?.takeIf { it.state == "COMPLETED" && it.kind == "MP4" }?.fileUri
        if (local != null) {
            previewPlayer.close()
            localPreview.attach(parent, Uri.parse(local))
        } else {
            localPreview.close()
            previewPlayer.attach(parent, id)
        }
    }

    private fun closePreview() { previewPlayer.close(); localPreview.close() }

    private fun showEmbedFallback(failedVideoId: String?, code: Int?) {
        val id = activePreviewId?.takeIf { it == failedVideoId } ?: return
        DiagnosticLog.record(this, "preview-embed-error", "YouTube error code: ${code ?: "unknown"}")
        embedFailureCode = code
        unavailablePreviewId = id
        previewPlayer.close()
        if (previewDialog?.isShowing == true) modalVideo?.let { embedFallback(it, id) }
        else playlistAdapter?.notifyDataSetChanged()
    }

    private fun embedFallback(parent: FrameLayout, id: String) {
        parent.removeAllViews()
        val content = column().apply { gravity = Gravity.CENTER; setPadding(8.dp, 5.dp, 8.dp, 5.dp) }
        content.addView(text(EmbedErrors.message(embedFailureCode), 12f, ink), spaced(bottom = 7))
        content.addView(button("YouTube’da ochish", Style.SELECTED) { openYouTube(id) })
        parent.addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    private fun openYouTube(id: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id"))
        if (intent.resolveActivity(packageManager) != null) startActivity(intent) else toast("YouTube ilovasi topilmadi")
    }

    private fun recent(parent: LinearLayout) {
        val completed = vm.tasks.value.filter { it.state == "COMPLETED" }.sortedByDescending { it.updatedAt }.take(2)
        if (completed.isEmpty()) return
        parent.addView(text("Yaqinda saqlangan", 14f, ink, true), spaced(top = 19, bottom = 7))
        completed.forEach { task ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(11.dp, 9.dp, 7.dp, 9.dp); background = shape(surface) }
            row.addView(text("${if (task.kind == "MP3") "♫" else "▶"}  ${task.title}", 12f, ink).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("Ochish", Style.QUIET) { play(task) }, LinearLayout.LayoutParams(72.dp, 38.dp))
            parent.addView(row, spaced(bottom = 6))
        }
        parent.addView(button("Barcha fayllar →", Style.QUIET) { openSheet("library") }, spaced(bottom = 3))
    }

    private fun renderMiniPanel() {
        if (!::miniPanel.isInitialized) return
        miniPanel.removeAllViews()
        val tasks = vm.tasks.value
        val active = tasks.firstOrNull { it.state in listOf("ANALYZING", "DOWNLOADING", "PROCESSING") }
        val paused = tasks.firstOrNull { it.state in listOf("PAUSED", "INTERRUPTED") }
        val waiting = tasks.count { it.state in listOf("PENDING", "QUEUED") }
        val completed = tasks.count { it.state == "COMPLETED" }
        val failed = tasks.count { it.state == "FAILED" }
        queueBadge.text = if (waiting > 0) "☷$waiting" else "☷"
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(if (active != null) "Yuklanmoqda" else "Yuklamalar", 13f, accent, true), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text("$waiting navbatda · $completed tayyor · $failed xato  ›", 11f, muted))
        miniPanel.addView(top)
        if (active != null) {
            val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            line.addView(text(active.title, 13f, ink, true).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            if (active.state in listOf("ANALYZING", "DOWNLOADING", "PROCESSING")) {
                line.addView(button("Ⅱ", Style.QUIET) { vm.queueAction(active.id, "pause") }.apply { contentDescription = "Pauza" }, LinearLayout.LayoutParams(38.dp, 38.dp))
                line.addView(button("×", Style.QUIET) { vm.queueAction(active.id, "cancel") }.apply { contentDescription = "Bekor qilish" }, LinearLayout.LayoutParams(38.dp, 38.dp))
            }
            miniPanel.addView(line)
            miniPanel.addView(progress(active), LinearLayout.LayoutParams(-1, 4.dp).apply { topMargin = 3.dp })
            val details = "${stateLabel(active.state)} · ${active.progress.toInt()}% · ${active.downloadedBytes / 1048576} MB${active.totalBytes?.let { "/${it / 1048576} MB" }.orEmpty()}${active.speed?.let { " · ${it / 1024} KB/s" }.orEmpty()}${active.etaSeconds?.let { " · ~${it}s" }.orEmpty()}"
            miniPanel.addView(text(details, 11f, muted).apply { maxLines = 1 }, spaced(top = 4))
        } else if (paused != null) {
            val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            line.addView(text("${stateLabel(paused.state)} · ${paused.title}", 12f, ink).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(button("Davom", Style.SELECTED) { vm.queueAction(paused.id, "retry") }, LinearLayout.LayoutParams(74.dp, 38.dp))
            miniPanel.addView(line)
        }
        miniPanel.setOnClickListener { openSheet("queue") }
    }

    private fun openSheet(kind: String) {
        sheet?.dismiss()
        sheetKind = kind
        val body = column().apply { setPadding(17.dp, 17.dp, 17.dp, 19.dp); background = shape(bg, radius = 20) }
        val scroller = ScrollView(this).apply { isFillViewport = true; addView(body) }
        sheetBody = body
        val dialog = AlertDialog.Builder(this).setView(scroller).create()
        sheet = dialog
        dialog.setOnDismissListener { if (sheet === dialog) { sheet = null; sheetKind = null; sheetBody = null } }
        when (kind) {
            "queue" -> renderQueueSheet()
            "library" -> renderLibrarySheet()
            "settings" -> renderSettingsSheet()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(if (portrait) WindowManager.LayoutParams.MATCH_PARENT else
            minOf(680.dp, resources.displayMetrics.widthPixels - 30.dp),
            (resources.displayMetrics.heightPixels * if (portrait) 0.84f else 0.91f).toInt())
    }

    private fun sheetHeader(body: LinearLayout, title: String, subtitle: String) {
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val heading = column()
        heading.addView(text(title, 22f, ink, true))
        heading.addView(text(subtitle, 12f, muted))
        bar.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(button("×", Style.QUIET) { sheet?.dismiss() }.apply { contentDescription = "Yopish" }, LinearLayout.LayoutParams(44.dp, 44.dp))
        body.addView(bar, spaced(bottom = 17))
    }

    private fun renderQueueSheet() {
        if (sheetKind != "queue") return
        lastQueueRefreshAt = SystemClock.elapsedRealtime()
        val body = sheetBody ?: return
        body.removeAllViews()
        val tasks = vm.tasks.value
        sheetHeader(body, "Navbat", "${tasks.count { it.state == "COMPLETED" }} tayyor · ${tasks.count { it.state == "FAILED" }} xato")
        val relevant = tasks.filter { it.state != "COMPLETED" }
        val actions = LinearLayout(this)
        actions.addView(button("Xatolarni qayta", Style.SECONDARY) { relevant.filter { it.state in listOf("FAILED", "INTERRUPTED") }.forEach { vm.queueAction(it.id, "retry") } }, LinearLayout.LayoutParams(0, 42.dp, 1f))
        actions.addView(button("Qolganini bekor", Style.QUIET) { vm.cancelRemaining() }, LinearLayout.LayoutParams(0, 42.dp, 1f))
        body.addView(actions, spaced(bottom = 9))
        if (tasks.isEmpty()) emptyState(body, "Navbat bo‘sh", "Yuklash ekranidan video tanlang.")
        val start = queuePage * 20
        if (start >= tasks.size && queuePage > 0) queuePage = 0
        tasks.drop(queuePage * 20).take(20).forEach { task -> queueItem(body, task) }
        val paging = LinearLayout(this)
        if (queuePage > 0) paging.addView(button("← Oldingi", Style.QUIET) { queuePage--; renderQueueSheet() }, LinearLayout.LayoutParams(0, 42.dp, 1f))
        if (tasks.size > (queuePage + 1) * 20) paging.addView(button("Keyingi →", Style.QUIET) { queuePage++; renderQueueSheet() }, LinearLayout.LayoutParams(0, 42.dp, 1f))
        body.addView(paging)
        val clear = LinearLayout(this)
        clear.addView(button("Tayyorlar tarixini tozalash", Style.QUIET) {
            AlertDialog.Builder(this).setMessage("Tayyor vazifalar ro‘yxatdan va ilova kutubxonasidan yo‘qoladi. Fayllar qurilma xotirasida qoladi.")
                .setPositiveButton("Tarixni tozalash") { _, _ -> vm.clearCompleted() }
                .setNegativeButton("Bekor", null).show()
        }, LinearLayout.LayoutParams(0, 42.dp, 1f))
        clear.addView(button("Xatolarni tozalash", Style.QUIET) { vm.clearFailed() }, LinearLayout.LayoutParams(0, 42.dp, 1f))
        body.addView(clear, spaced(top = 10))
    }

    private fun queueItem(parent: LinearLayout, task: DownloadTask) {
        val card = column().apply { setPadding(12.dp, 10.dp, 12.dp, 10.dp); background = shape(surface) }
        card.addView(text(task.title, 14f, ink, true).apply { maxLines = 2 })
        card.addView(text("${task.kind} ${task.quality} · ${stateLabel(task.state)} · ${task.stage.orEmpty()}", 11f, muted).apply { maxLines = 2 }, spaced(top = 3))
        card.addView(progress(task), LinearLayout.LayoutParams(-1, 4.dp).apply { topMargin = 8.dp })
        card.addView(text("${task.progress.toInt()}% · ${task.downloadedBytes / 1048576} MB${task.totalBytes?.let { "/${it / 1048576} MB" }.orEmpty()}${task.speed?.let { " · ${it / 1024} KB/s" }.orEmpty()}${task.etaSeconds?.let { " · ~${it}s" }.orEmpty()}", 11f, muted), spaced(top = 5))
        task.errorMessage?.let { card.addView(text(it, 12f, danger), spaced(top = 5)) }
        val buttons = LinearLayout(this)
        if (task.state in listOf("ANALYZING", "DOWNLOADING", "PROCESSING")) buttons.addView(button("Pauza", Style.QUIET) { vm.queueAction(task.id, "pause") }, LinearLayout.LayoutParams(0, 38.dp, 1f))
        if (task.state in listOf("PENDING", "QUEUED", "ANALYZING", "DOWNLOADING", "PROCESSING")) buttons.addView(button("Bekor", Style.QUIET) { vm.queueAction(task.id, "cancel") }, LinearLayout.LayoutParams(0, 38.dp, 1f))
        if (task.state in listOf("FAILED", "PAUSED", "INTERRUPTED", "CANCELLED")) buttons.addView(button("Qayta yuklash", Style.SECONDARY) { vm.queueAction(task.id, "retry") }, LinearLayout.LayoutParams(0, 38.dp, 1f))
        if (task.state in listOf("PENDING", "QUEUED")) {
            buttons.addView(button("↑", Style.QUIET) { vm.queueAction(task.id, "up") }, LinearLayout.LayoutParams(38.dp, 38.dp))
            buttons.addView(button("↓", Style.QUIET) { vm.queueAction(task.id, "down") }, LinearLayout.LayoutParams(38.dp, 38.dp))
        }
        if (task.state !in listOf("ANALYZING", "DOWNLOADING", "PROCESSING", "COMPLETED")) buttons.addView(button("Olib tashlash", Style.QUIET) { vm.queueAction(task.id, "remove") }, LinearLayout.LayoutParams(0, 38.dp, 1f))
        card.addView(buttons, spaced(top = 5))
        parent.addView(card, spaced(bottom = 8))
    }

    private fun renderLibrarySheet() {
        if (sheetKind != "library") return
        val body = sheetBody ?: return
        body.removeAllViews()
        libraryCount = vm.tasks.value.count { it.state == "COMPLETED" }
        sheetHeader(body, "Saqlangan fayllar", "$libraryCount ta yuklangan fayl")
        val types = LinearLayout(this)
        listOf("♫  Musiqa", "▶  Video").forEachIndexed { index, title ->
            types.addView(button(title, if (libraryVideo == (index == 1)) Style.SELECTED else Style.SECONDARY) {
                libraryVideo = index == 1; libraryPage = 0; renderLibrarySheet()
            }, LinearLayout.LayoutParams(0, 42.dp, 1f))
        }
        body.addView(types, spaced(bottom = 10))
        val searchRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val searchField = EditText(this).apply { hint = "Nom bo‘yicha qidirish"; setText(librarySearch); setSingleLine(true); setTextColor(ink); setHintTextColor(muted); textSize = 14f; background = shape(surface) }
        searchRow.addView(searchField, LinearLayout.LayoutParams(0, 46.dp, 1f))
        searchRow.addView(button("Qidir", Style.SECONDARY) { librarySearch = searchField.text.toString(); libraryPage = 0; hideKeyboard(searchField); renderLibrarySheet() }, LinearLayout.LayoutParams(78.dp, 44.dp))
        body.addView(searchRow, spaced(bottom = 6))
        body.addView(button(if (libraryAlphabetical) "Saralash: nomi bo‘yicha" else "Saralash: eng yangisi", Style.QUIET) { libraryAlphabetical = !libraryAlphabetical; renderLibrarySheet() }, spaced(bottom = 9))
        val matches = vm.tasks.value.filter { it.state == "COMPLETED" && it.fileUri != null && it.kind == if (libraryVideo) "MP4" else "MP3" }
            .filter { it.title.contains(librarySearch, true) }.let { if (libraryAlphabetical) it.sortedBy { t -> t.title.lowercase() } else it.sortedByDescending { t -> t.updatedAt } }
        if (matches.isEmpty()) emptyState(body, "Fayl topilmadi", "Yuklash tugagach fayllar shu yerda ko‘rinadi.")
        matches.drop(libraryPage * 20).take(20).forEach { task ->
            val row = column().apply { setPadding(12.dp, 10.dp, 12.dp, 10.dp); background = shape(surface) }
            row.addView(text(task.title, 14f, ink, true).apply { maxLines = 2 })
            val location = if (task.fileUri?.contains("/tree/") == true || task.fileUri?.contains("/document/") == true) "Tanlangan papka"
                else if (task.kind == "MP3") "Music/CarTube" else "Movies/CarTube"
            row.addView(text("${task.kind} · ${DateFormat.getDateInstance().format(Date(task.updatedAt))} · $location", 11f, muted))
            val actions = LinearLayout(this)
            actions.addView(button("Ochish", Style.SECONDARY) { play(task) }, LinearLayout.LayoutParams(0, 40.dp, 1f))
            actions.addView(button("O‘chirish", Style.QUIET) {
                AlertDialog.Builder(this).setMessage("${task.title} faylini qurilmadan o‘chirasizmi?")
                    .setPositiveButton("O‘chirish") { _, _ -> vm.removeLibrary(task.id) }
                    .setNegativeButton("Bekor", null).show()
            }, LinearLayout.LayoutParams(0, 40.dp, 1f))
            row.addView(actions, spaced(top = 4))
            body.addView(row, spaced(bottom = 7))
        }
        val pages = LinearLayout(this)
        if (libraryPage > 0) pages.addView(button("← Oldingi", Style.QUIET) { libraryPage--; renderLibrarySheet() }, LinearLayout.LayoutParams(0, 40.dp, 1f))
        if (matches.size > (libraryPage + 1) * 20) pages.addView(button("Keyingi →", Style.QUIET) { libraryPage++; renderLibrarySheet() }, LinearLayout.LayoutParams(0, 40.dp, 1f))
        body.addView(pages)
    }

    private fun renderSettingsSheet() {
        if (sheetKind != "settings") return
        val body = sheetBody ?: return
        body.removeAllViews()
        val settings = vm.settings.value
        sheetHeader(body, "Sozlamalar", "Yuklash va saqlash")
        section(body, "Yuklash")
        body.addView(toggle("Faqat Wi-Fi orqali yuklash", settings.wifiOnly) { vm.setWifi(it) })
        body.addView(toggle("Yangi pleylist videolarini navbatga qo‘shish", settings.autoAdd) { vm.setAutoAdd(it) })
        section(body, "Standart sifat")
        body.addView(text("MP3 · ${settings.audioQuality.removePrefix("K")} kbps", 12f, muted), spaced(bottom = 5))
        val audio = LinearLayout(this)
        AudioQuality.entries.forEach { q -> audio.addView(button("${q.kbps}k", if (settings.audioQuality == q.name) Style.SELECTED else Style.QUIET) { vm.audioQuality = q; vm.setAudio(q.name) }, LinearLayout.LayoutParams(0, 39.dp, 1f)) }
        body.addView(audio)
        body.addView(text("MP4 · ${settings.videoQuality.removePrefix("P")}p", 12f, muted), spaced(top = 10, bottom = 5))
        val video = LinearLayout(this)
        VideoQuality.entries.forEach { q -> video.addView(button("${q.height}p", if (settings.videoQuality == q.name) Style.SELECTED else Style.QUIET) { vm.videoQuality = q; vm.setVideo(q.name) }, LinearLayout.LayoutParams(0, 39.dp, 1f)) }
        body.addView(video)
        section(body, "Saqlash joyi")
        body.addView(text(settings.folderUri?.let { "Tanlangan tashqi papka" } ?: "Ichki xotira · Music/CarTube va Movies/CarTube", 12f, muted), spaced(bottom = 7))
        val folders = LinearLayout(this)
        folders.addView(button("USB/SD tanlash", Style.SECONDARY) { pickFolder.launch(null) }, LinearLayout.LayoutParams(0, 43.dp, 1f))
        folders.addView(button("Ichki xotira", Style.QUIET) { vm.setFolder(null) }, LinearLayout.LayoutParams(0, 43.dp, 1f))
        body.addView(folders)
        val free = StatFs(cacheDir.absolutePath).availableBytes / 1048576
        body.addView(text("Bo‘sh joy: $free MB · xavfsizlik zaxirasi: ${settings.reserveBytes / 1048576} MB", 12f, muted), spaced(top = 8))
        section(body, "Asboblar")
        body.addView(button("Dvigatelni tekshirish", Style.SECONDARY) { vm.checkHealth() }, spaced(bottom = 5))
        body.addView(button("Vaqtinchalik fayllarni tozalash", Style.QUIET) { vm.clearCache() }, spaced(bottom = 5))
        body.addView(button("Diagnostikani eksport qilish", Style.QUIET) { exportLog.launch("cartube-diagnostics.txt") })
        body.addView(text("Car Tube 0.1.7 · ARMv7 / ARM64 · yt-dlp, EJS, QuickJS, FFmpeg", 11f, muted), spaced(top = 17))
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(text(title, 15f, accent, true), spaced(top = 15, bottom = 9))
    }

    private fun toggle(title: String, checked: Boolean, action: (Boolean) -> Unit) = CheckBox(this).apply {
        text = title; textSize = 13f; setTextColor(ink); buttonTintList = ColorStateList.valueOf(accent)
        isChecked = checked; minHeight = 48.dp; setOnCheckedChangeListener { _, value -> action(value) }
    }

    private fun play(task: DownloadTask) {
        val uri = task.fileUri ?: return
        if (task.kind == "MP4") AlertDialog.Builder(this).setTitle("Video faqat to‘xtab turganda")
            .setMessage("Videoni faqat xavfsiz joyda to‘xtab turganingizda oching.")
            .setPositiveButton("Mashina to‘xtagan") { _, _ -> startActivity(PlayerActivity.intent(this, uri, true, true)) }
            .setNegativeButton("Bekor", null).show()
        else startActivity(PlayerActivity.intent(this, uri, false))
    }

    private fun thumbnail(url: String?, videoId: String? = null): ImageView {
        val view = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(raised, radius = 8); clipToOutline = true }
        images.bind(this, view, url, videoId)
        return view
    }

    private fun progress(task: DownloadTask) = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000; progress = (task.progress * 10).toInt().coerceIn(0, 1000)
        progressTintList = ColorStateList.valueOf(accent)
        progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(32, 58, 68))
        isIndeterminate = task.state == "ANALYZING" || (task.state == "PROCESSING" && task.progress == 0f)
    }

    private fun stateLabel(state: String) = when (state) {
        "PENDING", "QUEUED" -> "Navbatda"
        "ANALYZING" -> "Tayyorlanmoqda"
        "DOWNLOADING" -> "Yuklanmoqda"
        "PROCESSING" -> "Ishlanmoqda"
        "COMPLETED" -> "Tayyor"
        "FAILED" -> "Xato"
        "CANCELLED" -> "Bekor qilingan"
        "PAUSED" -> "Pauza"
        "INTERRUPTED" -> "Uzilib qolgan"
        else -> state
    }

    private fun loading(parent: LinearLayout, label: String) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(32.dp, 32.dp))
        row.addView(text(label, 12f, accent), spaced(left = 9))
        parent.addView(row, spaced(top = 10))
    }
    private fun emptyState(parent: LinearLayout, title: String, details: String) {
        val card = column().apply { setPadding(16.dp, 19.dp, 16.dp, 19.dp); background = shape(surface) }
        card.addView(text(title, 16f, ink, true))
        card.addView(text(details, 12f, muted), spaced(top = 5))
        parent.addView(card)
    }
    private enum class Style { PRIMARY, SECONDARY, QUIET, GHOST, SELECTED }
    private fun button(title: String, style: Style, action: () -> Unit): TextView {
        val fill = when (style) {
            Style.PRIMARY -> accent
            Style.SELECTED -> Color.rgb(31, 91, 93)
            Style.SECONDARY -> raised
            Style.QUIET, Style.GHOST -> surface
        }
        val color = if (style == Style.PRIMARY) bg else if (style == Style.SELECTED) accent else ink
        return text(title, 12f, color, style == Style.PRIMARY || style == Style.SELECTED).apply {
            gravity = Gravity.CENTER; minHeight = 38.dp; setPadding(7.dp, 5.dp, 7.dp, 5.dp)
            background = ripple(fill, if (style == Style.SELECTED) 18 else 12)
            isClickable = true; isFocusable = true; setOnClickListener { action() }
        }
    }
    private fun icon(symbol: String, description: String, action: () -> Unit) = button(symbol, Style.GHOST, action).apply {
        textSize = 23f; contentDescription = description
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun shape(fill: Int, radius: Int = 14) = GradientDrawable().apply {
        cornerRadius = radius.dp.toFloat(); setColor(fill)
        if (radius != 0) setStroke(1.dp, Color.rgb(35, 62, 74))
    }
    private fun ripple(fill: Int, radius: Int = 14) = RippleDrawable(ColorStateList.valueOf(Color.rgb(52, 124, 125)), shape(fill, radius), null)
    private fun spaced(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply { setMargins(left.dp, top.dp, right.dp, bottom.dp) }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private fun hideKeyboard(view: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun watcher(onChange: (String) -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChange(s.toString()) }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }

    @Deprecated("Compatibility with Android 11 system back dispatch")
    override fun onBackPressed() {
        if (previewDialog?.isShowing == true) previewDialog?.dismiss()
        else if (activePreviewId != null) { closePreview(); activePreviewId = null; playlistAdapter?.notifyDataSetChanged() }
        else if (sheet?.isShowing == true) sheet?.dismiss()
        else if (mode == 1) { mode = 0; renderTabs(); renderWorkspace(resetScroll = true) }
        else super.onBackPressed()
    }
}
