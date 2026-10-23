#!/usr/bin/env sh
# Packages the BeamNG mod as simdeck.zip (mod files at the zip root, as BeamNG expects).
# Usage: sh build.sh [output-dir]   (default: ./dist)
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
out="${1:-$here/dist}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"
rm -f "$out/simdeck.zip"
cd "$here/simdeck"
zip -r -X -q "$out/simdeck.zip" mod_info scripts lua
echo "Wrote $out/simdeck.zip"
