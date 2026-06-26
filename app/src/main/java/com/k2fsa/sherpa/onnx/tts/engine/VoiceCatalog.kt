package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Context
import android.speech.tts.Voice
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File
import java.util.Locale

data class TtsModelSpec(
    val id: String,
    val displayName: String,
    val directoryName: String,
    val modelName: String,
    val voicesFile: String = "",
    val dataDirName: String = "",
    val lexiconFiles: List<String> = emptyList(),
    val ruleFstFiles: List<String> = emptyList(),
    val numThreads: Int? = null,
    val voiceSpecs: List<TtsVoiceSpec>,
) {
    fun directory(context: Context): File = File(context.getExternalFilesDir(null), directoryName)

    fun isInstalled(context: Context): Boolean {
        val dir = directory(context)
        if (!File(dir, modelName).isFile || !File(dir, "tokens.txt").isFile) return false
        if (voicesFile.isNotEmpty() && !File(dir, voicesFile).isFile) return false
        if (dataDirName.isNotEmpty() && !File(dir, dataDirName).isDirectory) return false
        return true
    }

    fun buildConfig(context: Context): OfflineTtsConfig {
        val dir = directory(context).absolutePath
        return getOfflineTtsConfig(
            modelDir = dir,
            modelName = modelName,
            voices = voicesFile,
            lexicon = lexiconFiles.joinToString(",") { "$dir/$it" },
            dataDir = if (dataDirName.isNotEmpty()) "$dir/$dataDirName" else "",
            ruleFsts = ruleFstFiles.joinToString(",") { "$dir/$it" },
            ruleFars = "",
            numThreads = numThreads,
            acousticModelName = "",
            vocoder = "",
            dictDir = "",
            isKitten = false,
            isSupertonic = false,
            durationPredictor = "",
            textEncoder = "",
            vectorEstimator = "",
            supertonicVocoder = "",
            ttsJson = "",
            unicodeIndexer = "",
            voiceStyle = "",
        )
    }
}

data class TtsVoiceSpec(
    val name: String,
    val displayName: String,
    val locale: Locale,
    val sid: Int,
    val quality: Int,
    val modelId: String,
) {
    fun toAndroidVoice(): Voice = Voice(
        name,
        locale,
        quality,
        Voice.LATENCY_HIGH,
        false,
        emptySet()
    )
}

object VoiceCatalog {
    val models: List<TtsModelSpec> = listOf(
        kokoroMultiLangV10English(),
        piperRomanianMihai(),
    )

    fun installedModels(context: Context): List<TtsModelSpec> = models.filter { it.isInstalled(context) }

    fun installedVoices(context: Context): List<TtsVoiceSpec> =
        installedModels(context).flatMap { model -> model.voiceSpecs }

    fun findInstalledVoice(context: Context, name: String?): TtsVoiceSpec? {
        if (name.isNullOrBlank()) return null
        return installedVoices(context).firstOrNull { it.name == name }
    }

    fun findInstalledModel(context: Context, id: String): TtsModelSpec? =
        installedModels(context).firstOrNull { it.id == id }

    private fun kokoroMultiLangV10English(): TtsModelSpec {
        val modelId = "kokoro-multi-lang-v1_0"
        val entries = listOf(
            Triple("af_heart", Locale("en", "US"), Voice.QUALITY_VERY_HIGH),
            Triple("af_alloy", Locale("en", "US"), Voice.QUALITY_NORMAL),
            Triple("af_aoede", Locale("en", "US"), Voice.QUALITY_HIGH),
            Triple("af_bella", Locale("en", "US"), Voice.QUALITY_VERY_HIGH),
            Triple("af_jessica", Locale("en", "US"), Voice.QUALITY_LOW),
            Triple("af_kore", Locale("en", "US"), Voice.QUALITY_HIGH),
            Triple("af_nicole", Locale("en", "US"), Voice.QUALITY_HIGH),
            Triple("af_nova", Locale("en", "US"), Voice.QUALITY_NORMAL),
            Triple("af_river", Locale("en", "US"), Voice.QUALITY_LOW),
            Triple("af_sarah", Locale("en", "US"), Voice.QUALITY_HIGH),
            Triple("af_sky", Locale("en", "US"), Voice.QUALITY_NORMAL),
            Triple("am_adam", Locale("en", "US"), Voice.QUALITY_VERY_LOW),
            Triple("am_echo", Locale("en", "US"), Voice.QUALITY_LOW),
            Triple("am_eric", Locale("en", "US"), Voice.QUALITY_LOW),
            Triple("am_fenrir", Locale("en", "US"), Voice.QUALITY_HIGH),
            Triple("am_liam", Locale("en", "US"), Voice.QUALITY_LOW),
            Triple("am_michael", Locale("en", "US"), Voice.QUALITY_HIGH),
            Triple("am_onyx", Locale("en", "US"), Voice.QUALITY_LOW),
            Triple("am_puck", Locale("en", "US"), Voice.QUALITY_HIGH),
            Triple("am_santa", Locale("en", "US"), Voice.QUALITY_LOW),
            Triple("bf_alice", Locale("en", "GB"), Voice.QUALITY_LOW),
            Triple("bf_emma", Locale("en", "GB"), Voice.QUALITY_HIGH),
            Triple("bf_isabella", Locale("en", "GB"), Voice.QUALITY_NORMAL),
            Triple("bf_lily", Locale("en", "GB"), Voice.QUALITY_LOW),
            Triple("bm_daniel", Locale("en", "GB"), Voice.QUALITY_LOW),
            Triple("bm_fable", Locale("en", "GB"), Voice.QUALITY_NORMAL),
            Triple("bm_george", Locale("en", "GB"), Voice.QUALITY_NORMAL),
            Triple("bm_lewis", Locale("en", "GB"), Voice.QUALITY_LOW),
        ).mapIndexed { sid, entry ->
            val voiceId = entry.first
            TtsVoiceSpec(
                name = "sherpa-kokoro-$voiceId",
                displayName = "Kokoro $voiceId",
                locale = entry.second,
                sid = sid,
                quality = entry.third,
                modelId = modelId,
            )
        }

        return TtsModelSpec(
            id = modelId,
            displayName = "Kokoro English",
            directoryName = "kokoro-multi-lang-v1_0",
            modelName = "model.onnx",
            voicesFile = "voices.bin",
            dataDirName = "espeak-ng-data",
            lexiconFiles = listOf("lexicon-us-en.txt", "lexicon-gb-en.txt", "lexicon-zh.txt"),
            ruleFstFiles = listOf("phone-zh.fst", "date-zh.fst", "number-zh.fst"),
            numThreads = 4,
            voiceSpecs = entries,
        )
    }

    private fun piperRomanianMihai(): TtsModelSpec {
        val modelId = "vits-piper-ro_RO-mihai-medium"
        return TtsModelSpec(
            id = modelId,
            displayName = "Piper Romanian Mihai",
            directoryName = "vits-piper-ro_RO-mihai-medium",
            modelName = "ro_RO-mihai-medium.onnx",
            dataDirName = "espeak-ng-data",
            numThreads = 4,
            voiceSpecs = listOf(
                TtsVoiceSpec(
                    name = "sherpa-piper-ro_RO-mihai-medium",
                    displayName = "Romanian mihai",
                    locale = Locale("ro", "RO"),
                    sid = 0,
                    quality = Voice.QUALITY_HIGH,
                    modelId = modelId,
                )
            ),
        )
    }
}
