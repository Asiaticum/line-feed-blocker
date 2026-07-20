# LINE Feed Blocker 仕様書

## 1. 目的

Android版LINE（対象パッケージ `jp.naver.line.android`）のホーム画面に追加された、無限スクロール型の投稿・おすすめコンテンツ領域だけを、個人端末上で見えないようにする。

LINE本体のAPKは改変せず、Root化やプロセスへのフックも行わない。自作アプリの `AccessibilityService` でLINEの表示状態を解析し、ホーム画面かつ投稿フィード領域を高い確度で検出できた場合だけ、`TYPE_ACCESSIBILITY_OVERLAY` による不透明なオーバーレイを対象領域へ重ねる。

## 2. 完成時の動作

> 2026-07-19 実機テスト後の仕様変更: スクロール追従性を優先し、LINEホームでは上部固定ヘッダーと下部タブを除く中央コンテンツ領域を全面的に覆い、標準でタッチも遮断する。この変更は、以下に残る当初の「投稿フィード部分だけを覆う」「友だち・サービス等を操作可能にする」という記述に優先する。

> 2026-07-19 追加制限: LINEのAgent i入口とトーク一覧の公式Agent i行、ショッピングタブ、ミニアプリタブは透明なアクセシビリティオーバーレイでタッチを遮断する。ショッピングまたはミニアプリ画面が既に選択されている場合は、下部タブを残して画面本体を覆う。

> 2026-07-19 表示変更: 制限対象のAgent i、ショッピング、ミニアプリは不透明な「制限中」オーバーレイで対象を視覚的にも隠す。トーク画面では検索バー下端からトーク一覧開始位置までの広告・ニュース枠を動的に覆い、タッチも遮断する。

- LINE以外のアプリでは何も表示しない。
- LINEのホーム画面を開いたときだけ動作する。
- ホームの上部固定ヘッダーと下部タブを除く中央領域を全面的に覆う。
- 上部のプロフィール、各種アイコン、設定、および下部タブは操作できる。
- 覆った中央領域へのタッチは標準で遮断する。
- トーク一覧、トークルーム、友だち関連画面、設定画面では表示しない。
- LINEを閉じるか別アプリへ移動したら即座に消す。
- 端末再起動後も、アクセシビリティサービスが有効なら利用できる。
- 投稿内容や画面情報を外部へ送信しない。
- Google Playには公開せず、個人端末へ直接インストールする。

## 3. 技術構成

### PC側

- Codex CLI
- JDK 17
- Android SDK Command-line Tools
- Android SDK Platform
- Android SDK Build-Tools
- Android SDK Platform-Tools / ADB
- Gradle Wrapper
- Git
- Android Studioは使用しない。

### Androidアプリ側

- Kotlin
- Gradle Kotlin DSL
- `AccessibilityService`
- `TYPE_ACCESSIBILITY_OVERLAY`
- `AccessibilityNodeInfo`
- `DataStore` または `SharedPreferences`
- minSdk 28
- 対象パッケージ: `jp.naver.line.android`

## 4. 採用方式

次の順番で処理する。

1. LINEが前面にあることを検出する。
2. 現在の画面がLINEホームか判定する。
3. UIツリーから投稿フィード候補を探す。
4. 対象領域の画面座標を取得する。
5. その領域だけオーバーレイで覆う。
6. 条件を満たさなくなったらオーバーレイを消す。

UIツリーから投稿領域を正確に取得できない場合は、画面サイズに対する比率とユーザー調整値を併用する。固定座標だけには依存しない。

### 解析の優先順位

1. LINEが公開している安定した `viewIdResourceName`
2. 選択中タブの状態やUI構造
3. `contentDescription`
4. ホーム固有の複数テキスト
5. スクロール領域の位置とサイズ
6. 画面比率による位置推定
7. 最終手段として固定座標

## 5. 作業方針

最初から完成版を推測で作らず、実際の端末からLINEの画面情報を取得し、その結果を根拠に段階的に実装する。

調査対象:

- `text`
- `contentDescription`
- `viewIdResourceName`
- `className`
- `boundsInScreen`
- `scrollable`
- `clickable`
- `selected`
- `visibleToUser`
- `enabled`
- `childCount`
- UIツリー内の親子関係
- ホーム画面と他画面の差分

## 6. 作業フェーズ

### フェーズ1: PC環境の準備

JDK 17、Android SDK Command-line Tools、Platform、Build-Tools、Platform-Tools、Codex CLI、Gitを準備し、次を確認する。

```text
java -version
adb version
sdkmanager --version
codex --version
```

Windowsでは `ANDROID_HOME` とPATHを適切に設定する。

### フェーズ2: Android端末の準備

ユーザーが開発者向けオプションとUSBデバッグを有効にし、USBデバッグ許可を承認する。`adb devices` で端末が `device` 状態になることを確認する。LINEは解析対象となる最新状態にする。

### フェーズ3: プロジェクト作成

Gradle Wrapperを含むAndroidプロジェクトを作成する。初期段階は完成版ではなく、LINEのUI情報を調査するデバッグ版とする。

```text
line-feed-blocker/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/
        └── res/
```

### フェーズ4: LINE画面の初期解析

LINEの対象画面を手動で開き、ADBでUI XMLとスクリーンショットを `artifacts/` に保存する。

```text
01-home-top
02-home-scrolled
03-talk-list
04-friends
05-settings
06-chat-room
07-home-reopened
```

基本コマンド:

```bash
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml artifacts/line-home.xml
adb exec-out screencap -p > artifacts/line-home.png
```

### フェーズ5: アクセシビリティ調査アプリ

`uiautomator dump` で情報が不足する場合に備え、`AccessibilityService` からUIノードを再帰的に調査する機能を実装する。ログタグは `LineFeedInspector` とし、depth、className、text、contentDescription、viewIdResourceName、bounds、visible、selected、scrollable、clickable、enabled、childCountを出力できるようにする。

```bash
adb logcat -c
adb logcat -s LineFeedInspector
adb logcat -d -s LineFeedInspector > artifacts/accessibility-home.log
```

### フェーズ6: 実機インストール

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

アクセシビリティサービスの有効化は、ユーザーが端末設定から手動で行う。セキュリティ上、ADBから完全自動化しない。

### フェーズ7: 画面差分の解析

ホーム上部、ホームをスクロールした状態、トーク一覧、友だち関連画面、設定画面、トークルームのログを比較する。

ホーム画面判定では、選択状態のホームタブ、固有View ID、固有テキストの組み合わせ、親子構造、固有のスクロール領域を調べる。「ホーム」という文字があるだけでは判定しない。

投稿フィード領域判定では、大きなスクロール可能ノード、`RecyclerView`、Composeのスクロールコンテナ、投稿カード群を含む親ノード、一定位置より下にある縦長領域を候補とする。

### フェーズ8: オーバーレイ実装

次の全条件を満たす場合だけ表示する。

```text
前面アプリがLINE
かつ LINEホーム画面と高い確度で判定
かつ 投稿フィード領域を取得できた
```

条件を満たさない場合は表示しない。

仕様:

- 投稿フィード部分だけに表示する。
- 背景色を選択可能にする。
- 完全不透明を標準にする。
- テキスト表示をオン・オフ可能にする。
- タッチ遮断をオン・オフ可能にする。
- フィード位置の上下・左右補正を可能にする。
- デバッグ時だけ境界線を表示可能にする。

### フェーズ9: 調整画面

端末差やLINEアップデートに対応するため、次の設定を端末内だけに保存する。

- 機能の有効／無効
- 上端補正
- 下端補正
- 左右補正
- 不透明度
- 背景色
- タッチ遮断
- デバッグ枠
- 現在のUIツリー保存
- 画面判定結果の表示
- 設定の初期化
- 緊急停止ボタン

### フェーズ10: フェイルセーフ

- LINE以外へ移動したら即時非表示にする。
- ホーム判定が曖昧なら非表示にする。
- UIルートが取得できなければ非表示にする。
- 画面ロック時は非表示にする。
- サービス停止時に必ずオーバーレイを削除する。
- 例外発生時も必ず削除する。
- 一定時間イベントがなければ再判定する。
- 端末回転時と画面サイズ変更時に座標を再計算する。
- アプリ内に緊急停止ボタンを設ける。

### フェーズ11: 実機テスト

| テスト | 期待結果 |
| --- | --- |
| LINEホームを開く | 投稿部分だけ隠れる |
| ホームをスクロール | 隠す領域が維持される |
| トークタブへ移動 | オーバーレイが消える |
| トークルームを開く | 表示されない |
| LINE設定を開く | 表示されない |
| 別アプリへ移動 | 即時消える |
| LINEを再起動 | 正しく再判定される |
| 画面を回転 | 座標が再計算される |
| 文字サイズを変更 | 必要に応じ補正できる |
| 端末を再起動 | サービス状態に応じて動く |
| LINEを更新 | 誤判定時は安全側で非表示になる |

### フェーズ12: 最終ビルド

まずデバッグAPKを生成する。必要に応じて個人用署名鍵によるリリースAPKを生成し、ADBで個人端末へインストールする。

## 7. Codexが担当する範囲

- プロジェクト生成
- Kotlinコード、Manifest、リソース、Gradle設定の作成
- 安全なADBコマンドの実行
- UI XML、スクリーンショット、アクセシビリティログの取得
- ノード比較と判定ロジックの作成
- オーバーレイと設定画面の実装
- ビルド、APKインストール、コンパイルエラー修正
- ログと目視結果に基づく反復修正
- READMEと操作手順の作成

## 8. ユーザーが手動で行う範囲

- USBデバッグの初回許可
- LINEへのログイン
- 解析対象のLINE画面を開く
- アクセシビリティサービスを有効化
- 隠す範囲が正しいか目視確認
- 必要に応じて位置補正
- LINE更新後の再確認

## 9. 実施しないこと

- LINE APKの改変
- Root化
- LSPosedやXposedによるフック
- LINEプロセスのメモリ解析
- 通信内容の取得
- メッセージや投稿の自動送信・自動操作
- アカウント操作の自動化
- LINEのデータ削除
- 端末初期化
- UI情報、ログ、スクリーンショットなど取得情報の外部送信
- Google Playへの一般公開
- 破壊的なADBコマンド

## 10. 完了成果物

- 実機調査結果に基づくAndroidアプリ
- `app/build/outputs/apk/debug/app-debug.apk`
- 必要に応じた個人用リリースAPK
- 端末内だけに保存される設定
- 調査用アーティファクト
- ビルド、インストール、調査、調整、復旧手順を記載したREADME
