#!/usr/bin/env bash
# CD（.github/workflows/release-app.yml）が使う GitHub Secrets を、手元の鍵から登録する（docs/release.md）。
# アプリごとに、そのフォルダの keystore.properties・署名鍵（とログインの設定）を tar.gz にまとめ、
# base64 にして SIGNING_<アプリ名を大文字に、- を _ に> に入れる。値はコマンドの引数に出さず、標準入力から gh に渡す。
#
# 使い方（リポジトリの直下で）:
#   bash tools/set-release-secrets.sh                 # 全部（store-app と native-apps に include したモジュール）
#   bash tools/set-release-secrets.sh weight-log navi # 指定したものだけ
set -euo pipefail

repo="${REPO:-kawamonn91/ttech-store}"
cd "$(dirname "$0")/.."

prop() { grep "^$2=" "$1" | head -n 1 | cut -d= -f2- | tr -d '\r\n'; }

if [ "$#" -gt 0 ]; then
  apps=("$@")
else
  apps=(store-app)
  while read -r m; do apps+=("$m"); done < <(sed -nE 's/^include\(":([^"]+)"\)$/\1/p' native-apps/settings.gradle.kts)
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

for app in "${apps[@]}"; do
  if [ "$app" = store-app ]; then dir=store-app; else dir="native-apps/$app"; fi
  # ライブラリのモジュール（common など）は鍵を持たない
  if [ ! -f "$dir/keystore.properties" ]; then echo "- $app: keystore.properties が無いので飛ばす"; continue; fi
  store_file="$(prop "$dir/keystore.properties" storeFile)"
  [ -f "$dir/$store_file" ] || { echo "$dir/$store_file がありません" >&2; exit 1; }

  files=(keystore.properties "$store_file")
  # Drive のログインの設定（pocket-dev）
  [ -f "$dir/google-oauth.properties" ] && files+=(google-oauth.properties)

  stage="$work/$app"
  mkdir -p "$stage"
  for f in "${files[@]}"; do mkdir -p "$stage/$(dirname "$f")"; cp "$dir/$f" "$stage/$f"; done
  # ストアアプリは Google ログインのウェブ用クライアント ID だけを local.properties に入れる（sdk.dir は入れない）
  if [ "$app" = store-app ]; then
    printf 'googleWebClientId=%s\n' "$(prop store-app/local.properties googleWebClientId)" > "$stage/local.properties"
    files+=(local.properties)
  fi

  secret="SIGNING_$(echo "$app" | tr 'a-z-' 'A-Z_')"
  tar -czf - -C "$stage" "${files[@]}" | base64 -w0 | gh secret set "$secret" -R "$repo"
  echo "- $app: $secret（${files[*]}）"
done

gh secret list -R "$repo"
