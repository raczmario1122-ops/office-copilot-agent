#include <httplib.h>
#include <nlohmann/json.hpp>

#include <chrono>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

using json = nlohmann::json;

namespace {
constexpr const char* kElementKey = "element-6066-11e4-a52e-4f735466cecf";

struct UrlParts {
    std::string scheme;
    std::string host;
    int port;
    std::string path;
};

UrlParts parseUrl(const std::string& url) {
    std::string scheme;
    std::string rest;

    if (url.rfind("http://", 0) == 0) {
        scheme = "http";
        rest = url.substr(7);
    } else if (url.rfind("https://", 0) == 0) {
        scheme = "https";
        rest = url.substr(8);
    } else {
        throw std::runtime_error("Unsupported URL scheme: " + url);
    }

    auto slashPos = rest.find('/');
    std::string hostPort = slashPos == std::string::npos ? rest : rest.substr(0, slashPos);
    std::string path = slashPos == std::string::npos ? "/" : rest.substr(slashPos);

    auto colonPos = hostPort.find(':');
    int defaultPort = scheme == "https" ? 443 : 80;
    if (colonPos == std::string::npos) {
        return {scheme, hostPort, defaultPort, path};
    }

    std::string host = hostPort.substr(0, colonPos);
    int port = std::stoi(hostPort.substr(colonPos + 1));
    return {scheme, host, port, path};
}

template <typename TClient>
std::string executeRequest(TClient& client, const std::string& method, const std::string& path, const std::string& body) {
    client.set_connection_timeout(10, 0);
    client.set_read_timeout(30, 0);

    httplib::Result res;
    if (method == "GET") {
        res = client.Get(path);
    } else if (method == "POST") {
        res = client.Post(path, body, "application/json");
    } else if (method == "DELETE") {
        res = client.Delete(path);
    } else {
        throw std::runtime_error("Unsupported HTTP method: " + method);
    }

    if (!res) {
        throw std::runtime_error("HTTP request failed at path: " + path);
    }
    if (res->status < 200 || res->status >= 300) {
        throw std::runtime_error("HTTP status " + std::to_string(res->status) + " body: " + res->body);
    }
    return res->body;
}

std::string httpRequest(const std::string& method, const std::string& url, const json* payload = nullptr) {
    UrlParts p = parseUrl(url);
    std::string body = payload ? payload->dump() : "";

    if (p.scheme == "http") {
        httplib::Client client(p.host, p.port);
        return executeRequest(client, method, p.path, body);
    }

#ifdef CPPHTTPLIB_OPENSSL_SUPPORT
    httplib::SSLClient client(p.host, p.port);
    return executeRequest(client, method, p.path, body);
#else
    throw std::runtime_error("https is not enabled in this build: " + url);
#endif
}

std::vector<std::string> readNonEmptyLines(const std::string& filePath) {
    std::ifstream file(filePath);
    if (!file) throw std::runtime_error("Cannot open file: " + filePath);

    std::vector<std::string> lines;
    std::string line;
    while (std::getline(file, line)) {
        if (!line.empty() && line[0] != '#') {
            lines.push_back(line);
        }
    }
    return lines;
}

std::unordered_map<std::string, std::string> loadTxtData(const std::string& filePath) {
    std::ifstream file(filePath);
    if (!file) throw std::runtime_error("Cannot open file: " + filePath);

    std::unordered_map<std::string, std::string> data;
    std::string line;
    while (std::getline(file, line)) {
        if (line.empty() || line[0] == '#') continue;
        auto pos = line.find('=');
        if (pos == std::string::npos) continue;
        data[line.substr(0, pos)] = line.substr(pos + 1);
    }
    return data;
}

std::unordered_map<std::string, std::string> loadCsvData(const std::string& filePath) {
    std::ifstream file(filePath);
    if (!file) throw std::runtime_error("Cannot open file: " + filePath);

    std::string headerLine;
    std::string valuesLine;
    if (!std::getline(file, headerLine) || !std::getline(file, valuesLine)) {
        return {};
    }

    auto split = [](const std::string& line) {
        std::vector<std::string> parts;
        std::stringstream ss(line);
        std::string part;
        while (std::getline(ss, part, ',')) {
            parts.push_back(part);
        }
        return parts;
    };

    auto headers = split(headerLine);
    auto values = split(valuesLine);

    std::unordered_map<std::string, std::string> data;
    for (size_t i = 0; i < headers.size() && i < values.size(); ++i) {
        data[headers[i]] = values[i];
    }
    return data;
}

std::unordered_map<std::string, std::string> loadData(const std::string& filePath) {
    if (filePath.size() >= 4 && filePath.substr(filePath.size() - 4) == ".txt") {
        return loadTxtData(filePath);
    }
    if (filePath.size() >= 4 && filePath.substr(filePath.size() - 4) == ".csv") {
        return loadCsvData(filePath);
    }
    throw std::runtime_error("Unsupported data file type for C++ app. Use .txt or .csv.");
}

std::string createSession(const std::string& chromeDriverUrl, bool headless) {
    json caps = {
        {"capabilities", {
            {"alwaysMatch", {
                {"browserName", "chrome"},
                {"goog:chromeOptions", {
                    {"args", headless ? json::array({"--headless=new"}) : json::array()}
                }}
            }}
        }}
    };

    auto response = json::parse(httpRequest("POST", chromeDriverUrl + "/session", &caps));
    if (response.contains("value") && response["value"].contains("sessionId") && !response["value"]["sessionId"].is_null()) {
        return response["value"]["sessionId"].get<std::string>();
    }
    if (response.contains("sessionId")) {
        return response["sessionId"].get<std::string>();
    }
    throw std::runtime_error("Failed to parse session id from ChromeDriver response");
}

void navigateTo(const std::string& base, const std::string& sessionId, const std::string& url) {
    json payload = {{"url", url}};
    httpRequest("POST", base + "/session/" + sessionId + "/url", &payload);
}

std::string getTitle(const std::string& base, const std::string& sessionId) {
    auto response = json::parse(httpRequest("GET", base + "/session/" + sessionId + "/title"));
    return response["value"].get<std::string>();
}

std::string findElement(const std::string& base, const std::string& sessionId, const std::string& selector) {
    json payload = {{"using", "css selector"}, {"value", selector}};
    auto response = json::parse(httpRequest("POST", base + "/session/" + sessionId + "/element", &payload));
    return response["value"][kElementKey].get<std::string>();
}

void clickElement(const std::string& base, const std::string& sessionId, const std::string& elementId) {
    json payload = json::object();
    httpRequest("POST", base + "/session/" + sessionId + "/element/" + elementId + "/click", &payload);
}

void typeText(const std::string& base, const std::string& sessionId, const std::string& elementId, const std::string& text) {
    json payload = {{"text", text}, {"value", json::array()}};
    for (char c : text) {
        payload["value"].push_back(std::string(1, c));
    }
    httpRequest("POST", base + "/session/" + sessionId + "/element/" + elementId + "/value", &payload);
}

void deleteSession(const std::string& base, const std::string& sessionId) {
    httpRequest("DELETE", base + "/session/" + sessionId);
}

}  // namespace

int main(int argc, char** argv) {
    std::string chromeDriverUrl;
    std::string sessionId;

    try {
        if (argc < 2) {
            std::cerr << "Usage: cpp_automation <config.json>\n";
            return 1;
        }

        std::ifstream configFile(argv[1]);
        if (!configFile) throw std::runtime_error("Cannot open config file");
        json config = json::parse(configFile);

        chromeDriverUrl = config.value("chromeDriverUrl", "http://127.0.0.1:9515");
        const bool headless = config.value("headless", false);
        const auto urls = readNonEmptyLines(config.at("urlsFile").get<std::string>());
        const auto data = loadData(config.at("dataFile").get<std::string>());

        sessionId = createSession(chromeDriverUrl, headless);

        for (const auto& url : urls) {
            navigateTo(chromeDriverUrl, sessionId, url);
            std::cout << "Opened: " << url << "\n";
            std::cout << "Title: " << getTitle(chromeDriverUrl, sessionId) << "\n";

            for (const auto& action : config["actions"]) {
                const std::string type = action.at("type").get<std::string>();

                if (type == "wait") {
                    const int millis = action.value("waitMillis", 1000);
                    std::this_thread::sleep_for(std::chrono::milliseconds(millis));
                } else if (type == "click") {
                    const std::string selector = action.at("selector").get<std::string>();
                    const std::string elementId = findElement(chromeDriverUrl, sessionId, selector);
                    clickElement(chromeDriverUrl, sessionId, elementId);
                } else if (type == "type") {
                    const std::string selector = action.at("selector").get<std::string>();
                    const std::string key = action.value("valueFrom", "");
                    const std::string directValue = action.value("value", "");
                    const std::string value = key.empty() ? directValue : (data.count(key) ? data.at(key) : "");
                    const std::string elementId = findElement(chromeDriverUrl, sessionId, selector);
                    typeText(chromeDriverUrl, sessionId, elementId, value);
                } else if (type == "analyze") {
                    std::cout << "Analyze (title): " << getTitle(chromeDriverUrl, sessionId) << "\n";
                }
            }
        }

        if (!sessionId.empty()) {
            deleteSession(chromeDriverUrl, sessionId);
        }
        return 0;
    } catch (const std::exception& ex) {
        if (!chromeDriverUrl.empty() && !sessionId.empty()) {
            try {
                deleteSession(chromeDriverUrl, sessionId);
            } catch (...) {
            }
        }
        std::cerr << "Error: " << ex.what() << "\n";
        return 1;
    }
}
