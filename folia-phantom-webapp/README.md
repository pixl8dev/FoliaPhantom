# Folia Phantom 3.0 - Web Application

このWebアプリケーションは、既存のBukkit/Spigot/Paperプラグイン(.jar)を、Foliaサーバーで動作するようにパッチを適用するためのWeb UIを提供します。

## 1. ビルド方法

プロジェクトのルートディレクトリで以下のコマンドを実行してください。

```bash
# Windows
./gradlew.bat -p folia-phantom-webapp clean build

# Linux/macOS
./gradlew -p folia-phantom-webapp clean build
```

これにより、`folia-phantom-webapp/build/libs/` ディレクトリに実行可能なJARファイル `folia-phantom-webapp-1.0.0.jar` が生成されます。

## 2. 実行方法

以下のコマンドでWebアプリケーションを起動します。

```bash
# リポジトリのルートディレクトリから実行
java -jar folia-phantom-webapp/build/libs/folia-phantom-webapp-1.0.0.jar
```

サーバーが起動すると、コンソールに `Started FoliaPhantom3Application` というメッセージが表示されます。
デフォルトでは、アプリケーションはポート `8080` で実行されます。

## 3. 使い方

1.  Webブラウザを開き、 `http://localhost:8080` にアクセスします。
2.  表示されたページのアップロードエリアに、パッチを適用したいプラグインのJARファイルをドラッグ＆ドロップするか、「ファイルを選択」ボタンからファイルを選択します。
3.  アップロードとパッチ処理が自動的に開始されます。
4.  処理が成功すると、「ダウンロード」ボタンが表示されます。これをクリックして、パッチ済みのJARファイルをダウンロードしてください。
5.  ダウンロードしたJARファイルを、あなたのFoliaサーバーの `plugins` フォルダに配置して使用してください。
