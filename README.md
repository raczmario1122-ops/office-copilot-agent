# office-copilot-agent

Offline/local browser automation starter with **Java (Playwright for Java)** and **Kotlin** implementations for Chrome.

## What is included

- `java-automation/`: Java app using Playwright for Java
  - URL list loading
  - Click/type/wait actions
  - Page analysis (title + text sample)
  - Form input typing from `.txt`, `.csv`, `.xlsx`
  - OCR text extraction from browser images (`ocrImage` action, local Tesseract required)
  - PDF analysis report output
  - Built-in defaults so it can run without mandatory config edits

- `kotlin-automation/`: Kotlin app using Playwright for Java bindings
  - URL list loading
  - Click/type/wait actions
  - Page analysis (title + text sample)
  - OCR text extraction from browser images (`ocrImage` action, local Tesseract required)
  - PDF analysis report output
  - Built-in defaults so it can run without mandatory config edits

- `Features_list_main.txt`: initial feature list requested by user

## Local requirements

1. Java 17+
2. Maven 3.9+
3. Google Chrome locally installed
4. (Optional, for OCR) local `tesseract` command available in PATH

---

## Java project (Playwright)

Path: `java-automation/`

### Build and run

```bash
cd java-automation
mvn -DskipTests package
java -jar target/java-automation-1.0.0-jar-with-dependencies.jar
```

Optional config:

```bash
java -jar target/java-automation-1.0.0-jar-with-dependencies.jar config/config.json
```

### Playwright browser install (first time)

```bash
cd java-automation
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chrome"
```

### Build EXE (Windows)

You can do it locally on Windows with `jpackage`:

```powershell
cd C:\path\to\java-automation
mvn -DskipTests package
jpackage --name OfficeWebAutomationJava \
  --input target \
  --main-jar java-automation-1.0.0-jar-with-dependencies.jar \
  --main-class com.officecopilot.OfflineAutomationApp \
  --type exe \
  --dest dist
```

---

## Kotlin project

Path: `kotlin-automation/`

### Build and run

```bash
cd kotlin-automation
mvn -DskipTests package
java -jar target/kotlin-automation-1.0.0-jar-with-dependencies.jar
```

Optional config:

```bash
java -jar target/kotlin-automation-1.0.0-jar-with-dependencies.jar config/config.json
```

### Playwright browser install (first time)

```bash
cd kotlin-automation
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chrome"
```

### Build EXE (Windows)

You can do it locally on Windows with `jpackage`:

```powershell
cd C:\path\to\kotlin-automation
mvn -DskipTests package
jpackage --name OfficeWebAutomationKotlin \
  --input target \
  --main-jar kotlin-automation-1.0.0-jar-with-dependencies.jar \
  --main-class com.officecopilot.kotlin.OfflineAutomationKotlinAppKt \
  --type exe \
  --dest dist
```

---

## Notes

- App is local/offline, while target web pages can be online in Chrome.
- Local config files are supported, but default working parameters are already built in.
- If OCR is needed, install Tesseract locally (`tesseract` command).
