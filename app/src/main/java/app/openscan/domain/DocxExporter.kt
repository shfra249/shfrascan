package app.openscan.domain

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free DOCX writer. Each OCR page becomes a page of paragraphs.
 * The output opens in Word, LibreOffice, Google Docs and Pages.
 */
object DocxExporter {

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    fun export(pages: List<String>, output: File) {
        output.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            write(zip, "[Content_Types].xml", CONTENT_TYPES)
            write(zip, "_rels/.rels", RELS)
            write(zip, "word/document.xml", document(pages))
        }
    }

    fun exportText(pages: List<String>, output: File) {
        output.parentFile?.mkdirs()
        output.writeText(pages.joinToString("\n\n\u000C\n\n"))
    }

    private fun document(pages: List<String>): String {
        val body = StringBuilder()
        pages.forEachIndexed { index, page ->
            page.split('\n').forEach { line ->
                body.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                    .append(escape(line))
                    .append("</w:t></w:r></w:p>")
            }
            if (index != pages.lastIndex) {
                body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr></w:body>
</w:document>"""
    }

    private fun write(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
