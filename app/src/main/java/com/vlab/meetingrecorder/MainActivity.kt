package com.vlab.meetingrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private var recorder: MediaRecorder? = null
    private val rows = mutableListOf<Pair<String, String>>()
    private lateinit var speaker: EditText
    private lateinit var words: EditText
    private lateinit var address: EditText
    private lateinit var log: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply { orientation = 1; setPadding(28,28,28,28) }
        setContentView(ScrollView(this).apply { addView(column) })
        column.addView(TextView(this).apply { text = "회의록 작업실 0.1"; textSize = 26f })
        column.addView(TextView(this).apply { text = "녹음 후 화자와 발언을 직접 입력합니다. 자동 분석은 아직 제공되지 않습니다." })
        fun button(label: String, action: () -> Unit) { column.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
        fun input(hintText: String) = EditText(this).also { it.hint = hintText; column.addView(it) }
        button("녹음 시작") { start() }; button("녹음 종료") { stop() }
        speaker = input("화자"); words = input("발언 내용")
        log = TextView(this).also { column.addView(it) }
        button("발언 추가") { if (speaker.text.isNotBlank() && words.text.isNotBlank()) { rows.add(speaker.text.toString() to words.text.toString()); log.text = rows.joinToString("\n") { "${it.first}: ${it.second}" }; words.text.clear() } }
        address = input("받는 이메일 주소")
        button("엑셀 생성 후 이메일 앱 열기") { send() }
    }
    private fun start() {
        if (recorder != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return }
        try {
            val file = File(filesDir, "meeting_${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION") val r = MediaRecorder()
            recorder = r
            r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setOutputFile(file.absolutePath)
            r.prepare(); r.start(); toast("녹음 중: ${file.name}")
        } catch (e: Exception) { recorder?.release(); recorder = null; toast("녹음 실패: ${e.message}") }
    }
    private fun stop() {
        val r = recorder ?: return
        try { r.stop(); toast("녹음 저장됨") } catch (e: Exception) { toast("저장 실패: ${e.message}") }
        finally { r.release(); recorder = null }
    }
    private fun send() {
        val email = address.text.toString().trim()
        if (rows.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) { toast("발언과 이메일 주소를 확인하세요"); return }
        try {
            val file = File(File(cacheDir, "exports").apply { mkdirs() }, "meeting_${System.currentTimeMillis()}.xlsx")
            Xlsx.write(file, rows)
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email)); putExtra(Intent.EXTRA_SUBJECT, "회의 발언 기록")
                putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "이메일 앱 선택"))
        } catch (e: Exception) { toast("공유 실패: ${e.message}") }
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    override fun onDestroy() { stop(); super.onDestroy() }
}

private object Xlsx {
    private fun escape(s: String) = s.filter { it == '\n' || it == '\t' || it == '\r' || it.code >= 32 }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    fun write(file: File, rows: List<Pair<String, String>>) {
        ZipOutputStream(FileOutputStream(file)).use { out ->
            fun part(name: String, xml: String) { out.putNextEntry(ZipEntry(name)); out.write(xml.toByteArray()); out.closeEntry() }
            part("[Content_Types].xml", """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            part("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            part("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="발언" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            part("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val body = (listOf("화자" to "발언 내용") + rows).mapIndexed { i, pair ->
                "<row r=\"${i+1}\">" + listOf(pair.first, pair.second).mapIndexed { j, value ->
                    "<c r=\"${'A'+j}${i+1}\" t=\"inlineStr\"><is><t>${escape(value)}</t></is></c>"
                }.joinToString("") + "</row>"
            }.joinToString("")
            part("xl/worksheets/sheet1.xml", """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$body</sheetData></worksheet>""")
        }
    }
}
