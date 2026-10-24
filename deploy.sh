#!/usr/bin/env bash
# USB 接続した端末へ lockrec をビルド・インストールし、権限・モデル・トークンまで設定する
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
prop() { sed -n "s/^$1=//p" "$ROOT/lockrec.properties"; }
PACKAGE=$(prop package)
MODEL_NAME=$(prop model_file)
REPO="$(prop github_owner)/$(prop github_repo)"
MINUTE_DIR=$(prop minute_dir)
MODEL_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$MODEL_NAME"
MODEL_PATH="$ROOT/models/$MODEL_NAME"
TOKEN_FILE="${LOCKREC_TOKEN_FILE:-$HOME/.config/lockrec/github_token}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"

die() { echo "error: $*" >&2; exit 1; }
step() { echo "==> $*"; }

[ -s "$TOKEN_FILE" ] || die "GitHub トークンがありません: ${TOKEN_FILE}（README の手順で作成）"
[ "$(adb get-state 2>/dev/null)" = device ] || die "端末が見つかりません（USB デバッグを ON にして接続、許可ダイアログを承認）"

step "トークンで $REPO を読めるか確認"
status=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $(tr -d '[:space:]' < "$TOKEN_FILE")" \
  "https://api.github.com/repos/$REPO/contents/$MINUTE_DIR")
[ "$status" = 200 ] || die "トークンで $REPO を読めません (HTTP $status)"

step "ビルド"
"$ROOT/gradlew" -p "$ROOT" --quiet assembleDebug

step "インストール"
adb install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" > /dev/null

step "権限付与・電池最適化の除外"
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS
adb shell dumpsys deviceidle whitelist "+$PACKAGE" > /dev/null

if [ ! -f "$MODEL_PATH" ]; then
  step "モデルをダウンロード（約 550MB）"
  mkdir -p "$ROOT/models"
  curl -fL -o "$MODEL_PATH.part" "$MODEL_URL"
  mv "$MODEL_PATH.part" "$MODEL_PATH"
fi

local_size=$(stat -f%z "$MODEL_PATH")
remote_size=$(adb shell "run-as $PACKAGE stat -c %s files/models/$MODEL_NAME 2>/dev/null" | tr -d '\r' || true)
if [ "$local_size" != "$remote_size" ]; then
  step "モデルを端末へ転送"
  adb shell "run-as $PACKAGE mkdir -p files/models"
  # exec-in は入力終了でコマンドごと止まるため、転送と rename を分ける
  adb exec-in "run-as $PACKAGE sh -c 'cat > files/models/$MODEL_NAME.tmp'" < "$MODEL_PATH"
  remote_size=$(adb shell "run-as $PACKAGE stat -c %s files/models/$MODEL_NAME.tmp" | tr -d '\r')
  [ "$local_size" = "$remote_size" ] || die "モデル転送が途中で切れました ($remote_size / $local_size bytes)"
  adb shell "run-as $PACKAGE mv files/models/$MODEL_NAME.tmp files/models/$MODEL_NAME"
fi

step "トークンを端末へ書き込み"
tr -d '[:space:]' < "$TOKEN_FILE" | adb exec-in "run-as $PACKAGE sh -c 'cat > files/github_token'"
adb shell "run-as $PACKAGE chmod 600 files/github_token"

cat <<'EOF'
==> 完了
初回だけ端末で:
  1. 設定 → ロック画面と AOD → ショートカット → 左 or 右 に「録音」を割り当てる
  2. クイック設定パネルの編集（鉛筆）から「録音」タイルを追加する
  3. 設定 → バッテリー → バックグラウンドでの使用制限 → 「スリープしないアプリ」に「録音」を追加する
EOF
