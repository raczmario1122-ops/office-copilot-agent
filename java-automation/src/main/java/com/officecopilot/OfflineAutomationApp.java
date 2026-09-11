package com.officecopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
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
        Path configPath = args.length > 0
                ? Paths.get(args[0])
                : Paths.get("config", "config.json");

        AutomationConfig config = new ObjectMapper().readValue(configPath.toFile(), AutomationConfig.class);
        configureLogging(config.logsDirectory);

        Map<String, String> formData = loadFormData(config.dataFile);
        List<String> urls = readLines(config.urlsFile);

        System.setProperty("webdriver.chrome.driver", config.chromeDriverPath);
        ChromeOptions options = new ChromeOptions();
        if (config.chromeBinaryPath != null && !config.chromeBinaryPath.isBlank()) {
            options.setBinary(config.chromeBinaryPath);
        }
        if (config.headless) {
            options.addArguments("--headless=new");
        }

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(config.timeoutSeconds));

        try {
            for (String url : urls) {
                driver.get(url);
                LOG.info("Opened URL: " + url);

                for (ActionSpec action : config.actions) {
                    executeAction(driver, wait, action, formData);
                }
            }
        } finally {
            driver.quit();
        }
    }

    private static void configureLogging(String logsDirectory) throws IOException {
        Files.createDirectories(Paths.get(logsDirectory));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        FileHandler fileHandler = new FileHandler(Paths.get(logsDirectory, "run-" + timestamp + ".log").toString());
        fileHandler.setFormatter(new SimpleFormatter());
        LOG.addHandler(fileHandler);
        LOG.setLevel(Level.INFO);
    }

    private static void executeAction(WebDriver driver, WebDriverWait wait, ActionSpec action, Map<String, String> formData) {
        try {
            switch (action.type.toLowerCase(Locale.ROOT)) {
                case "analyze" -> {
                    String title = driver.getTitle();
                    String bodyText = driver.findElement(By.tagName("body")).getText();
                    String sample = bodyText.length() > 250 ? bodyText.substring(0, 250) + "..." : bodyText;
                    LOG.info("Page title: " + title);
                    LOG.info("Page text sample: " + sample);
                }
                case "click" -> {
                    WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(action.selector)));
                    new Actions(driver).moveToElement(element).perform();
                    element.click();
                    LOG.info("Clicked: " + action.selector);
                }
                case "type" -> {
                    WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(action.selector)));
                    element.clear();
                    String value = action.value;
                    if (action.valueFrom != null && !action.valueFrom.isBlank()) {
                        value = formData.getOrDefault(action.valueFrom, "");
                    }
                    element.sendKeys(value == null ? "" : value);
                    LOG.info("Typed value into: " + action.selector);
                }
                case "wait" -> {
                    long millis = action.waitMillis > 0 ? action.waitMillis : 1000;
                    Thread.sleep(millis);
                    LOG.info("Waited for " + millis + " ms");
                }
                default -> LOG.warning("Unsupported action type: " + action.type);
            }
        } catch (Exception ex) {
            LOG.severe("Action failed [" + action.type + "] on selector [" + action.selector + "]: " + ex.getMessage());
        }
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
                if (keyCell == null) {
                    continue;
                }
                String key = formatter.formatCellValue(keyCell).trim();
                if (key.isEmpty()) {
                    continue;
                }
                Cell valueCell = values.getCell(i);
                String value = valueCell == null ? "" : formatter.formatCellValue(valueCell);
                map.put(key, value);
            }
            return map;
        }
    }

    public static class AutomationConfig {
        public String chromeDriverPath;
        public String chromeBinaryPath;
        public boolean headless = false;
        public int timeoutSeconds = 15;
        public String urlsFile;
        public String dataFile;
        public String logsDirectory = "logs";
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
