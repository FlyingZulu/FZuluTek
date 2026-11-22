#!/usr/bin/env bash
set -euo pipefail
tests="$(cd "$(dirname "$0")" && pwd)"
source_dir="$tests/../../app-zulutek/src/main/cpp"
build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT
cd "$build_dir"
flags=(-O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer -DFX_HOST -DZTEK_LUXE_BUF)
"${CC:-cc}" -std=c99 "${flags[@]}" -c "$source_dir"/dsp/{atek_voice,bass_engines,fx,acid_pattern,midi_looper,drum_engine}.c
"${CXX:-c++}" -std=c++17 "${flags[@]}" -I"$tests" -I"$source_dir" "$tests/audio.cpp" "$source_dir/ztek_engine.cpp" ./*.o -o audio
UBSAN_OPTIONS=halt_on_error=1 ./audio
