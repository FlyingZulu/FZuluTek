cmake_minimum_required(VERSION 3.18)
project(zulutek C CXX)
set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 17)

find_package(oboe REQUIRED CONFIG)

# the zuluTek DSP, vendored from the ATEK303 app (animatek/FM1-Animatek, GPL-3.0-or-later,
# Open303 parts MIT) with the zuluTek patches applied (MOJO drive, BBD delay, Black Hole
# reverb, bass engines, punch-in ring) — see dsp/README.md. FX_HOST keeps fx.c's big
# buffers out of the FM-1's ".pool" section (plain heap here).
add_library(zulutek SHARED
    jni_bridge.cpp
    ztek_engine.cpp
    dsp/atek_voice.c
    dsp/bass_engines.c
    dsp/fx.c
    dsp/acid_pattern.c
    dsp/midi_looper.c
    dsp/drum_engine.c)
target_include_directories(zulutek PRIVATE dsp)
target_compile_definitions(zulutek PRIVATE FX_HOST)
target_link_libraries(zulutek oboe::oboe log m)
target_compile_options(zulutek PRIVATE -O2)
