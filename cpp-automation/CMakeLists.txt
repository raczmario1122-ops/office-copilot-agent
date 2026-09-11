cmake_minimum_required(VERSION 3.20)
project(cpp_automation LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(
  nlohmann_json
  URL https://github.com/nlohmann/json/releases/download/v3.11.3/json.tar.xz
)
FetchContent_MakeAvailable(nlohmann_json)

find_package(CURL REQUIRED)

add_executable(cpp_automation
  src/main.cpp
)

target_link_libraries(cpp_automation PRIVATE nlohmann_json::nlohmann_json CURL::libcurl)
