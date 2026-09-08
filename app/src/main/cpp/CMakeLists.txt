cmake_minimum_required(VERSION 3.18.1)
project("omnex_core")

add_library(omnex_core SHARED
    omnex_core.cpp
)

target_compile_options(omnex_core PRIVATE
    -Wall
    -Wextra
    -O3
    -fvisibility=hidden
    -fno-rtti
    -fno-exceptions
)

find_library(log-lib log)
find_library(android-lib android)
find_library(dl-lib dl)

target_link_libraries(omnex_core
    ${log-lib}
    ${android-lib}
    ${dl-lib}
)