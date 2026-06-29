package com.k2fsa.sherpa.onnx.tts.engine

import PreferenceHelper
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import com.k2fsa.sherpa.onnx.OfflineTts
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

const val MIN_TTS_SPEED = 0.1f
const val MAX_TTS_SPEED = 5.0f

object TtsEngine {
    var tts: OfflineTts? = null
    var activeModelId: String? = null
    var activeVoiceName: String? = null

    /** Named voices for the currently loaded model, in speaker-id order (for the picker). */
    fun currentVoices(context: Context): List<TtsVoiceSpec> =
        activeModelId?.let { id -> VoiceCatalog.findInstalledModel(context, id)?.voiceSpecs }
            ?: emptyList()

    fun allInstalledVoices(context: Context): List<TtsVoiceSpec> =
        VoiceCatalog.installedVoices(context)

    fun modelForVoice(context: Context, voice: TtsVoiceSpec): TtsModelSpec? =
        VoiceCatalog.findInstalledModel(context, voice.modelId)

    // https://en.wikipedia.org/wiki/ISO_639-3
    // Example:
    // eng for English,
    // deu for German
    // cmn for Mandarin
    var lang: String? = null

    // if a model supports two languages, set also lang2
    var lang2: String? = null

    // for Supertonic TTS: language code in ISO 639-1 format, e.g., "en", "zh", "ja"
    var supertonicLang: String = "en"


    val speedState: MutableState<Float> = mutableFloatStateOf(1.0F)
    val speakerIdState: MutableState<Int> = mutableIntStateOf(0)

    var speed: Float
        get() = speedState.value
        set(value) {
            speedState.value = value
        }

    var speakerId: Int
        get() = speakerIdState.value
        set(value) {
            speakerIdState.value = value
        }

    private var assets: AssetManager? = null
    var isSupertonic = false

    init {
        // The purpose of such a design is to make the CI test easier
        // Please see
        // https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py
        //
        // For VITS -- begin
        // For VITS -- end

        // For Matcha -- begin
        // For Matcha -- end

        // For Kokoro -- begin
        // For Kokoro -- end

        lang = null
        lang2 = null

        // Please enable one and only one of the examples below

        // Example 1:
        // https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-vctk.tar.bz2
        // modelDir = "vits-vctk"
        // modelName = "vits-vctk.onnx"
        // lexicon = "lexicon.txt"
        // lang = "eng"

        // Example 2:
        // https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
        // https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2
        // modelDir = "vits-piper-en_US-amy-low"
        // modelName = "en_US-amy-low.onnx"
        // dataDir = "vits-piper-en_US-amy-low/espeak-ng-data"
        // lang = "eng"

        // Example 3:
        // https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2
        // modelDir = "vits-icefall-zh-aishell3"
        // modelName = "model.onnx"
        // ruleFars = "vits-icefall-zh-aishell3/rule.far"
        // lexicon = "lexicon.txt"
        // lang = "zho"

        // Example 4:
        // https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html#csukuangfj-vits-zh-hf-fanchen-c-chinese-187-speakers
        // modelDir = "vits-zh-hf-fanchen-C"
        // modelName = "vits-zh-hf-fanchen-C.onnx"
        // lexicon = "lexicon.txt"
        // lang = "zho"

        // Example 5:
        // https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-coqui-de-css10.tar.bz2
        // This model does not need lexicon or dataDir
        // modelDir = "vits-coqui-de-css10"
        // modelName = "model.onnx"
        // lang = "deu"

        // Example 6
        // vits-melo-tts-zh_en
        // https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html#vits-melo-tts-zh-en-chinese-english-1-speaker
        // modelDir = "vits-melo-tts-zh_en"
        // modelName = "model.onnx"
        // lexicon = "lexicon.txt"
        // lang = "zho"
        // lang2 = "eng"

        // Example 7
        // matcha-icefall-zh-baker
        // https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/matcha.html#matcha-icefall-zh-baker-chinese-1-female-speaker
        // modelDir = "matcha-icefall-zh-baker"
        // acousticModelName = "model-steps-3.onnx"
        // vocoder = "vocos-22khz-univ.onnx"
        // lexicon = "lexicon.txt"
        // lang = "zho"

        // Example 8
        // matcha-icefall-en_US-ljspeech
        // https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/matcha.html#matcha-icefall-en-us-ljspeech-american-english-1-female-speaker
        // modelDir = "matcha-icefall-en_US-ljspeech"
        // acousticModelName = "model-steps-3.onnx"
        // vocoder = "vocos-22khz-univ.onnx"
        // dataDir = "matcha-icefall-en_US-ljspeech/espeak-ng-data"
        // lang = "eng"

        // Example 9
        // kokoro-en-v0_19
        // modelDir = "kokoro-en-v0_19"
        // modelName = "model.onnx"
        // voices = "voices.bin"
        // dataDir = "kokoro-en-v0_19/espeak-ng-data"
        // lang = "eng"

        // Example 10
        // kokoro-multi-lang-v1_0
        // modelDir = "kokoro-multi-lang-v1_0"
        // modelName = "model.onnx"
        // voices = "voices.bin"
        // dataDir = "kokoro-multi-lang-v1_0/espeak-ng-data"
        // lexicon = "kokoro-multi-lang-v1_0/lexicon-us-en.txt,kokoro-multi-lang-v1_0/lexicon-zh.txt"
        // lang = "eng"
        // lang2 = "zho"
        // ruleFsts = "$modelDir/phone-zh.fst,$modelDir/date-zh.fst,$modelDir/number-zh.fst"
        //
        // This model supports many languages, e.g., English, Chinese, etc.
        // We set lang to eng here.

        // Example 11
        // kitten-nano-en-v0_1-fp16
        // modelDir = "kitten-nano-en-v0_1-fp16"
        // modelName = "model.fp16.onnx"
        // voices = "voices.bin"
        // dataDir = "kitten-nano-en-v0_1-fp16/espeak-ng-data"
        // lang = "eng"
        // isKitten = true

        // Example 12
        // matcha-icefall-zh-en
        // https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/matcha-icefall-zh-en.html
        // modelDir = "matcha-icefall-zh-en"
        // acousticModelName = "model-steps-3.onnx"
        // vocoder = "vocos-16khz-univ.onnx"
        // dataDir = "matcha-icefall-zh-en/espeak-ng-data"
        // lexicon = "lexicon.txt"
        // lang = "zho"

        // Example 13
        // supertonic-3-tts (supports 31 languages, default: English)
        // https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
        // modelDir = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
        // isSupertonic = true
        // durationPredictor = "duration_predictor.int8.onnx"
        // textEncoder = "text_encoder.int8.onnx"
        // vectorEstimator = "vector_estimator.int8.onnx"
        // supertonicVocoder = "vocoder.int8.onnx"
        // ttsJson = "tts.json"
        // unicodeIndexer = "unicode_indexer.bin"
        // voiceStyle = "voice.bin"
        // supertonicLang = "en"  // ISO 639-1: en, zh, ja, ko, fr, de, es, etc.
    }

    fun createTts(context: Context): Boolean {
        Log.i(TAG, "Init Next-gen Kaldi TTS")
        if (tts != null) return true

        val helper = PreferenceHelper(context)
        val preferredVoice = VoiceCatalog.findInstalledVoice(context, helper.getVoiceName())
        val voice = preferredVoice ?: VoiceCatalog.installedVoices(context).firstOrNull()
        if (voice == null) {
            Log.e(TAG, "No installed sherpa-onnx TTS models found under ${context.getExternalFilesDir(null)}")
            return false
        }

        return loadVoice(context, voice.name)
    }

    @Synchronized
    fun loadVoice(context: Context, voiceName: String): Boolean {
        val voice = VoiceCatalog.findInstalledVoice(context, voiceName) ?: return false
        val model = VoiceCatalog.findInstalledModel(context, voice.modelId) ?: return false

        if (tts != null && activeModelId == model.id) {
            speakerId = voice.sid
            activeVoiceName = voice.name
            updateLanguageState(voice.locale)
            PreferenceHelper(context).setVoiceName(voice.name)
            return true
        }

        tts?.release()
        tts = null
        activeModelId = null
        activeVoiceName = null

        assets = context.assets
        val config = model.buildConfig(context)

        speed = PreferenceHelper(context).getSpeed()
        speakerId = voice.sid

        tts = OfflineTts(assetManager = null, config = config)
        activeModelId = model.id
        activeVoiceName = voice.name
        updateLanguageState(voice.locale)
        PreferenceHelper(context).setVoiceName(voice.name)
        Log.i(TAG, "Loaded voice ${voice.name} from ${model.directory(context)}")
        return true
    }

    fun resolveVoice(context: Context, requestedName: String?, language: String?): TtsVoiceSpec? {
        val requested = VoiceCatalog.findInstalledVoice(context, requestedName)
        if (requested != null) return requested

        val current = VoiceCatalog.findInstalledVoice(context, activeVoiceName)
        if (current != null && (language.isNullOrBlank() || current.locale.isO3Language == language)) {
            return current
        }

        val voices = VoiceCatalog.installedVoices(context)
        return voices.firstOrNull { it.locale.isO3Language == language } ?: voices.firstOrNull()
    }

    fun isLanguageAvailable(context: Context, lang: String): Boolean =
        VoiceCatalog.installedVoices(context).any { it.locale.isO3Language == lang }

    fun defaultLanguage(context: Context): Array<String> {
        val locale = VoiceCatalog.findInstalledVoice(context, activeVoiceName)?.locale
            ?: VoiceCatalog.installedVoices(context).firstOrNull()?.locale
            ?: Locale.ENGLISH
        return arrayOf(locale.isO3Language, locale.isO3Country, "")
    }

    private fun updateLanguageState(locale: Locale) {
        lang = locale.isO3Language
        lang2 = null
    }

    private fun copyDataDir(context: Context, dataDir: String): String {
        Log.i(TAG, "data dir is $dataDir")
        copyAssets(context, dataDir)

        val newDataDir = context.getExternalFilesDir(null)!!.absolutePath
        Log.i(TAG, "newDataDir: $newDataDir")
        return newDataDir
    }

    private fun copyAssets(context: Context, path: String) {
        val assets: Array<String>?
        try {
            assets = context.assets.list(path)
            if (assets!!.isEmpty()) {
                copyFile(context, path)
            } else {
                val fullPath = "${context.getExternalFilesDir(null)}/$path"
                val dir = File(fullPath)
                dir.mkdirs()
                for (asset in assets.iterator()) {
                    val p: String = if (path == "") "" else "$path/"
                    copyAssets(context, p + asset)
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy $path. $ex")
        }
    }

    private fun copyFile(context: Context, filename: String) {
        try {
            val istream = context.assets.open(filename)
            val newFilename = context.getExternalFilesDir(null).toString() + "/" + filename
            val ostream = FileOutputStream(newFilename)
            // Log.i(TAG, "Copying $filename to $newFilename")
            val buffer = ByteArray(1024)
            var read = 0
            while (read != -1) {
                ostream.write(buffer, 0, read)
                read = istream.read(buffer)
            }
            istream.close()
            ostream.flush()
            ostream.close()
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename, $ex")
        }
    }
}
