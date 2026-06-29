@file:OptIn(ExperimentalMaterial3Api::class)

package com.k2fsa.sherpa.onnx.tts.engine

import PreferenceHelper
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.Voice
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.tts.engine.ui.theme.SherpaOnnxTtsEngineTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.TimeSource

const val TAG = "portal-tts"

private val ACCENT = Color(0xFF7C5CFF)
private const val PORTAL_TTS_PACKAGE = "com.portal.tts"

data class GenStats(
    val elapsed: Float,
    val audioDuration: Float,
    val chars: Int,
    val threads: Int,
    val ttfbMs: Long,
    val voiceLabel: String,
    val modelName: String,
)

data class RecentGeneration(
    val text: String,
    val voiceName: String,
    val voiceLabel: String,
    val stats: GenStats,
)

data class BenchmarkResult(
    val voiceName: String,
    val voiceLabel: String,
    val modelName: String,
    val elapsed: Float,
    val audioDuration: Float,
    val realtime: Float,
    val ttfbMs: Long,
)

private fun qualityStars(q: Int): Int = when (q) {
    Voice.QUALITY_VERY_HIGH -> 5
    Voice.QUALITY_HIGH -> 4
    Voice.QUALITY_NORMAL -> 3
    Voice.QUALITY_LOW -> 2
    else -> 1
}

private fun voiceLabel(spec: TtsVoiceSpec): String {
    val stars = "*".repeat(qualityStars(spec.quality))
    val kokoro = spec.name.substringAfter("sherpa-kokoro-", "")
    val m = Regex("^([ab])([fm])_(.+)$").find(kokoro)
    val base = if (m != null) {
        val (region, gender, nm) = m.destructured
        val reg = if (region == "a") "US" else "UK"
        val g = if (gender == "f") "F" else "M"
        "${nm.replaceFirstChar { it.uppercase() }} - $reg $g"
    } else {
        spec.displayName.replaceFirstChar { it.uppercase() }
    }
    return "$base  $stars"
}

private fun localeLabel(spec: TtsVoiceSpec): String =
    listOf(spec.locale.displayLanguage, spec.locale.displayCountry)
        .filter { it.isNotBlank() }
        .joinToString(" / ")

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var track: AudioTrack
    private var stopped: Boolean = false

    @Volatile private var genStartNanos: Long = 0L
    @Volatile private var ttfbMs: Long = -1L

    private var samplesChannel = Channel<FloatArray>(capacity = 128)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SherpaOnnxTtsEngineTheme {
                var initState by remember { mutableStateOf("loading") }

                LaunchedEffect(Unit) {
                    Log.i(TAG, "Start TTS init")
                    val hasTtsModel = withContext(Dispatchers.Default) {
                        TtsEngine.createTts(this@MainActivity)
                    }
                    Log.i(TAG, "Finish TTS init")
                    if (hasTtsModel) {
                        resetAudioTrack()
                        initState = "ready"
                    } else {
                        initState = "missing"
                    }
                }

                when (initState) {
                    "loading" -> LoadingScreen()
                    "missing" -> MissingModelsScreen(getExternalFilesDir(null).toString())
                    else -> PortalTtsScreen()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LoadingScreen() {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ACCENT)
                Spacer(Modifier.height(16.dp))
                Text("Loading voices...", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun MissingModelsScreen(path: String) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Text(
                "No TTS voices installed.\n\nPut model folders under:\n$path",
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun PortalTtsScreen() {
        val context = LocalContext.current
        val preferenceHelper = remember { PreferenceHelper(this@MainActivity) }
        val allVoices = remember { TtsEngine.allInstalledVoices(context) }
        var selectedTab by remember { mutableStateOf("Speak") }
        var selectedVoiceName by remember {
            mutableStateOf(TtsEngine.activeVoiceName ?: preferenceHelper.getVoiceName())
        }
        var testText by remember { mutableStateOf(getSampleText(TtsEngine.lang ?: "")) }
        var startEnabled by remember { mutableStateOf(true) }
        var playEnabled by remember { mutableStateOf(false) }
        var saveEnabled by remember { mutableStateOf(false) }
        var shareEnabled by remember { mutableStateOf(false) }
        var generating by remember { mutableStateOf(false) }
        var liveElapsed by remember { mutableStateOf(0f) }
        var lastStats by remember { mutableStateOf<GenStats?>(null) }
        var recent by remember { mutableStateOf<List<RecentGeneration>>(emptyList()) }
        var currentFileName by remember { mutableStateOf("generated.wav") }
        var benchmarkRunning by remember { mutableStateOf(false) }
        var benchmarkElapsed by remember { mutableStateOf(0f) }
        var benchmarkResults by remember { mutableStateOf<List<BenchmarkResult>>(emptyList()) }

        val selectedVoice = allVoices.firstOrNull { it.name == selectedVoiceName }
            ?: allVoices.firstOrNull()
        val selectedModel = selectedVoice?.let { TtsEngine.modelForVoice(context, it) }

        LaunchedEffect(selectedVoiceName) {
            selectedVoice?.let { voice ->
                testText = getSampleText(voice.locale.isO3Language)
            }
        }

        LaunchedEffect(generating) {
            if (generating) {
                val mark = TimeSource.Monotonic.markNow()
                while (generating) {
                    liveElapsed = mark.elapsedNow().inWholeMilliseconds / 1000f
                    delay(50)
                }
            }
        }

        LaunchedEffect(benchmarkRunning) {
            if (benchmarkRunning) {
                val mark = TimeSource.Monotonic.markNow()
                while (benchmarkRunning) {
                    benchmarkElapsed = mark.elapsedNow().inWholeMilliseconds / 1000f
                    delay(100)
                }
            }
        }

        val saveLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("audio/wav")
        ) { uri ->
            if (uri != null) {
                try {
                    val srcFile = File(application.filesDir, currentFileName)
                    contentResolver.openOutputStream(uri)?.use { output ->
                        srcFile.inputStream().use { it.copyTo(output) }
                    }
                    Toast.makeText(applicationContext, "Audio saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save audio: $e")
                    Toast.makeText(applicationContext, "Failed to save audio", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold { contentPadding ->
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .padding(start = 16.dp, top = 72.dp, end = 16.dp, bottom = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Header(generating)
                    TabButtons(selectedTab) { selectedTab = it }

                    when (selectedTab) {
                        "Speak" -> SpeakTab(
                            voices = allVoices,
                            selectedVoice = selectedVoice,
                            selectedModel = selectedModel,
                            testText = testText,
                            onTextChange = { testText = it },
                            startEnabled = startEnabled,
                            playEnabled = playEnabled,
                            saveEnabled = saveEnabled,
                            shareEnabled = shareEnabled,
                            generating = generating,
                            liveElapsed = liveElapsed,
                            lastStats = lastStats,
                            recent = recent,
                            onVoiceSelected = { voice ->
                                selectedVoiceName = voice.name
                                scope.launch { loadVoiceForUi(voice, preferenceHelper) }
                            },
                            onSpeedChange = {
                                TtsEngine.speed = it
                                preferenceHelper.setSpeed(it)
                            },
                            onSpeak = {
                                val voice = selectedVoice ?: return@SpeakTab
                                if (testText.isBlank()) {
                                    Toast.makeText(applicationContext, "Please input some text", Toast.LENGTH_SHORT).show()
                                    return@SpeakTab
                                }
                                startEnabled = false
                                playEnabled = false
                                saveEnabled = false
                                shareEnabled = false
                                generating = true
                                liveElapsed = 0f
                                lastStats = null
                                val filename = "generated-${System.currentTimeMillis()}.wav"
                                scope.launch {
                                    val stats = synthesizeToFile(testText, voice, preferenceHelper, filename, streamToSpeaker = true)
                                    withContext(Dispatchers.Main) {
                                        generating = false
                                        startEnabled = true
                                        if (stats != null) {
                                            currentFileName = filename
                                            playEnabled = true
                                            saveEnabled = true
                                            shareEnabled = true
                                            lastStats = stats
                                            recent = (listOf(RecentGeneration(testText, voice.name, voiceLabel(voice), stats)) + recent)
                                                .take(10)
                                        }
                                    }
                                }
                            },
                            onPlay = {
                                stopped = true
                                if (::track.isInitialized) {
                                    track.pause()
                                    track.flush()
                                }
                                playFile(currentFileName)
                            },
                            onStop = {
                                onClickStop()
                                startEnabled = true
                                generating = false
                            },
                            onSave = { saveLauncher.launch("portal-tts.wav") },
                            onShare = { shareFile(currentFileName) },
                            onUseRecent = { item ->
                                testText = item.text
                                selectedVoiceName = item.voiceName
                                allVoices.firstOrNull { it.name == item.voiceName }?.let { voice ->
                                    scope.launch { loadVoiceForUi(voice, preferenceHelper) }
                                }
                            }
                        )

                        "Voices" -> VoicesTab(
                            voices = allVoices,
                            selectedVoiceName = selectedVoiceName,
                            defaultVoiceName = preferenceHelper.getVoiceName(),
                            onUse = { voice ->
                                selectedVoiceName = voice.name
                                scope.launch { loadVoiceForUi(voice, preferenceHelper) }
                                selectedTab = "Speak"
                            },
                            onPreview = { voice ->
                                selectedVoiceName = voice.name
                                generating = true
                                liveElapsed = 0f
                                val filename = "preview-${System.currentTimeMillis()}.wav"
                                scope.launch {
                                    val stats = synthesizeToFile(previewTextFor(voice), voice, preferenceHelper, filename, streamToSpeaker = false)
                                    withContext(Dispatchers.Main) {
                                        generating = false
                                        if (stats != null) {
                                            currentFileName = filename
                                            lastStats = stats
                                            playEnabled = true
                                            saveEnabled = true
                                            shareEnabled = true
                                            playFile(filename)
                                        }
                                    }
                                }
                            },
                            onDefault = { voice ->
                                selectedVoiceName = voice.name
                                scope.launch { loadVoiceForUi(voice, preferenceHelper) }
                                Toast.makeText(applicationContext, "Default voice saved", Toast.LENGTH_SHORT).show()
                            }
                        )

                        "Models" -> ModelsTab()

                        "Settings" -> SettingsTab(
                            defaultSynth = Settings.Secure.getString(contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH) ?: "",
                            selectedVoice = selectedVoice,
                            lastStats = lastStats,
                            benchmarkRunning = benchmarkRunning,
                            benchmarkElapsed = benchmarkElapsed,
                            benchmarkResults = benchmarkResults,
                            onSetDefaultEngine = {
                                runCatching {
                                    Settings.Secure.putString(
                                        contentResolver,
                                        Settings.Secure.TTS_DEFAULT_SYNTH,
                                        PORTAL_TTS_PACKAGE,
                                    )
                                }.onSuccess {
                                    Toast.makeText(applicationContext, "Portal TTS set as default", Toast.LENGTH_SHORT).show()
                                }.onFailure { e ->
                                    Log.w(TAG, "Failed to set default TTS engine", e)
                                    Toast.makeText(
                                        applicationContext,
                                        "Could not change the default TTS engine from inside the app",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onRunBenchmarks = {
                                if (benchmarkRunning) return@SettingsTab
                                benchmarkRunning = true
                                benchmarkElapsed = 0f
                                benchmarkResults = emptyList()
                                scope.launch {
                                    val results = mutableListOf<BenchmarkResult>()
                                    allVoices.forEachIndexed { index, voice ->
                                        val filename = "benchmark-$index.wav"
                                        val stats = synthesizeToFile(
                                            text = previewTextFor(voice),
                                            voice = voice,
                                            preferenceHelper = preferenceHelper,
                                            filename = filename,
                                            streamToSpeaker = false
                                        )
                                        if (stats != null) {
                                            val realtime = if (stats.elapsed > 0f) stats.audioDuration / stats.elapsed else 0f
                                            val result = BenchmarkResult(
                                                voiceName = voice.name,
                                                voiceLabel = voiceLabel(voice),
                                                modelName = stats.modelName,
                                                elapsed = stats.elapsed,
                                                audioDuration = stats.audioDuration,
                                                realtime = realtime,
                                                ttfbMs = stats.ttfbMs,
                                            )
                                            results += result
                                            withContext(Dispatchers.Main) {
                                                benchmarkResults = results.sortedByDescending { it.realtime }
                                            }
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        benchmarkRunning = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Header(generating: Boolean) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Portal TTS", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        "Offline neural voices for Portal",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = if (generating) "GENERATING" else "READY",
                    color = if (generating) Color(0xFFE0A100) else Color(0xFF2E9E5B)
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun TabButtons(selectedTab: String, onSelected: (String) -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Speak", "Voices", "Models", "Settings").forEach { tab ->
                val modifier = Modifier.weight(1f)
                if (selectedTab == tab) {
                    Button(onClick = { onSelected(tab) }, modifier = modifier) { Text(tab, fontSize = 12.sp) }
                } else {
                    OutlinedButton(onClick = { onSelected(tab) }, modifier = modifier) { Text(tab, fontSize = 12.sp) }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SpeakTab(
        voices: List<TtsVoiceSpec>,
        selectedVoice: TtsVoiceSpec?,
        selectedModel: TtsModelSpec?,
        testText: String,
        onTextChange: (String) -> Unit,
        startEnabled: Boolean,
        playEnabled: Boolean,
        saveEnabled: Boolean,
        shareEnabled: Boolean,
        generating: Boolean,
        liveElapsed: Float,
        lastStats: GenStats?,
        recent: List<RecentGeneration>,
        onVoiceSelected: (TtsVoiceSpec) -> Unit,
        onSpeedChange: (Float) -> Unit,
        onSpeak: () -> Unit,
        onPlay: () -> Unit,
        onStop: () -> Unit,
        onSave: () -> Unit,
        onShare: () -> Unit,
        onUseRecent: (RecentGeneration) -> Unit,
    ) {
        var voiceMenuOpen by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Voice", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                OutlinedButton(onClick = { voiceMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        selectedVoice?.let { voiceLabel(it) } ?: "No voice",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("v")
                }
                DropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text("${voiceLabel(voice)} - ${modelNameFor(voice)}") },
                            onClick = {
                                onVoiceSelected(voice)
                                voiceMenuOpen = false
                            }
                        )
                    }
                }
            }
            selectedModel?.let {
                Text(
                    "Model: ${it.displayName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Speed  ${String.format("%.1f", TtsEngine.speed)}x", fontSize = 13.sp)
            Slider(
                value = TtsEngine.speedState.value,
                onValueChange = onSpeedChange,
                valueRange = MIN_TTS_SPEED..MAX_TTS_SPEED,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = testText,
                onValueChange = onTextChange,
                label = { Text("Text to speak") },
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                singleLine = false,
            )
            Text(
                "${testText.length} characters",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = startEnabled, onClick = onSpeak) { Text("Speak") }
                Button(enabled = playEnabled, onClick = onPlay) { Text("Replay") }
                Button(onClick = onStop) { Text("Stop") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = saveEnabled, onClick = onSave) { Text("Save WAV") }
                Button(enabled = shareEnabled, onClick = onShare) { Text("Share") }
            }

            if (generating) {
                GenerationCard(liveElapsed)
            } else {
                lastStats?.let { ResultCard(it) }
            }

            if (recent.isNotEmpty()) {
                Text("Recent", fontWeight = FontWeight.Bold)
                recent.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.voiceLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onUseRecent(item) }) { Text("Use") }
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun VoicesTab(
        voices: List<TtsVoiceSpec>,
        selectedVoiceName: String,
        defaultVoiceName: String,
        onUse: (TtsVoiceSpec) -> Unit,
        onPreview: (TtsVoiceSpec) -> Unit,
        onDefault: (TtsVoiceSpec) -> Unit,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Installed Voices", fontWeight = FontWeight.Bold)
            voices.forEach { voice ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(voiceLabel(voice), fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${localeLabel(voice)} - ${modelNameFor(voice)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (voice.name == selectedVoiceName) {
                                StatusPill("ACTIVE", Color(0xFF2E9E5B))
                            } else if (voice.name == defaultVoiceName) {
                                StatusPill("DEFAULT", ACCENT)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onPreview(voice) }) { Text("Preview") }
                            OutlinedButton(onClick = { onUse(voice) }) { Text("Use") }
                            OutlinedButton(onClick = { onDefault(voice) }) { Text("Default") }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ModelsTab() {
        val context = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Model Manager", fontWeight = FontWeight.Bold)
            VoiceCatalog.models.forEach { model ->
                val installed = model.isInstalled(context)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(model.displayName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            StatusPill(if (installed) "INSTALLED" else "MISSING", if (installed) Color(0xFF2E9E5B) else Color(0xFFE0A100))
                        }
                        StatRow("Folder", model.directoryName)
                        StatRow("Voices", model.voiceSpecs.size.toString())
                        StatRow("Model file", model.modelName)
                        Text(
                            model.directory(context).absolutePath,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SettingsTab(
        defaultSynth: String,
        selectedVoice: TtsVoiceSpec?,
        lastStats: GenStats?,
        benchmarkRunning: Boolean,
        benchmarkElapsed: Float,
        benchmarkResults: List<BenchmarkResult>,
        onSetDefaultEngine: () -> Unit,
        onRunBenchmarks: () -> Unit,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Settings", fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatRow("Engine package", packageName)
                    StatRow("Default synth", if (defaultSynth == PORTAL_TTS_PACKAGE) "Portal TTS" else defaultSynth.ifBlank { "Not set" })
                    StatRow("Selected voice", selectedVoice?.let { voiceLabel(it) } ?: "None")
                    Button(onClick = onSetDefaultEngine) { Text("Set Portal TTS default") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Storage", fontWeight = FontWeight.SemiBold)
                    Text(
                        getExternalFilesDir(null).toString(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            lastStats?.let {
                Text("Last Benchmark", fontWeight = FontWeight.Bold)
                ResultCard(it)
            }
            Text("Voice Performance", fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (benchmarkRunning) "Testing voices... ${String.format("%.1f", benchmarkElapsed)} s"
                            else "Run the same short sample through every installed voice.",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(enabled = !benchmarkRunning, onClick = onRunBenchmarks) {
                            Text(if (benchmarkResults.isEmpty()) "Run" else "Run Again")
                        }
                    }
                    if (benchmarkRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ACCENT)
                    }
                    if (benchmarkResults.isNotEmpty()) {
                        BenchmarkTable(benchmarkResults)
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun BenchmarkTable(results: List<BenchmarkResult>) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("#", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                Text("Voice", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                Text("RTF", fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
                Text("First", fontWeight = FontWeight.Bold, modifier = Modifier.width(62.dp))
            }
            results.forEachIndexed { index, result ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", modifier = Modifier.width(28.dp))
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(result.voiceLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(result.modelName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(String.format("%.1fx", result.realtime), modifier = Modifier.width(58.dp))
                    Text(if (result.ttfbMs >= 0) "${result.ttfbMs} ms" else "-", modifier = Modifier.width(62.dp))
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun GenerationCard(liveElapsed: Float) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Generating... ${String.format("%.1f", liveElapsed)} s", fontWeight = FontWeight.SemiBold)
                if (ttfbMs >= 0) {
                    Text(
                        "first audio in ${ttfbMs} ms",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ACCENT)
            }
        }
    }

    private fun modelNameFor(voice: TtsVoiceSpec): String =
        VoiceCatalog.models.firstOrNull { it.id == voice.modelId }?.displayName ?: voice.modelId

    private fun previewTextFor(voice: TtsVoiceSpec): String =
        if (voice.locale.language == "ro") "Salut. Portal TTS este pregatit." else "Portal TTS voice preview."

    private suspend fun loadVoiceForUi(voice: TtsVoiceSpec, preferenceHelper: PreferenceHelper): Boolean {
        val ok = withContext(Dispatchers.Default) {
            TtsEngine.loadVoice(applicationContext, voice.name)
        }
        if (ok) {
            withContext(Dispatchers.Main) {
                TtsEngine.speakerId = voice.sid
                preferenceHelper.setSid(voice.sid)
                preferenceHelper.setVoiceName(voice.name)
                resetAudioTrack()
            }
        }
        return ok
    }

    private suspend fun synthesizeToFile(
        text: String,
        voice: TtsVoiceSpec,
        preferenceHelper: PreferenceHelper,
        filename: String,
        streamToSpeaker: Boolean,
    ): GenStats? {
        if (!loadVoiceForUi(voice, preferenceHelper)) return null
        val tts = TtsEngine.tts ?: return null
        stopped = false
        ttfbMs = -1L
        genStartNanos = System.nanoTime()

        if (streamToSpeaker) {
            withContext(Dispatchers.Main) {
                track.pause()
                track.flush()
                track.play()
            }
            scope.launch {
                for (samples in samplesChannel) {
                    if (samples.isEmpty()) break
                    track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    if (stopped) break
                }
                while (samplesChannel.tryReceive().getOrNull() != null) {
                    // Drain any chunks queued after Stop.
                }
            }
        }

        val startTime = TimeSource.Monotonic.markNow()
        val genConfig = GenerationConfig(sid = voice.sid, speed = TtsEngine.speed)
        if (TtsEngine.isSupertonic) {
            genConfig.extra = mapOf("lang" to TtsEngine.supertonicLang)
        }
        val callback: (FloatArray) -> Int = if (streamToSpeaker) {
            ::callback
        } else {
            { samples ->
                if (samples.isNotEmpty() && ttfbMs < 0 && genStartNanos != 0L) {
                    ttfbMs = (System.nanoTime() - genStartNanos) / 1_000_000
                }
                1
            }
        }
        val audio = withContext(Dispatchers.Default) {
            tts.generateWithConfigAndCallback(text = text, config = genConfig, callback = callback)
        }
        if (streamToSpeaker) {
            scope.launch { samplesChannel.send(FloatArray(0)) }
        }

        val elapsed = startTime.elapsedNow().inWholeMilliseconds.toFloat() / 1000f
        val audioDuration = audio.samples.size / tts.sampleRate().toFloat()
        val output = File(application.filesDir, filename)
        val ok = audio.samples.isNotEmpty() && audio.save(output.absolutePath)
        if (!ok) return null

        return GenStats(
            elapsed = elapsed,
            audioDuration = audioDuration,
            chars = text.length,
            threads = tts.config.model.numThreads,
            ttfbMs = ttfbMs,
            voiceLabel = voiceLabel(voice),
            modelName = modelNameFor(voice),
        )
    }

    override fun onDestroy() {
        stopMediaPlayer()
        if (::track.isInitialized) {
            track.release()
        }
        super.onDestroy()
    }

    private fun stopMediaPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun playFile(filename: String) {
        val file = File(application.filesDir, filename)
        if (!file.exists()) {
            Toast.makeText(applicationContext, "No audio to play", Toast.LENGTH_SHORT).show()
            return
        }
        stopMediaPlayer()
        mediaPlayer = MediaPlayer.create(applicationContext, Uri.fromFile(file))
        mediaPlayer?.start()
    }

    private fun shareFile(filename: String) {
        val file = File(application.filesDir, filename)
        if (!file.exists()) {
            Toast.makeText(applicationContext, "No audio to share", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "com.portal.tts.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share audio"))
    }

    private fun onClickStop() {
        stopped = true
        if (::track.isInitialized) {
            track.pause()
            track.flush()
        }
        stopMediaPlayer()
    }

    private fun callback(samples: FloatArray): Int {
        if (!stopped) {
            if (samples.isNotEmpty() && ttfbMs < 0 && genStartNanos != 0L) {
                ttfbMs = (System.nanoTime() - genStartNanos) / 1_000_000
            }
            val samplesCopy = samples.copyOf()
            scope.launch { samplesChannel.trySend(samplesCopy).isSuccess }
            return 1
        }
        if (::track.isInitialized) {
            track.stop()
        }
        return 0
    }

    private fun resetAudioTrack() {
        if (::track.isInitialized) {
            track.pause()
            track.flush()
            track.release()
        }
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val sampleRate = TtsEngine.tts!!.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()
        track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.play()
    }
}

@androidx.compose.runtime.Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@androidx.compose.runtime.Composable
private fun ResultCard(s: GenStats) {
    val realtime = if (s.elapsed > 0f) s.audioDuration / s.elapsed else 0f
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Benchmark", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusPill(
                    text = String.format("%.1fx realtime", realtime),
                    color = if (realtime >= 1f) Color(0xFF2E9E5B) else Color(0xFFE0A100)
                )
            }
            Spacer(Modifier.height(8.dp))
            StatRow("Voice", s.voiceLabel)
            StatRow("Model", s.modelName)
            StatRow("Generated in", String.format("%.2f s", s.elapsed))
            StatRow("Audio length", String.format("%.2f s", s.audioDuration))
            if (s.ttfbMs >= 0) StatRow("First audio", "${s.ttfbMs} ms")
            StatRow("Characters", "${s.chars}")
            StatRow("Threads", "${s.threads}")
        }
    }
}

@androidx.compose.runtime.Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
