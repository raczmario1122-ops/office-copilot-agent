package com.officecopilot.kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.apache.commons.csv.CSVFormat
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime

data class ActionSpec(
    var type: String? = null,
    var selector: String? = null,
    var value: String? = null,
    var valueFrom: String? = null,
    var waitMillis: Long = 0
)

data class AutomationConfig(
    var chromeBinaryPath: String = "",
    var headless: Boolean = false,
    var timeoutMillis: Long = 20000,
    var urlsFile: String = "data/urls.txt",
    var dataFile: String = "data/form-data.txt",
    var logsDirectory: String = "logs",
    var pdfReportPath: String = "logs/analysis-report-kotlin.pdf",
    var tesseractCommand: String = "tesseract",
    var actions: List<ActionSpec> = listOf(
        ActionSpec(type = "analyze"),
        ActionSpec(type = "type", selector = "input[name='q']", valueFrom = "searchTerm"),
        ActionSpec(type = "wait", waitMillis = 1000),
        ActionSpec(type = "click", selector = "button[type='submit']"),
        ActionSpec(type = "analyze")
    )
)

fun main(args: Array<String>) {
    val configPath = if (args.isNotEmpty()) Paths.get(args[0]) else Paths.get("config", "config.json")
    val config = loadConfigOrDefault(configPath)

    Files.createDirectories(Paths.get(config.logsDirectory))
    val urls = loadUrlsOrDefault(config.urlsFile)
    val data = loadDataOrDefault(config.dataFile)
    val report = mutableListOf("Run time: ${LocalDateTime.now()}", "Features list name: Features_list_main")

    Playwright.create().use { playwright ->
        val options = BrowserType.LaunchOptions().setHeadless(config.headless)
        if (config.chromeBinaryPath.isNotBlank()) {
            options.setExecutablePath(Paths.get(config.chromeBinaryPath))
        }
        val browser = playwright.chromium().launch(options)
        runFlow(browser, config, urls, data, report)
        browser.close()
    }

    writePdf(config.pdfReportPath, report)
}

private fun runFlow(
    browser: Browser,
    config: AutomationConfig,
    urls: List<String>,
    data: Map<String, String>,
    report: MutableList<String>
) {
    val context = browser.newContext()
    val page = context.newPage()
    page.setDefaultTimeout(config.timeoutMillis.toDouble())

    for (url in urls) {
        page.navigate(url)
        report += "Opened URL: $url"
        for (action in config.actions) {
            executeAction(page, action, data, config, report)
        }
    }
    context.close()
}

private fun executeAction(
    page: Page,
    action: ActionSpec,
    data: Map<String, String>,
    config: AutomationConfig,
    report: MutableList<String>
) {
    try {
        when (action.type?.lowercase()) {
            "analyze" -> {
                val title = page.title()
                val body = page.textContent("body") ?: ""
                val sample = if (body.length > 250) body.substring(0, 250) + "..." else body
                report += "Title: $title"
                report += "Text sample: $sample"
            }
            "click" -> {
                val selector = action.selector ?: return
                page.locator(selector).first().click()
                report += "Clicked: $selector"
            }
            "type" -> {
                val selector = action.selector ?: return
                val value = action.valueFrom?.let { data[it] } ?: action.value.orEmpty()
                page.locator(selector).first().fill(value)
                report += "Typed into: $selector"
            }
            "wait" -> page.waitForTimeout(if (action.waitMillis > 0) action.waitMillis.toDouble() else 1000.0)
            "ocrimage" -> {
                val selector = action.selector ?: return
                val image = Paths.get(config.logsDirectory, "ocr-source-kotlin.png")
                val bytes = page.locator(selector).first().screenshot()
                Files.write(image, bytes)
                val ocrText = runTesseract(config.tesseractCommand, image)
                report += "OCR from $selector: $ocrText"
            }
        }
    } catch (e: Exception) {
        report += "Action failed: ${action.type} / ${action.selector} => ${e.message}"
    }
}

private fun runTesseract(command: String, imagePath: Path): String {
    return try {
        val process = ProcessBuilder(command, imagePath.toString(), "stdout").redirectErrorStream(true).start()
        val text = String(process.inputStream.readAllBytes(), StandardCharsets.UTF_8).trim()
        val code = process.waitFor()
        if (code == 0) text else "OCR command failed: $text"
    } catch (e: Exception) {
        "OCR unavailable (install local Tesseract): ${e.message}"
    }
}

private fun writePdf(pdfPath: String, lines: List<String>) {
    val path = Paths.get(pdfPath)
    path.parent?.let { Files.createDirectories(it) }

    PDDocument().use { doc ->
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val fontSize = 10f
        val leading = 14f
        val startX = 50f
        val startY = 750f
        val bottomMargin = 50f

        var page = PDPage()
        doc.addPage(page)
        var content = PDPageContentStream(doc, page)
        content.beginText()
        content.setFont(font, fontSize)
        content.setLeading(leading)
        content.newLineAtOffset(startX, startY)
        var currentY = startY

        for (line in lines) {
            if (currentY <= bottomMargin) {
                content.endText()
                content.close()

                page = PDPage()
                doc.addPage(page)
                content = PDPageContentStream(doc, page)
                content.beginText()
                content.setFont(font, fontSize)
                content.setLeading(leading)
                content.newLineAtOffset(startX, startY)
                currentY = startY
            }

            val safe = line.replace(Regex("[\\r\\n]+"), " ")
            content.showText(if (safe.length > 120) safe.substring(0, 120) else safe)
            content.newLine()
            currentY -= leading
        }

        content.endText()
        content.close()
        doc.save(path.toFile())
    }
}

private fun loadConfigOrDefault(path: Path): AutomationConfig {
    return try {
        if (Files.exists(path)) ObjectMapper().readValue(path.toFile(), AutomationConfig::class.java)
        else AutomationConfig()
    } catch (_: Exception) {
        AutomationConfig()
    }
}

private fun loadUrlsOrDefault(path: String): List<String> {
    val p = Paths.get(path)
    if (!Files.exists(p)) return listOf("https://duckduckgo.com")
    return Files.readAllLines(p).map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
}

private fun loadDataOrDefault(path: String): Map<String, String> {
    val p = Paths.get(path)
    if (!Files.exists(p)) return mapOf("searchTerm" to "office copilot kotlin automation")
    return if (path.lowercase().endsWith(".csv")) loadCsv(path) else loadTxt(path)
}

private fun loadTxt(path: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    for (line in Files.readAllLines(Paths.get(path))) {
        val t = line.trim()
        if (t.isBlank() || t.startsWith("#")) continue
        val idx = t.indexOf('=')
        if (idx > 0) result[t.substring(0, idx).trim()] = t.substring(idx + 1).trim()
    }
    return result
}

private fun loadCsv(path: String): Map<String, String> {
    Files.newBufferedReader(Paths.get(path)).use { reader ->
        CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader).use { parser ->
            val first = parser.firstOrNull() ?: return emptyMap()
            val map = mutableMapOf<String, String>()
            for (h in parser.headerMap.keys) map[h] = first.get(h)
            return map
        }
    }
}
