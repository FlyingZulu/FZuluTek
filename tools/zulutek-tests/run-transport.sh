#!/usr/bin/env bash
set -euo pipefail
tests="$(cd "$(dirname "$0")" && pwd)"
source_dir="$tests/../../app-zulutek/src/main/java/com/zulutek/preview"
build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT
"${KOTLINC:-kotlinc}" "$source_dir"/{ZtfCodec,ZtfTransport,ZuluLink}.kt "$tests/Transport.kt" "$tests/Link.kt" -include-runtime -d "$build_dir/ztf.jar"
java -cp "$build_dir/ztf.jar" TransportKt
java -cp "$build_dir/ztf.jar" LinkKt
