package de.k1direkt.ui

import android.app.Application
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.k1direkt.K1Application
import de.k1direkt.data.AppSettings
import de.k1direkt.data.Http
import de.k1direkt.data.LibraryFile
import de.k1direkt.data.SOURCE_SLICED
import de.k1direkt.data.await
import de.k1direkt.data.readable
import de.k1direkt.printer.PrintState
import de.k1direkt.printer.PrinterClient
import de.k1direkt.printer.PrinterStatus
import de.k1direkt.printer.RemoteFile
import de.k1direkt.slicer.SliceParams
import de.k1direkt.slicer.SliceResult
import de.k1direkt.slicer.SlicerClient
import de.k1direkt.sources.ModelSite
import de.k1direkt.sources.ProviderOutcome
import de.k1direkt.sources.UnifiedSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val page: Int = 0,
    val outcomes: List<ProviderOutcome> = emptyList(),
    val filter: ModelSite? = null,
)

data class TransferState(
    val active: Boolean = false,
    val title: String = "",
    /** null = unbestimmter Fortschritt */
    val progress: Float? = null,
)

sealed interface SliceUiState {
    data object Idle : SliceUiState
    data object Running : SliceUiState
    data class Done(val result: SliceResult) : SliceUiState
    data class Failed(val message: String) : SliceUiState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val k1 = app as K1Application
    private val settingsRepo = k1.settings
    val library = k1.library

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    fun toast(msg: String) { _messages.tryEmit(msg) }

    init {
        viewModelScope.launch { library.refresh() }
    }

    // ------------------------------------------------------------------ Einstellungen

    fun saveSettings(s: AppSettings) = viewModelScope.launch {
        settingsRepo.save(s)
        resetPrinterClient()
        toast("Einstellungen gespeichert")
    }

    // ------------------------------------------------------------------ Suche

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private var searchJob: Job? = null

    fun setSearchFilter(site: ModelSite?) { _search.value = _search.value.copy(filter = site) }

    fun search(query: String, page: Int = 0) {
        if (query.isBlank()) return
        searchJob?.cancel()
        _search.value = _search.value.copy(query = query, loading = true, page = page)
        searchJob = viewModelScope.launch {
            val outcomes = UnifiedSearch.search(query.trim(), page, settings.value)
            _search.value = _search.value.copy(loading = false, outcomes = outcomes)
        }
    }

    // ------------------------------------------------------------------ Bibliothek / Downloads

    private val _transfer = MutableStateFlow(TransferState())
    val transfer: StateFlow<TransferState> = _transfer.asStateFlow()

    fun importUris(uris: List<Uri>) = viewModelScope.launch {
        var count = 0
        for (uri in uris) {
            try {
                count += library.importUri(uri).size
            } catch (e: Exception) {
                toast("Import fehlgeschlagen: ${e.readable()}")
            }
        }
        if (count > 0) toast(if (count == 1) "1 Datei hinzugefügt" else "$count Dateien hinzugefügt")
    }

    /** Wird vom eingebauten Browser aufgerufen, sobald eine Seite einen Download startet. */
    fun downloadFromBrowser(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        referer: String?,
    ) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            toast("Diese Seite liefert den Download nur im Browser aus – bitte 'Im externen Browser öffnen' nutzen und die Datei danach importieren.")
            return
        }
        val site = ModelSite.forUrl(referer ?: url) ?: ModelSite.forUrl(url)
        viewModelScope.launch {
            _transfer.value = TransferState(true, "Lade herunter …", null)
            try {
                val req = Request.Builder().url(url).apply {
                    header("User-Agent", userAgent ?: Http.USER_AGENT)
                    CookieManager.getInstance().getCookie(url)?.let { header("Cookie", it) }
                    referer?.let { header("Referer", it) }
                }.build()
                withContext(Dispatchers.IO) { Http.longClient.await(req) }.use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("Leere Antwort")
                    var name = URLUtil.guessFileName(
                        url,
                        contentDisposition ?: resp.header("Content-Disposition"),
                        mimeType ?: resp.header("Content-Type"),
                    )
                    if (name.endsWith(".bin")) name = name.removeSuffix(".bin") + ".stl"
                    _transfer.value = TransferState(true, "Speichere $name", null)
                    val created = library.importStream(name, body.byteStream(), site?.displayName ?: "Web", referer ?: url)
                    toast(
                        if (created.size == 1) "${created[0].name} in Bibliothek gespeichert"
                        else "${created.size} Dateien in Bibliothek gespeichert"
                    )
                }
            } catch (e: Exception) {
                toast("Download fehlgeschlagen: ${e.readable()}")
            } finally {
                _transfer.value = TransferState()
            }
        }
    }

    fun delete(item: LibraryFile) = viewModelScope.launch {
        library.delete(item)
        toast("${item.name} gelöscht")
    }

    fun rename(item: LibraryFile, newName: String) = viewModelScope.launch {
        if (!library.rename(item, newName)) toast("Umbenennen nicht möglich (Name schon vergeben?)")
    }

    // ------------------------------------------------------------------ Slicen

    private val _slice = MutableStateFlow<SliceUiState>(SliceUiState.Idle)
    val slice: StateFlow<SliceUiState> = _slice.asStateFlow()
    private var sliceJob: Job? = null

    fun resetSlice() {
        sliceJob?.cancel()
        _slice.value = SliceUiState.Idle
    }

    fun testSlicer(s: AppSettings = settings.value) = viewModelScope.launch {
        if (!s.slicerConfigured) { toast("Bitte zuerst die Slicer-Server-Adresse eintragen"); return@launch }
        try {
            val v = SlicerClient(s.slicerUrl, s.slicerToken).health()
            toast("Slicer-Server erreichbar ($v)")
        } catch (e: Exception) {
            toast("Slicer-Server nicht erreichbar: ${e.readable()}")
        }
    }

    fun startSlice(item: LibraryFile, params: SliceParams, rememberAsDefault: Boolean) {
        val s = settings.value
        if (!s.slicerConfigured) {
            _slice.value = SliceUiState.Failed("Kein Slicer-Server eingetragen. Siehe Einstellungen → Slicer.")
            return
        }
        sliceJob?.cancel()
        _slice.value = SliceUiState.Running
        sliceJob = viewModelScope.launch {
            if (rememberAsDefault) settingsRepo.save(s.copy(defaultSlice = params))
            val baseName = item.name.substringBeforeLast('.')
            val suffix = "_${params.material.label}_${"%.2f".format(java.util.Locale.ROOT, params.layerHeight)}mm"
            val out = library.newFile("$baseName$suffix.gcode")
            try {
                val result = SlicerClient(s.slicerUrl, s.slicerToken).slice(item.file, params, out)
                library.registerFile(out, SOURCE_SLICED, item.sourceUrl)
                _slice.value = SliceUiState.Done(result)
            } catch (e: Exception) {
                out.delete()
                if (isActive) _slice.value = SliceUiState.Failed(e.readable())
            }
        }
    }

    // ------------------------------------------------------------------ Drucker

    private var printerClient: PrinterClient? = null
    private var clientFor: AppSettings? = null

    private fun client(): PrinterClient? {
        val s = settings.value
        if (!s.printerConfigured) return null
        if (printerClient == null || clientFor?.printerHost != s.printerHost || clientFor?.printerMode != s.printerMode ||
            clientFor?.moonrakerPort != s.moonrakerPort || clientFor?.moonrakerApiKey != s.moonrakerApiKey
        ) {
            printerClient?.close()
            printerClient = PrinterClient.create(s)
            clientFor = s
        }
        return printerClient
    }

    private fun resetPrinterClient() {
        printerClient?.close()
        printerClient = null
        clientFor = null
    }

    private val _status = MutableStateFlow(PrinterStatus())
    val status: StateFlow<PrinterStatus> = _status.asStateFlow()
    private val _statusError = MutableStateFlow<String?>(null)
    val statusError: StateFlow<String?> = _statusError.asStateFlow()
    private val _remoteFiles = MutableStateFlow<List<RemoteFile>>(emptyList())
    val remoteFiles: StateFlow<List<RemoteFile>> = _remoteFiles.asStateFlow()
    private var pollJob: Job? = null
    private var pollers = 0

    fun startPolling() {
        pollers++
        if (!liveConnection) return
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            settings.first() // sicherstellen, dass Einstellungen geladen sind
            while (isActive) {
                refreshStatus()
                delay(if (_status.value.state == PrintState.OFFLINE) 6_000 else 2_500)
            }
        }
    }

    fun stopPolling() {
        pollers = (pollers - 1).coerceAtLeast(0)
        if (pollers == 0) { pollJob?.cancel(); pollJob = null }
    }

    private suspend fun refreshStatus() {
        val c = client()
        if (c == null) {
            _status.value = PrinterStatus()
            _statusError.value = "Kein Drucker eingerichtet"
            return
        }
        try {
            _status.value = c.status()
            _statusError.value = null
        } catch (e: Exception) {
            _status.value = PrinterStatus()
            _statusError.value = e.readable()
        }
    }

    fun loadRemoteFiles() = viewModelScope.launch {
        if (!liveConnection) return@launch
        val c = client() ?: return@launch
        if (!c.supportsFileList) { _remoteFiles.value = emptyList(); return@launch }
        try {
            _remoteFiles.value = c.listFiles()
        } catch (e: Exception) {
            toast("Dateiliste nicht abrufbar: ${e.readable()}")
        }
    }

    private fun printerAction(label: String, block: suspend (PrinterClient) -> Unit) = viewModelScope.launch {
        val c = client()
        if (c == null) { toast("Bitte zuerst den Drucker in den Einstellungen eintragen"); return@launch }
        try {
            block(c)
            toast(label)
            refreshStatus()
        } catch (e: Exception) {
            toast("Fehler: ${e.readable()}")
        }
    }

    fun pause() = printerAction("Druck pausiert") { it.pause() }
    fun resume() = printerAction("Druck fortgesetzt") { it.resume() }
    fun cancel() = printerAction("Druck abgebrochen") { it.cancel() }
    fun emergencyStop() = printerAction("NOT-AUS gesendet") { it.emergencyStop() }
    fun sendGcode(cmd: String) = printerAction("Befehl gesendet: $cmd") { it.sendGcode(cmd) }
    fun printRemote(name: String) = printerAction("Druck gestartet: $name") { it.startPrint(name) }
    fun deleteRemote(name: String) = printerAction("$name gelöscht") {
        it.deleteFile(name)
        _remoteFiles.value = it.listFiles()
    }

    /** Lädt eine G-Code-Datei auf den Drucker und startet (optional) sofort den Druck. */
    fun sendToPrinter(item: LibraryFile, start: Boolean) = viewModelScope.launch {
        val c = client()
        if (c == null) { toast("Bitte zuerst den Drucker in den Einstellungen eintragen"); return@launch }
        if (!item.type.printable) { toast("Nur G-Code-Dateien können gedruckt werden – bitte zuerst slicen."); return@launch }
        val busy = _status.value.state == PrintState.PRINTING || _status.value.state == PrintState.PAUSED
        val startNow = start && !busy
        _transfer.value = TransferState(true, "Sende ${item.name} an K1 Max", 0f)
        try {
            val remoteName = item.name.replace(' ', '_')
            c.upload(item.file, remoteName, startNow) { p ->
                _transfer.value = _transfer.value.copy(progress = p)
            }
            toast(
                when {
                    startNow -> "Hochgeladen – Druck startet!"
                    start && busy -> "Hochgeladen. Drucker ist beschäftigt, Druck nicht gestartet."
                    else -> "Auf den Drucker hochgeladen"
                }
            )
            refreshStatus()
        } catch (e: Exception) {
            toast("Senden fehlgeschlagen: ${e.readable()}")
        } finally {
            _transfer.value = TransferState()
        }
    }

    // ------------------------------------------------------------------ Nur für Tests / Screenshots

    /** false = keine Netzwerkzugriffe auf den Drucker (Screenshot-Tests mit Beispieldaten). */
    @VisibleForTesting
    internal var liveConnection = true

    @VisibleForTesting
    internal fun showDemoData(search: SearchState? = null, status: PrinterStatus? = null, files: List<RemoteFile>? = null, slice: SliceUiState? = null) {
        search?.let { _search.value = it }
        status?.let { _status.value = it; _statusError.value = null }
        files?.let { _remoteFiles.value = it }
        slice?.let { _slice.value = it }
    }

    override fun onCleared() {
        printerClient?.close()
        super.onCleared()
    }
}
