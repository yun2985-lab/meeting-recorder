package com.vlab.meetingrecorder

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private data class Line(var speaker: String, var text: String)
    private val lines = mutableListOf<Line>()
    private var recorder: WavRecorder? = null
    private var recordingFile: File? = null
    private var startedAt = 0L
    private var busy = false
    private var txtSelected = false
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var timer: TextView
    private lateinit var subject: EditText
    private lateinit var participants: EditText
    private lateinit var address: EditText
    private lateinit var transcript: LinearLayout
    private lateinit var formatExcel: TextView
    private lateinit var formatText: TextView
    private lateinit var sendButton: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val purple = Color.rgb(105, 58, 240)
    private val ink = Color.rgb(27, 27, 44)
    private val muted = Color.rgb(113, 119, 141)
    private fun dp(x: Int) = (x * resources.displayMetrics.density + .5f).toInt()
    private fun shape(color: Int, radius: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun label(text: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun block(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(18))
        background = shape(Color.WHITE, 20)
        elevation = dp(2).toFloat()
        content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
    }
    private fun field(hint: String): EditText = EditText(this).apply {
        setSingleLine(true); textSize = 14f; setTextColor(ink); setHintTextColor(muted); this.hint = hint
        background = shape(Color.WHITE, 12, Color.rgb(220, 224, 236))
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(248, 249, 255)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content); setBackgroundColor(Color.rgb(248, 249, 255)) }
        setContentView(scroll)
        content.addView(label("회의록 작업실 0.3", 28f, ink, true))
        content.addView(label("음성을 기록하고, 발언을 확인한 뒤 공유하세요.", 15f).apply { setPadding(0,dp(8),0,0) })
        content.addView(label("20분 이하 음성을 분석합니다.", 13f, muted).apply { setPadding(0,dp(4),0,dp(20)) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(actions, LinearLayout.LayoutParams(-1, dp(150)).apply { bottomMargin = dp(16) })
        fun action(title: String, icon: String, subtitle: String, selected: Boolean, click: () -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4),dp(9),dp(4),dp(9))
                background = if (selected) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xff9066f7.toInt(), 0xff247cf2.toInt())).apply { cornerRadius=dp(17).toFloat() } else shape(Color.WHITE)
                elevation = dp(3).toFloat(); setOnClickListener { click() }
            }
            val fg = if (selected) Color.WHITE else ink
            card.addView(label(icon, 32f, if (selected) Color.WHITE else purple).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(57)))
            card.addView(label(title, 16f, fg, true).apply { gravity = Gravity.CENTER })
            card.addView(label(subtitle, 11f, if (selected) Color.WHITE else muted).apply { gravity = Gravity.CENTER })
            actions.addView(card, LinearLayout.LayoutParams(0,-1,1f).apply { marginEnd=dp(7) })
        }
        action("녹음 시작", "♩", "회의를 기록합니다", true) { start() }
        action("녹음 종료", "■", "녹음을 마칩니다", false) { stop() }
        action("자동 분석", "▤", "발언을 분리합니다", false) { analyzeRecording() }
        status = label("녹음 대기", 13f, muted)
        content.addView(status, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        block().apply {
            addView(label("회의 주제 (선택)", 16f, ink, true)); subject = field("예) 주간 회의, 프로젝트 기획 등")
            addView(subject, LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(8); bottomMargin=dp(16) })
            addView(label("참석자 정보 (선택)", 16f, ink, true)); participants = field("예) 김팀장, 이과장, 박대리 …")
            addView(participants, LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(8) })
        }
        block().apply {
            val heading=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL }
            heading.addView(label("발언 내용",18f,ink,true),LinearLayout.LayoutParams(0,-2,1f))
            timer=label("00:00 / 20:00",12f,muted); heading.addView(timer); addView(heading)
            transcript=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.VERTICAL }
            addView(transcript, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) })
            val add=label("⊕  발언 추가",15f,muted,true).apply {
                gravity=Gravity.CENTER; background=shape(0xfff1f3fa.toInt(),12)
                setOnClickListener { editLine(null) }
            }
            addView(add, LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(12) })
        }
        block().apply {
            addView(label("받는 이메일 주소",16f,ink,true))
            address=field("회의록을 받을 이메일 주소를 입력하세요")
            address.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            addView(address, LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(8) })
        }
        val format = block()
        format.addView(label("내보내기 형식",16f,ink,true))
        val choices=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        format.addView(choices, LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(10) })
        formatExcel=label("▣  엑셀 (.xlsx)",14f,ink,true).apply { gravity=Gravity.CENTER; setOnClickListener { txtSelected=false; updateFormat() } }
        formatText=label("☰  텍스트 (.txt)",14f,ink,true).apply { gravity=Gravity.CENTER; setOnClickListener { txtSelected=true; updateFormat() } }
        choices.addView(formatExcel,LinearLayout.LayoutParams(0,-1,1f).apply { marginEnd=dp(6) })
        choices.addView(formatText,LinearLayout.LayoutParams(0,-1,1f).apply { marginStart=dp(6) })
        updateFormat()
        sendButton=label("엑셀 생성 후 이메일 앱 열기  →",17f,Color.WHITE,true).apply {
            gravity=Gravity.CENTER; background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xff9664f2.toInt(),0xff319df2.toInt())).apply { cornerRadius=dp(12).toFloat() }
            setOnClickListener { send() }
        }
        content.addView(sendButton,LinearLayout.LayoutParams(-1,dp(58)).apply { bottomMargin=dp(20) })
        renderLines()
        handler.post(object: Runnable { override fun run() {
            if (recorder != null) { val seconds=(System.currentTimeMillis()-startedAt)/1000; timer.text="%02d:%02d / 20:00".format(seconds/60,seconds%60) }
            handler.postDelayed(this,1000)
        } })
    }
    private fun updateFormat() {
        formatExcel.background=shape(if (txtSelected) 0xfff1f3fa.toInt() else 0xffeee8ff.toInt(),12)
        formatText.background=shape(if (txtSelected) 0xffeee8ff.toInt() else 0xfff1f3fa.toInt(),12)
        formatExcel.setTextColor(if (txtSelected) muted else purple)
        formatText.setTextColor(if (txtSelected) purple else muted)
        if (::sendButton.isInitialized) sendButton.text=if (txtSelected) "텍스트 생성 후 이메일 앱 열기  →" else "엑셀 생성 후 이메일 앱 열기  →"
    }
    private fun renderLines() {
        transcript.removeAllViews()
        if (lines.isEmpty()) { transcript.addView(label("분석 결과가 여기에 표시됩니다. 발언을 직접 추가할 수도 있습니다.",13f,muted)); return }
        lines.forEachIndexed { index, line ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(3),0,dp(3)); setOnClickListener { editLine(index) } }
            val number=label(line.speaker,13f,if(index%2==0) 0xff0876d9.toInt() else purple,true).apply {
                gravity=Gravity.CENTER; background=shape(if(index%2==0) 0xffe8f3ff.toInt() else 0xfff0eaff.toInt(),20)
            }
            row.addView(number,LinearLayout.LayoutParams(dp(114),dp(40)))
            val words=label(line.text,14f,ink).apply { setPadding(dp(12),0,dp(10),0); background=shape(0xfff3f4f8.toInt(),12) }
            row.addView(words,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(8); height=dp(40) })
            transcript.addView(row)
        }
    }
    private fun editLine(index: Int?) {
        val pane=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(8),dp(18),0) }
        val speaker=field("화자 이름 또는 번호").apply { setText(index?.let { lines[it].speaker } ?: "화자 1") }
        val words=field("발언 내용").apply { setSingleLine(false); minLines=3; setText(index?.let { lines[it].text } ?: "") }
        pane.addView(speaker); pane.addView(words,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) })
        val dialog=AlertDialog.Builder(this).setTitle(if(index==null) "발언 추가" else "발언 수정").setView(pane)
            .setPositiveButton("저장",null).setNegativeButton("취소",null)
        if(index!=null) dialog.setNeutralButton("삭제") { _,_ -> lines.removeAt(index); renderLines() }
        val shown=dialog.create(); shown.setOnShowListener {
            shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(speaker.text.isBlank() || words.text.isBlank()) { toast("화자와 발언을 입력하세요"); return@setOnClickListener }
                val line=Line(speaker.text.toString().trim(),words.text.toString().trim())
                if(index==null) lines.add(line) else lines[index]=line
                renderLines(); shown.dismiss()
            }
        }; shown.show()
    }
    private fun start() {
        if(busy || recorder!=null) return
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),1); return }
        try { val file=File(filesDir,"meeting_${System.currentTimeMillis()}.wav"); val r=WavRecorder(file)
            r.start(); recorder=r; recordingFile=file; startedAt=System.currentTimeMillis(); status.text="● 녹음 중 · 종료 버튼을 눌러 저장하세요" }
        catch(e:Exception) { recorder=null; toast("녹음 실패: ${e.message}") }
    }
    private fun stop() {
        val r=recorder ?: return
        try { r.stop(); status.text="녹음 저장됨 · 자동 분석을 시작하세요" }
        catch(e:Exception) { status.text="저장 실패: ${e.message}" }
        finally { recorder=null }
    }
    private fun analyzeRecording() {
        if(recorder!=null) { toast("녹음을 먼저 종료하세요"); return }
        if(busy) return
        val file=recordingFile ?: run { toast("녹음 파일이 없습니다"); return }
        busy=true; status.text="분석 중… 앱을 화면에 유지하세요"
        Thread {
            try {
                val result=OfflineMeetingAnalyzer.analyze(this,file) { percent -> runOnUiThread { status.text="분석 중 $percent%" } }
                runOnUiThread { lines.clear(); lines.addAll(result.map { Line("화자 ${it.speaker+1} (${"%.1f".format(it.start)}초)",it.text) }); renderLines(); busy=false; status.text="분석 완료 · 발언을 눌러 수정할 수 있습니다" }
            } catch(e:Exception) { runOnUiThread { busy=false; status.text="분석 실패: ${e.message}" } }
        }.start()
    }
    private fun send() {
        val email=address.text.toString().trim()
        if(lines.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) { toast("발언과 이메일 주소를 확인하세요"); return }
        try {
            val name="meeting_${System.currentTimeMillis()}"
            val file=File(File(cacheDir,"exports").apply { mkdirs() },name+if(txtSelected) ".txt" else ".xlsx")
            val title=subject.text.toString().trim().ifBlank { "회의 발언 기록" }
            if(txtSelected) file.writeText(buildString {
                appendLine(title); appendLine("참석자: ${participants.text}"); appendLine()
                lines.forEach { appendLine("${it.speaker}: ${it.text}") }
            },Charsets.UTF_8)
            else Xlsx.write(file,lines.map { it.speaker to it.text })
            val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type=if(txtSelected) "text/plain" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_EMAIL,arrayOf(email)); putExtra(Intent.EXTRA_SUBJECT,title)
                putExtra(Intent.EXTRA_STREAM,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },"이메일 앱 선택"))
        } catch(e:Exception) { toast("공유 실패: ${e.message}") }
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); stop(); super.onDestroy() }
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
