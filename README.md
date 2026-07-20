# LINE Feed Blocker

個人端末向けに、Android版LINEのホーム画面で上部固定ヘッダーと下部タブを除く中央コンテンツ領域を、アクセシビリティオーバーレイで隠すアプリです。

実機で確認したLINE 26.10.1の安定View ID、ホームタブ選択状態、スクロール領域、下部タブ境界を使って判定します。判定が曖昧な場合は安全側で非表示にします。詳細な要件は [SPEC.md](SPEC.md) を参照してください。

ホーム中央領域に加え、Agent iの入口、ショッピングタブ、ミニアプリタブはタッチ遮断対象です。ショッピングまたはミニアプリが既に開かれている場合は、その画面本体も覆います。

制限対象のアイコンやAgent i行には見えるオーバーレイを表示します。トーク画面では検索バーとトーク一覧の間に広告・ニュース領域がある場合、その領域を自動的に覆います。

## 制約

- Root化、LINE APK改変、プロセスフックを行いません。
- ネットワーク権限を要求しません。
- UI情報は、アプリ画面で明示的に調査ログを有効化し、出力を要求した場合だけ端末内のlogcatへ書きます。
- アクセシビリティサービスは端末設定から手動で有効化します。

## ビルド

Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:ANDROID_HOME = 'C:\Android\Sdk'
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
.\gradlew.bat assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## インストールと調査

```powershell
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb logcat -s LineFeedInspector
```

1. 端末でアプリを開き、「ユーザー補助設定を開く」からサービスを手動で有効にします。
2. 「UIツリー調査ログを有効にする」をオンにします。
3. 「次のLINEイベントでUIツリーを出力」を押します。
4. LINEで対象画面を開くか、少しスクロールします。
5. 調査後はログ設定をオフにします。

画面ごとの保存手順は、端末接続後に実行します。投稿やメッセージ本文がログやスクリーンショットへ含まれる可能性があるため、`artifacts/` はGit管理対象外です。
