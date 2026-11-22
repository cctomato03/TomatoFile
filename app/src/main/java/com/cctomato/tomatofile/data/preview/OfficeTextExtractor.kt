package com.cctomato.tomatofile.data.preview

import com.cctomato.tomatofile.data.model.PreviewKind
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Lightweight, offline Office text extraction. OOXML files are ZIP + XML, so we can show the
 * document contents without pulling Apache POI (which is a poor fit for Android). Legacy binary
 * .doc/.xls/.ppt files are intentionally not parsed; the UI offers "open with" for those.
 */
object OfficeTextExtractor {
    private const val MAX_CHARS = 1_500_000

    fun extract(file: File, kind: PreviewKind): String {
        val extension = file.extension.lowercase()
        return ZipFile(file).use { zip ->
            when (kind) {
                PreviewKind.DOCX -> {
                    val entry = zip.getEntry("word/document.xml")
                        ?: zip.getEntry("content.xml")
                        ?: throw IOException("文档中找不到正文内容")
                    extractDocument(zip.getInputStream(entry), odt = extension == "odt")
                }
                PreviewKind.XLSX -> {
                    if (extension == "ods" || zip.getEntry("xl/workbook.xml") == null) {
                        val entry = zip.getEntry("content.xml")
                            ?: throw IOException("表格中找不到内容")
                        extractDocument(zip.getInputStream(entry), odt = true)
                    } else {
                        extractWorkbook(zip)
                    }
                }
                PreviewKind.PPTX -> {
                    if (extension == "odp" || zip.getEntry("ppt/presentation.xml") == null) {
                        val entry = zip.getEntry("content.xml")
                            ?: throw IOException("演示文稿中找不到内容")
                        extractDocument(zip.getInputStream(entry), odt = true)
                    } else {
                        extractPresentation(zip)
                    }
                }
                else -> throw IOException("不支持此文档格式")
            }
        }
    }

    private fun newParser(input: InputStream): XmlPullParser {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(input, null)
        return parser
    }

    private fun name(parser: XmlPullParser): String =
        parser.name?.substringAfter(':').orEmpty()

    private fun extractDocument(input: InputStream, odt: Boolean): String {
        val output = StringBuilder()
        var inText = false
        var paragraphDepth = 0
        input.use {
            val parser = newParser(it)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (name(parser)) {
                        "p" -> {
                            paragraphDepth++
                            if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
                        }
                        "t" -> inText = true
                        "tab" -> output.append('\t')
                        "br" -> output.append('\n')
                        "tc" -> if (output.isNotEmpty() && output.last() != '\n' &&
                            output.last() != '\t'
                        ) output.append('\t')
                        "s" -> if (odt) output.append(' ')
                    }
                    XmlPullParser.TEXT -> {
                        if (inText || (odt && paragraphDepth > 0)) output.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> when (name(parser)) {
                        "t" -> inText = false
                        "p" -> {
                            paragraphDepth = (paragraphDepth - 1).coerceAtLeast(0)
                            if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
                        }
                    }
                }
                if (output.length >= MAX_CHARS) break
                event = parser.next()
            }
        }
        return finish(output)
    }

    private fun extractWorkbook(zip: ZipFile): String {
        val sharedStrings = readSharedStrings(zip)
        val sheetNames = readSheetNames(zip)
        val sheetEntries = zip.entries().asSequence()
            .filter { it.name.matches(Regex("""xl/worksheets/sheet\d+\.xml""")) }
            .sortedBy { it.name.filter(Char::isDigit).toIntOrNull() ?: 0 }
            .take(20)
            .toList()
        if (sheetEntries.isEmpty()) throw IOException("工作簿中没有工作表")

        val output = StringBuilder()
        for ((index, entry) in sheetEntries.withIndex()) {
            val title = sheetNames.getOrNull(index) ?: "工作表 ${index + 1}"
            output.append("━━ ").append(title).append(" ━━\n")
            parseWorksheet(zip.getInputStream(entry), sharedStrings, output)
            if (output.length >= MAX_CHARS) break
            output.append('\n')
        }
        return finish(output)
    }

    private fun readSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val strings = ArrayList<String>()
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var current: StringBuilder? = null
            var inText = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (name(parser)) {
                        "si" -> current = StringBuilder()
                        "t" -> inText = true
                    }
                    XmlPullParser.TEXT -> if (inText) current?.append(parser.text)
                    XmlPullParser.END_TAG -> when (name(parser)) {
                        "t" -> inText = false
                        "si" -> {
                            strings.add(current?.toString().orEmpty())
                            current = null
                        }
                    }
                }
                event = parser.next()
            }
        }
        return strings
    }

    private fun readSheetNames(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/workbook.xml") ?: return emptyList()
        val names = ArrayList<String>()
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && name(parser) == "sheet") {
                    parser.getAttributeValue(null, "name")?.takeIf { it.isNotBlank() }
                        ?.let(names::add)
                }
                event = parser.next()
            }
        }
        return names
    }

    private fun parseWorksheet(input: InputStream, sharedStrings: List<String>, output: StringBuilder) {
        input.use {
            val parser = newParser(it)
            val cells = HashMap<Int, String>()
            var cellReference: String? = null
            var cellType: String? = null
            var inValue = false
            var inText = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (name(parser)) {
                        "c" -> {
                            cellReference = parser.getAttributeValue(null, "r")
                            cellType = parser.getAttributeValue(null, "t")
                            cells[cellReference?.let(::columnIndex) ?: cells.size] = ""
                            inValue = false
                            inText = false
                        }
                        "v" -> inValue = true
                        "t" -> inText = true
                    }
                    XmlPullParser.TEXT -> if (inValue || inText) {
                        val ref = cellReference?.let(::columnIndex) ?: 0
                        cells[ref] = (cells[ref].orEmpty() + parser.text)
                    }
                    XmlPullParser.END_TAG -> when (name(parser)) {
                        "v" -> inValue = false
                        "t" -> inText = false
                        "c" -> {
                            val ref = cellReference?.let(::columnIndex) ?: 0
                            val raw = cells[ref].orEmpty()
                            cells[ref] = when (cellType) {
                                "s" -> sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                "str", "inlineStr", "e" -> raw
                                else -> raw
                            }
                            cellReference = null
                        }
                        "row" -> {
                            if (cells.isNotEmpty()) {
                                val lastColumn = cells.keys.maxOrNull() ?: 0
                                output.append(
                                    (0..lastColumn).joinToString("\t") { column ->
                                        cells[column].orEmpty()
                                    }
                                ).append('\n')
                                cells.clear()
                            }
                        }
                    }
                }
                if (output.length >= MAX_CHARS) break
                event = parser.next()
            }
        }
    }

    private fun extractPresentation(zip: ZipFile): String {
        val slides = zip.entries().asSequence()
            .filter { it.name.matches(Regex("""ppt/slides/slide\d+\.xml""")) }
            .sortedBy { it.name.filter(Char::isDigit).toIntOrNull() ?: 0 }
            .toList()
        if (slides.isEmpty()) throw IOException("演示文稿中没有幻灯片")
        val output = StringBuilder()
        for ((index, entry) in slides.withIndex()) {
            output.append("━━ 幻灯片 ${index + 1} ━━\n")
            zip.getInputStream(entry).use { input ->
                val parser = newParser(input)
                var inText = false
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (name(parser)) {
                            "p" -> if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
                            "t" -> inText = true
                        }
                        XmlPullParser.TEXT -> if (inText) output.append(parser.text)
                        XmlPullParser.END_TAG -> when (name(parser)) {
                            "t" -> inText = false
                            "p" -> if (output.lastOrNull() != '\n') output.append('\n')
                        }
                    }
                    if (output.length >= MAX_CHARS) break
                    event = parser.next()
                }
            }
            output.append('\n')
            if (output.length >= MAX_CHARS) break
        }
        return finish(output)
    }

    private fun columnIndex(reference: String): Int {
        var value = 0
        for (char in reference) {
            if (char.isLetter()) {
                value = value * 26 + (char.uppercaseChar() - 'A' + 1)
            } else {
                break
            }
        }
        return (value - 1).coerceAtLeast(0)
    }

    private fun finish(output: StringBuilder): String {
        var text = output.toString().trim()
        if (text.isEmpty()) return "（文档中没有可提取的文本）"
        if (output.length >= MAX_CHARS) {
            text = text.take(MAX_CHARS) + "\n\n[内容较长，已截断]"
        }
        return text
    }
}