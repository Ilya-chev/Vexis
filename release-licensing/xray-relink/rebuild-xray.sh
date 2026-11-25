#!/usr/bin/env bash
# Candidate source rebuild; requires a network connection for Go module downloads.
set -euo pipefail
if [ "$#" -ne 2 ]; then
  echo 'Usage: bash rebuild-xray.sh <unpacked-wrapper-source-directory> <new-output.aar>' >&2
  exit 2
fi
source_dir=$(cd "$1" && pwd)
output_dir=$(cd "$(dirname "$2")" && pwd)
output="$output_dir/$(basename "$2")"
test ! -e "$output" || { echo 'Output already exists.' >&2; exit 1; }
test -f "$source_dir/go.mod" || { echo 'go.mod missing.' >&2; exit 1; }
test "$(go env GOVERSION)" = go1.27.3 || { echo 'Go 1.27.3 is required.' >&2; exit 1; }
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
ndk="$ANDROID_HOME/ndk/29.0.14206865"
test -f "$ndk/source.properties" || { echo 'NDK 29.0.14206865 is missing.' >&2; exit 1; }
grep -q 'Pkg.Revision *= *29\.0\.14206865' "$ndk/source.properties" || { echo 'Unexpected NDK revision.' >&2; exit 1; }
for file in geoip.dat geosite.dat geoip-only-cn-private.dat; do
  test ! -e "$source_dir/assets/$file" || { echo "Remove or separately review assets/$file before building." >&2; exit 1; }
done
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
export GOTOOLCHAIN=local GOBIN="$tmp/bin" ANDROID_NDK_HOME="$ndk"
export PATH="$GOBIN:$PATH"
mobile_version=v0.0.0-20261011034528-197770444867
go install "golang.org/x/mobile/cmd/gomobile@$mobile_version"
go install "golang.org/x/mobile/cmd/gobind@$mobile_version"
cd "$source_dir"
go mod download
go mod verify
gomobile init
gomobile bind -v -androidapi 24 -trimpath -ldflags='-s -w -buildid= -checklinkname=0' -o "$output" ./
echo "Built $output; test the AAR and its behavior before redistribution."
