# 芽帖（めちょう）

「小さな習慣」を実践するためのAndroidアプリ（APK）。失敗しようがないほど小さな目標を毎日つづけ、余力があれば「おまけ」を記録する。

- 画面本体：`app/src/main/assets/index.html`（単一HTML・素のJavaScript、端末内は localStorage）
- ネイティブ側：`app/src/main/java/jp/marionette1994/mecho/MainActivity.java`（ファイル入出力）
- `main` に push すると GitHub Actions が APK をビルドし、Releases に `mecho.apk` を公開する

## Googleドライブ自動同期

「保存」→「同期先を選ぶ」で保存先にドライブを選ぶと、記録のたびにバックアップ（`mecho-backup.json`）と記録（`mecho-log.md`、任意）を自動で上書きする。
入れ直し時は「ドライブ等から読み込んで置き換える」で `mecho-backup.json` を選ぶ。
