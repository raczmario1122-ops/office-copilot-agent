package com.officecopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class OfflineAutomationApp {
    private static final Logger LOG = Logger.getLogger(OfflineAutomationApp.class.getName());

    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0 ? Paths.get(args[0]) : Paths.get("config", "config.json");
        AutomationConfig config = loadConfigOrDefault(configPath);

        configureLogging(config.logsDirectory);
        Map<String, String> formData = loadFormDataOrDefault(config.dataFile);
        List<String> urls = loadUrlsOrDefault(config.urlsFile);

        List<String> reportLines = new ArrayList<>();
        reportLines.add("Run time: " + LocalDateTime.now());
        reportLines.add("Features list name: Features_list_main");

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(config.headless);
        if (config.chromeBinaryPath != null && !config.chromeBinaryPath.isBlank()) {
            launchOptions.setExecutablePath(Paths.get(config.chromeBinaryPath));
        }

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(launchOptions)) {
            BrowserContext context = browser.newContext();
            Page page = context.newPage();
            page.setDefaultTimeout((double) config.timeoutMillis);

            for (String url : urls) {
                page.navigate(url);
                LOG.info("Opened URL: " + url);
                reportLines.add("Opened URL: " + url);

                for (ActionSpec action : config.actions) {
                    executeAction(page, action, formData, config, reportLines);
                }
            }
        }

        writePdfReport(config.pdfReportPath, reportLines);
        LOG.info("PDF report created: " + config.pdfReportPath);
    }

    private static AutomationConfig loadConfigOrDefault(Path configPath) {
        try {
            if (Files.exists(configPath)) {
                return new ObjectMapper().readValue(configPath.toFile(), AutomationConfig.class);
            }
        } catch (Exception ex) {
            LOG.warning("Config load failed, using defaults: " + ex.getMessage());
        }
        return defaultConfig();
    }

    private static AutomationConfig defaultConfig() {
        AutomationConfig c = new AutomationConfig();
        c.headless = false;
        c.timeoutMillis = 20000;
        c.urlsFile = "data/urls.txt";
        c.dataFile = "data/form-data.txt";
        c.logsDirectory = "logs";
        c.pdfReportPath = "logs/analysis-report.pdf";
        c.tesseractCommand = "tesseract";
        c.actions = List.of(
                action("analyze", null, null, null, 0),
                action("type", "input[name='q']", null, "searchTerm", 0),
                action("wait", null, null, null, 1000),
                action("click", "button[type='submit']", null, null, 0),
                action("analyze", null, null, null, 0)
        );
        return c;
    }

    private static ActionSpec action(String type, String selector, String value, String valueFrom, long waitMillis) {
        ActionSpec a = new ActionSpec();
        a.type = type;
        a.selector = selector;
        a.value = value;
        a.valueFrom = valueFrom;
        a.waitMillis = waitMillis;
        return a;
    }

    private static void configureLogging(String logsDirectory) throws IOException {
        Files.createDirectories(Paths.get(logsDirectory));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        FileHandler fileHandler = new FileHandler(Paths.get(logsDirectory, "run-" + timestamp + ".log").toString());
        fileHandler.setFormatter(new SimpleFormatter());
        LOG.addHandler(fileHandler);
        LOG.setLevel(Level.INFO);
    }

    private static void executeAction(Page page, ActionSpec action, Map<String, String> formData,
                                      AutomationConfig config, List<String> reportLines) {
        try {
            String actionType = action.type == null ? "" : action.type.toLowerCase(Locale.ROOT);
            switch (actionType) {
                case "analyze" -> {
                    String title = page.title();
                    String body = Optional.ofNullable(page.textContent("body")).orElse("");
                    String sample = body.length() > 250 ? body.substring(0, 250) + "..." : body;
                    LOG.info("Page title: " + title);
                    LOG.info("Page text sample: " + sample);
                    reportLines.add("Title: " + title);
                    reportLines.add("Text sample: " + sample);
                }
                case "click" -> {
                    if (action.selector == null || action.selector.isBlank()) return;
                    page.locator(action.selector).first().click();
                    LOG.info("Clicked: " + action.selector);
                    reportLines.add("Clicked: " + action.selector);
                }
                case "type" -> {
                    if (action.selector == null || action.selector.isBlank()) return;
                    String value = action.value;
                    if (action.valueFrom != null && !action.valueFrom.isBlank()) {
                        value = formData.getOrDefault(action.valueFrom, "");
                    }
                    page.locator(action.selector).first().fill(value == null ? "" : value);
                    LOG.info("Typed value into: " + action.selector);
                    reportLines.add("Typed into: " + action.selector);
                }
                case "wait" -> {
                    long millis = action.waitMillis > 0 ? action.waitMillis : 1000;
                    page.waitForTimeout(millis);
                    LOG.info("Waited for " + millis + " ms");
                }
                case "ocrimage" -> {
                    if (action.selector == null || action.selector.isBlank()) return;
                    Files.createDirectories(Paths.get(config.logsDirectory));
                    Path imagePath = Paths.get(config.logsDirectory, "ocr-source.png");
                    byte[] bytes = page.locator(action.selector).first().screenshot();
                    Files.write(imagePath, bytes);
                    String ocrText = runTesseract(config.tesseractCommand, imagePath);
                    LOG.info("OCR text: " + ocrText);
                    reportLines.add("OCR from " + action.selector + ": " + ocrText);
                }
                default -> LOG.warning("Unsupported action type: " + action.type);
            }
        } catch (Exception ex) {
            LOG.severe("Action failed [" + action.type + "] on selector [" + action.selector + "]: " + ex.getMessage());
            reportLines.add("Action failed: " + action.type + " / " + action.selector + " => " + ex.getMessage());
        }
    }

    private static String runTesseract(String cmd, Path imagePath) {
        try {
            Process process = new ProcessBuilder(cmd, imagePath.toString(), "stdout").redirectErrorStream(true).start();
            byte[] out = process.getInputStream().readAllBytes();
            int code = process.waitFor();
            String text = new String(out, StandardCharsets.UTF_8).trim();
            return code == 0 ? text : "OCR command failed (install local Tesseract): " + text;
        } catch (Exception ex) {
            return "OCR unavailable (install local Tesseract): " + ex.getMessage();
        }
    }

    private static void writePdfReport(String pdfPath, List<String> lines) throws IOException {
        Path path = Paths.get(pdfPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float fontSize = 10f;
            float leading = 14.5f;
            float startX = 50f;
            float startY = 750f;
            float bottomMargin = 50f;

            PDPage page = new PDPage();
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            content.beginText();
            content.setFont(font, fontSize);
            content.setLeading(leading);
            content.newLineAtOffset(startX, startY);
            float currentY = startY;

            for (String line : lines) {
                if (currentY <= bottomMargin) {
                    content.endText();
                    content.close();

                    page = new PDPage();
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    content.beginText();
                    content.setFont(font, fontSize);
                    content.setLeading(leading);
                    content.newLineAtOffset(startX, startY);
                    currentY = startY;
                }

                String safe = line == null ? "" : line.replaceAll("[\\r\\n]+", " ");
                content.showText(safe.length() > 120 ? safe.substring(0, 120) : safe);
                content.newLine();
                currentY -= leading;
            }

            content.endText();
            content.close();

            document.save(path.toFile());
        }
    }

    private static List<String> loadUrlsOrDefault(String path) throws IOException {
        if (path == null || path.isBlank() || !Files.exists(Paths.get(path))) {
            return List.of("https://duckduckgo.com");
        }
        return readLines(path);
    }

    private static Map<String, String> loadFormDataOrDefault(String path) throws IOException {
        if (path == null || path.isBlank() || !Files.exists(Paths.get(path))) {
            return Map.of("searchTerm", "office copilot automation");
        }
        return loadFormData(path);
    }

    private static List<String> readLines(String path) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path));
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static Map<String, String> loadFormData(String path) throws IOException {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return loadCsv(path);
        }
        if (lower.endsWith(".txt")) {
            return loadTxt(path);
        }
        if (lower.endsWith(".xlsx")) {
            return loadXlsx(path);
        }
        throw new IllegalArgumentException("Unsupported data file type: " + path);
    }

    private static Map<String, String> loadTxt(String path) throws IOException {
        Map<String, String> map = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(path))) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int sep = trimmed.indexOf('=');
            if (sep > 0) {
                map.put(trimmed.substring(0, sep).trim(), trimmed.substring(sep + 1).trim());
            }
        }
        return map;
    }

    private static Map<String, String> loadCsv(String path) throws IOException {
        try (Reader reader = Files.newBufferedReader(Paths.get(path));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            for (CSVRecord record : parser) {
                Map<String, String> map = new HashMap<>();
                for (String header : parser.getHeaderMap().keySet()) {
                    map.put(header, record.get(header));
                }
                return map;
            }
        }
        return Map.of();
    }

    private static Map<String, String> loadXlsx(String path) throws IOException {
        try (InputStream in = Files.newInputStream(Paths.get(path)); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            Row values = sheet.getRow(1);
            if (header == null || values == null) {
                return Map.of();
            }

            Map<String, String> map = new HashMap<>();
            DataFormatter formatter = new DataFormatter();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                Cell keyCell = header.getCell(i);
                if (keyCell == null) continue;
                String key = formatter.formatCellValue(keyCell).trim();
                if (key.isEmpty()) continue;
                Cell valueCell = values.getCell(i);
                String value = valueCell == null ? "" : formatter.formatCellValue(valueCell);
                map.put(key, value);
            }
            return map;
        }
    }

    public static class AutomationConfig {
        public String chromeBinaryPath;
        public boolean headless = false;
        public long timeoutMillis = 20000;
        public String urlsFile = "data/urls.txt";
        public String dataFile = "data/form-data.txt";
        public String logsDirectory = "logs";
        public String pdfReportPath = "logs/analysis-report.pdf";
        public String tesseractCommand = "tesseract";
        public List<ActionSpec> actions = List.of();
    }

    public static class ActionSpec {
        public String type;
        public String selector;
        public String value;
        public String valueFrom;
        public long waitMillis;
    }
}
