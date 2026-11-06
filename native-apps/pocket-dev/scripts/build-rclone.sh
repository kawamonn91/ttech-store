#!/usr/bin/env bash
# Google ドライブ直結の接続先で使う rclone を、必要な機能だけ入れてビルドし、src/main/jniLibs/ に置く。
#
# 入れる機能は scripts/rclone/main.go（Drive・ログイン（authorize）・遠隔操作の API（rcd））。
# Android 上では Linux 版（静的リンク）をそのまま実行する。名前解決と証明書は
# アプリが環境変数で渡す（drive/Rclone.kt を参照）。
# exec できるよう lib*.so という名前で APK に入れる（Android 10 以降はアプリのデータ領域から exec できない）。
# 作り方は Pocket Linux（kawamonn91/pocket-linux の scripts/build-rclone.sh）と同じ。
#
# 使い方（Linux か WSL で実行。Go 1.27.3 と git が必要。CI と同じ版の Go でないと同じ実行ファイルにならない）:
#   ./scripts/build-rclone.sh
#   RCLONE_SRC=~/src/rclone-v1.75.2 ./scripts/build-rclone.sh   # 取得済みのソース（同じ版）を使う
#
# ソース: https://github.com/rclone/rclone （MIT。ライセンスは src/main/assets/licenses/rclone.txt）
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
VERSION=v1.75.2
OUT="$ROOT/src/main/jniLibs"
GO="${GO:-go}"
SRC="${RCLONE_SRC:-$ROOT/third_party/rclone-build/rclone-$VERSION}"

if [ ! -d "$SRC" ]; then
  git clone -q --depth 1 --branch "$VERSION" https://github.com/rclone/rclone "$SRC"
fi
actual=$(git -C "$SRC" describe --tags --exact-match 2>/dev/null || echo "?")
if [ "$actual" != "$VERSION" ]; then
  echo "rclone のソースが $VERSION ではありません（$actual）: $SRC" >&2
  exit 1
fi

mkdir -p "$SRC/pocketdev"
cp "$ROOT/scripts/rclone/main.go" "$SRC/pocketdev/main.go"

cd "$SRC"
for pair in "arm64 arm64-v8a" "amd64 x86_64"; do
  read -r arch abi <<<"$pair"
  mkdir -p "$OUT/$abi"
  # 同じ版の Go なら誰が作っても同じ実行ファイルになるよう、パスと Git の情報は埋め込まない
  # （CI の .github/workflows/pocket-dev-rclone.yml が作り直して比べる）
  CGO_ENABLED=0 GOOS=linux GOARCH="$arch" GOTOOLCHAIN=local "$GO" build -trimpath -buildvcs=false \
    -ldflags "-s -w -X github.com/rclone/rclone/fs.Version=$VERSION-pocketdev" \
    -o "$OUT/$abi/librclone.so" ./pocketdev
  echo "$abi: $(du -h "$OUT/$abi/librclone.so" | cut -f1)"
done
