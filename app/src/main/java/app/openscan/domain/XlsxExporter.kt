package app.openscan.domain

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Turns OCR text into a spreadsheet. Lines are split into columns on tabs or runs of
 * two or more spaces, which is how Tesseract usually preserves table alignment.
 *
 * The XLSX writer produces a minimal but valid SpreadsheetML package with inline
 * strings, so no extra Apache POI dependency is needed on Android.
 */
object XlsxExporter {

    private const val MAX_COLUMNS = 24

    fun exportXlsx(pages: List<String>, output: File) {
        val rows = buildRows(pages)
        output.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            write(zip, "[Content_Types].xml", CONTENT_TYPES)
            write(zip, "_rels/.rels", ROOT_RELS)
            write(zip, "xl/workbook.xml", WORKBOOK)
            write(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            write(zip, "xl/styles.xml", STYLES)
            write(zip, "xl/worksheets/sheet1.xml", sheet(rows))
        }
    }

    fun exportCsv(pages: List<String>, output: File) {
        val rows = buildRows(pages)
        output.parentFile?.mkdirs()
        output.writeText(
            buildString {
                append('\uFEFF')
                rows.forEach { row ->
                    append(row.joinToString(",") { csvCell(it) })
                    append("\r\n")
                }
            }
        )
    }

    private fun buildRows(pages: List<String>): List<List<String>> {
        val parsed = pages.map { page ->
            page.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { splitCells(it) }
        }
        val maxColumns = (parsed.flatten().maxOfOrNull { it.size } ?: 1)
            .coerceIn(1, MAX_COLUMNS)
        val header = if (maxColumns == 1) {
            listOf("Page", "Text")
        } else {
            listOf("Page") + (1..maxColumns).map { "Column $it" }
        }
        val rows = mutableListOf(header)
        parsed.forEachIndexed { pageIndex, lines ->
            lines.forEach { cells ->
                rows += listOf((pageIndex + 1).toString()) + cells.take(maxColumns)
            }
        }
        return rows
    }

    private fun splitCells(line: String): List<String> {
        val cells = when {
            line.contains('\t') -> line.split('\t').map { it.trim() }
            else -> line.split(Regex("\\s{2,}")).map { it.trim() }
        }.dropLastWhile { it.isEmpty() }
        return if (cells.size <= 1) listOf(line) else cells.take(MAX_COLUMNS)
    }

    private fun sheet(rows: List<List<String>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIndex, cells ->
            val rowNumber = rowIndex + 1
            append("<row r=\"$rowNumber\">")
            cells.forEachIndexed { columnIndex, value ->
                append("<c r=\"")
                append(columnName(columnIndex))
                append(rowNumber)
                append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                append(escapeXml(value))
                append("</t></is></c>")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun columnName(index: Int): String {
        var value = index
        val name = StringBuilder()
        while (value >= 0) {
            name.insert(0, ('A' + value % 26))
            value = value / 26 - 1
        }
        return name.toString()
    }

    private fun escapeXml(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                '\n', '\r', '\t' -> append(' ')
                else -> if (char.code == 0x09 || char.code >= 0x20) append(char)
            }
        }
    }

    private fun csvCell(value: String): String {
        val clean = value.filter { it == '\t' || it.code >= 0x20 }.replace('\n', ' ')
        return if (clean.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"" + clean.replace("\"", "\"\"") + "\""
        } else {
            clean
        }
    }

    private fun write(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Scan" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}
