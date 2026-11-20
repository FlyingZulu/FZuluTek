cmake_minimum_required(VERSION 3.18)
project(zuludrums C CXX)
set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 17)

find_package(oboe REQUIRED CONFIG)

# mini drum machine analogique zuluTek (8 pads + seq 16 steps), autonome.
add_library(zuludrums SHARED
    jni_bridge.cpp
    drum_engine.c)
target_include_directories(zuludrums PRIVATE .)
target_link_libraries(zuludrums oboe::oboe log)
target_compile_options(zuludrums PRIVATE -O2)
