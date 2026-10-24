# lockrec

Galaxy のロック画面ショートカットで録音を開始・停止する。充電中に端末上の whisper.cpp で文字起こしし、
`t-hada/vault` の `02_minute/YYYYMMDD_HHMM.md` へ GitHub API で直接コミットする。音声は端末の外に出ない。

## セットアップ

1. GitHub → Settings → Developer settings → Fine-grained tokens で、次の設定のトークンを作る
   - Repository access: Only select repositories → `t-hada/vault`
   - Permissions: Contents → Read and write
2. `~/.config/lockrec/github_token` に保存する（`chmod 600`）
3. 端末で開発者向けオプション → USB デバッグを ON にし、USB で接続する
   （設定 → セキュリティとプライバシー → 自動ブロッカーが ON だと USB からのインストールが止められるので OFF にする）
4. `./deploy.sh`（2回目以降はアプリ更新・トークン差し替えにも使う）

## 動き

- 録音: `files/recordings/<yyyyMMdd_HHmmss>/audio.wav`（16kHz mono）。push 後も残す
- 文字起こし: 充電中に 5 分の窓ごとに処理し、窓単位で保存する。途中で止められても続きから再開する
- push: 文字起こしが終わり、ネットワークに繋がっていれば送る。同じ名前のファイルがあれば `_2` を付ける

## 調べるとき

```bash
adb logcat -s lockrec lockrec-whisper WM-WorkerWrapper
adb shell run-as dev.thada.lockrec ls -R files/recordings
```
