cmake_minimum_required(VERSION 3.18)
project(zuludrums C CXX)
set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 17)

find_package(oboe REQUIRED CONFIG)

# drum machine hybride zuluTek (inspirée Erica Synths PĒRKONS HD-01 : voix hybrides) :
# - drum_engine.c : moteurs custom (kick/snare style Mutable Braids/Elements, hat ZuluFx
#   6-carrés, clap, tom, perc modal, cowbell, FM drum) + hybridation avec les moteurs
#   basse/lead existants (bass_engines.c : PLUK/FM/SUB/8BIT) via le mix HYB par pad
# - séquenceur façon midi_looper.c (clk 1/64 mesure, COND façon Elektron ODDS, FILL,
#   modes NORM/REV/PING-PONG, swing, REC live)
# bass_engines.c + atek_voice.c sont vendored depuis app-zulutek (mêmes crédits GPL-3.0 :
# Animatek/ATEK303 + ZuluFx) — on réutilise, on ne réécrit pas (économie DSP).
set(ZTEK_DSP ${CMAKE_CURRENT_SOURCE_DIR}/../../../../app-zulutek/src/main/cpp/dsp)

add_library(zuludrums SHARED
    jni_bridge.cpp
    drum_engine.c
    ${ZTEK_DSP}/bass_engines.c
    ${ZTEK_DSP}/atek_voice.c)
target_include_directories(zuludrums PRIVATE . ${ZTEK_DSP})
target_compile_definitions(zuludrums PRIVATE FX_HOST)
target_link_libraries(zuludrums oboe::oboe log)
target_compile_options(zuludrums PRIVATE -O2)
