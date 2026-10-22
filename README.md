# yuubinnkyoku niconico patches

[ニコニコ動画](https://www.nicovideo.jp/) Android 版のための非公式 Morphe パッチです。

## 対応アプリ

- パッケージ名: `jp.nicovideo.android`
- 対象バージョン: **9.16.3（versionCode 479）**
- 元の配布形式: **分割 APK（APKS）**
- 状態: **試験的**（DEX の静的解析で変更箇所を確認。実機での動作確認は未実施）

## パッチ

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0-dev.7](https://github.com/yuubinnkyoku/morphe-nicovideo-patch/releases/tag/v1.0.0-dev.7)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
<details open>
<summary>📦 ニコニコ動画&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;9.16.3 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Diagnostic: re-sign only (no changes)](#diagnostic-re-sign-only-no-changes) | Diagnostic only: no app behavior changes. Select this alone to isolate APK repacking/re-signing from the playback patches. |  |
| [Enable background playback](#enable-background-playback) | Uses the app's existing background audio service when the background-play setting is enabled. |  |
| [Enable picture-in-picture](#enable-picture-in-picture) | Enables the app's existing PiP playback path while retaining Android's PiP support and permission checks. |  |
| [Login diagnostics: copy error report](#login-diagnostics-copy-error-report) | Copy a safe login diagnostic report with App Check token presence, Account Passport request token presence and HTTP status. Never copies tokens. |  |

</details>

<!-- PATCHES_END -->

2つのパッチは独立して選択できます。アカウント全体のプレミアム会員情報は変更しません。

### 実装上の注意

9.16.3 の `c86.a()Z` は `c86.b` の会員状態を読み取った後、端末の PiP 対応状況と Android AppOps の許可を確認します。PiP パッチは会員状態の読み取り結果だけを変更します。

`VideoPlayerFragment.T()Z` は、画面終了状態、`background_play` 設定、PiP の状態、`z0` の会員状態、再生制限状態を確認します。バックグラウンド再生パッチは `z0` の読み取り結果だけを変更します。通知表示や再生継続にはアプリ既存の `SeamlessPlayerService` を使います。

**実機未検証です。** 端末や動画によっては動作しない可能性があります。サーバー側の判定には対応していません。元のアプリが定める利用条件・法令にも注意してください。

## 開発・検証

```sh
./gradlew buildAndroid
```

完成した `.mpp` は `patches/build/libs/` に出力されます。Morphe のパッチソースとして追加して利用します。

実機テストでは、通常動画再生中のホーム移動（PiP 自動開始）、PiP からの復帰、バックグラウンド再生中の画面消灯・通知操作・アプリ復帰を確認してください。Android の設定でニコニコ動画の PiP が許可されている必要があります。

新しいアプリのバージョンでは難読化されたクラス名やメソッド名が変わるため、再解析してフィンガープリントを更新してください。

## ライセンス

[GPLv3](LICENSE)。本リポジトリはニコニコ動画およびドワンゴとは無関係です。
