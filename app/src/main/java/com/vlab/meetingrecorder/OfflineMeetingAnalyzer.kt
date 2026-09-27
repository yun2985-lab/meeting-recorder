package com.vlab.meetingrecorder

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

/** Models and inference run entirely on device; results require human review. */
internal object OfflineMeetingAnalyzer {
    data class Line(val start: Float, val end: Float, val speaker: Int, val text: String)

    fun analyze(context: Context, wav: File, progress: (Int) -> Unit): List<Line> {
        val samples = readWav(wav)
        val diarization = OfflineSpeakerDiarization(
            assetManager = context.assets,
            config = OfflineSpeakerDiarizationConfig(
                segmentation = OfflineSpeakerSegmentationModelConfig(
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig("segmentation.onnx", 0.1f)
                ),
                embedding = SpeakerEmbeddingExtractorConfig(model = "embedding.onnx", numThreads = 2),
                clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f)
            )
        )
        val recognizer = OfflineRecognizer(
            assetManager = context.assets,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(model = "model.int8.onnx", language = "ko"),
                    tokens = "tokens.txt", numThreads = 2
                )
            )
        )
        try {
            require(diarization.sampleRate() == 16000) { "모델 샘플레이트가 16 kHz가 아닙니다" }
            val segments = diarization.process(samples)
            val lines = mutableListOf<Line>()
            segments.forEachIndexed { index, segment ->
                val startSample = (segment.start * 16000).toInt().coerceIn(0, samples.size)
                val endSample = (segment.end * 16000).toInt().coerceIn(startSample, samples.size)
                // SenseVoice accepts short utterances. Long turns are split without pretending
                // to have word-level timestamps.
                var cursor = startSample
                while (cursor < endSample) {
                    val end = min(cursor + 20 * 16000, endSample)
                    val stream = recognizer.createStream()
                    try {
                        stream.acceptWaveform(samples.copyOfRange(cursor, end), 16000)
                        recognizer.decode(stream)
                        val text = recognizer.getResult(stream).text.trim()
                        if (text.isNotEmpty()) lines += Line(cursor / 16000f, end / 16000f, segment.speaker, text)
                    } finally { stream.release() }
                    cursor = end
                }
                progress((index + 1) * 100 / segments.size.coerceAtLeast(1))
            }
            return lines.sortedBy { it.start }
        } finally { recognizer.release(); diarization.release() }
    }

    private fun readWav(file: File): FloatArray = RandomAccessFile(file, "r").use { input ->
        require(input.length() >= 44 && input.length() <= 44L + 16000L * 2 * 60 * 20) { "20분 이하 녹음만 분석할 수 있습니다" }
        val header = ByteArray(44); input.readFully(header)
        require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") { "WAV 파일이 아닙니다" }
        val count = ((input.length() - 44) / 2).toInt()
        FloatArray(count) {
            val lo = input.readUnsignedByte(); val hi = input.readUnsignedByte()
            (((hi shl 8) or lo).toShort().toFloat() / 32768f)
        }
    }
}
