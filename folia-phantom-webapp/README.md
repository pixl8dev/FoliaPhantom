# Folia Phantom 3.0 - Web Application

このWebアプリケーションは、既存のBukkit/Spigot/Paperプラグイン(.jar)を、Foliaサーバーで動作するようにパッチを適用するためのWeb UIを提供します。

## 1. 前提条件

このWebアプリケーションは、内部で元の `FoliaPhantom-extra` プロジェクトが生成したヘルパークラスを使用します。そのため、**Webアプリケーションを起動する前に、必ず元のプロジェクトをビルドしてください。**

```bash
# リポジトリのルートディレクトリで実行
mvn -f FoliaPhantom/pom.xml clean package
```

これにより、`FoliaPhantom/target/FoliaPhantom-extra-2.0-SNAPSHOT.jar` が生成されます。Webアプリケーションは、このJARファイルを起動時に読み込みます。このファイルが存在しない場合、Webアプリケーションは起動に失敗します。

## 2. ビルド方法

次に、このWebアプリケーション自体をビルドします。プロジェクトのルートディレクトリで以下のコマンドを実行してください。

```bash
# Windows
./gradlew.bat -p folia-phantom-webapp build

# Linux/macOS
./gradlew -p folia-phantom-webapp build
```

これにより、`folia-phantom-webapp/build/libs/` ディレクトリに実行可能なJARファイル `folia-phantom-webapp-1.0.0.jar` が生成されます。

## 3. 実行方法

以下のコマンドでWebアプリケーションを起動します。

```bash
# リポジトリのルートディレクトリで実行
java -jar folia-phantom-webapp/build/libs/folia-phantom-webapp-1.0.0.jar
```

サーバーが起動すると、コンソールに `Started FoliaPhantom3Application` というメッセージが表示されます。
デフォルトでは、アプリケーションはポート `8080` で実行されます。

## 4. 使い方

1.  Webブラウザを開き、 `http://localhost:8080` にアクセスします。
2.  表示されたページのアップロードエリアに、パッチを適用したいプラグインのJARファイルをドラッグ＆ドロップするか、「ファイルを選択」ボタンからファイルを選択します。
3.  アップロードとパッチ処理が自動的に開始されます。
4.  処理が成功すると、「ダウンロード」ボタンが表示されます。これをクリックして、パッチ済みのJARファイルをダウンロードしてください。
5.  ダウンロードしたJARファイルを、あなたのFoliaサーバーの `plugins` フォルダに配置して使用してください。
