# office-copilot-agent

Offline/local browser automation starter with **Java** and **C++** implementations for Chrome.

## What is included

- `java-automation/`: Java app using Selenium + ChromeDriver
  - URL list loading
  - Mouse move + click
  - Page analysis (title + text sample)
  - Form input typing from `.txt`, `.csv`, or `.xlsx`
  - Local run logs
  - Maven build + `jpackage` EXE instructions

- `cpp-automation/`: C++ app using ChromeDriver WebDriver HTTP API
  - URL list loading
  - Click and type actions
  - Basic page analysis (title)
  - Data from `.txt` or `.csv`
  - CMake build to native executable

## Prerequisites (offline/local)

1. Install Google Chrome locally.
2. Install matching ChromeDriver locally.
3. Keep ChromeDriver path/URL in the app config files.
4. Prepare local data files (`txt/csv/xlsx`) and URL file.

---

## Java project

Path: `/home/runner/work/office-copilot-agent/office-copilot-agent/java-automation`

### Configure

Edit:
- `/home/runner/work/office-copilot-agent/office-copilot-agent/java-automation/config/config.json`

Set:
- `chromeDriverPath`
- `chromeBinaryPath`
- `urlsFile`
- `dataFile`
- `actions`

### Build and run

```bash
cd /home/runner/work/office-copilot-agent/office-copilot-agent/java-automation
mvn -DskipTests package
java -jar target/java-automation-1.0.0-jar-with-dependencies.jar config/config.json
```

### Build EXE from Java

Use `jpackage` on Windows:

```powershell
cd C:\path\to\java-automation
mvn -DskipTests package
jpackage --name OfficeWebAutomation \
  --input target \
  --main-jar java-automation-1.0.0-jar-with-dependencies.jar \
  --main-class com.officecopilot.OfflineAutomationApp \
  --type exe \
  --dest dist
```

This creates a Windows `.exe` installer under `dist`.

---

## C++ project

Path: `/home/runner/work/office-copilot-agent/office-copilot-agent/cpp-automation`

### Configure

Edit:
- `/home/runner/work/office-copilot-agent/office-copilot-agent/cpp-automation/config/config.json`

Set:
- `chromeDriverUrl` (default `http://127.0.0.1:9515`)
- `urlsFile`
- `dataFile`
- `actions`

### Build and run

```bash
cd /home/runner/work/office-copilot-agent/office-copilot-agent/cpp-automation
cmake -S . -B build
cmake --build build --config Release
./build/cpp_automation config/config.json
```

### Build EXE on Windows

Build with MSVC/MinGW via CMake; output will be `cpp_automation.exe` in your build folder.

---

## Notes

- The Java implementation is the recommended primary solution.
- C++ implementation is a practical local starter using WebDriver HTTP directly.
- For large Excel-driven workflows, prefer Java (`.xlsx` support is included there).
