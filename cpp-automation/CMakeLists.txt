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

FetchContent_Declare(
  cpp_httplib
  URL https://github.com/yhirose/cpp-httplib/archive/refs/tags/v0.16.2.tar.gz
)
FetchContent_MakeAvailable(cpp_httplib)

add_executable(cpp_automation
  src/main.cpp
)

target_link_libraries(cpp_automation PRIVATE nlohmann_json::nlohmann_json httplib::httplib)
