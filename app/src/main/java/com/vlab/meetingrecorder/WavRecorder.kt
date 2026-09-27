package com.vlab.meetingrecorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/** 16 kHz mono 16-bit PCM WAV, compatible with the offline inference engine. */
internal class WavRecorder(private val file: File) {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var audio: AudioRecord? = null
    fun start() {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(min > 0) { "마이크 초기화 실패" }
        val record = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, min * 2)
        require(record.state == AudioRecord.STATE_INITIALIZED) { record.release(); "마이크를 사용할 수 없습니다" }
        audio = record; running = true; record.startRecording()
        worker = thread(name = "wav-recorder") {
            try {
                RandomAccessFile(file, "rw").use { output ->
                    output.setLength(0); output.write(ByteArray(44))
                    val buf = ByteArray(min)
                    while (running) {
                        val count = record.read(buf, 0, buf.size)
                        if (count > 0) output.write(buf, 0, count)
                    }
                    val length = output.length()
                    fun little(value: Long, bytes: Int) { repeat(bytes) { output.write(((value shr (it * 8)) and 255).toInt()) } }
                    output.seek(0); output.writeBytes("RIFF"); little(length - 8, 4)
                    output.writeBytes("WAVEfmt "); little(16, 4); little(1, 2); little(1, 2)
                    little(16000, 4); little(32000, 4); little(2, 2); little(16, 2)
                    output.writeBytes("data"); little(length - 44, 4)
                }
            } finally { record.release() }
        }
    }
    fun stop() { running = false; audio?.stop(); worker?.join(5000); worker = null; audio = null }
}
