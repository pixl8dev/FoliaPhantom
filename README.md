# FoliaPhantom
![ロゴ](logo.png)
**日本語 (Japanese)** | [English](#english)

[![GitHub release](https://img.shields.io/github/v/release/MARVserver/FoliaPhantom.svg)](https://github.com/MARVserver/FoliaPhantom/releases)
[![License](https://img.shields.io/github/license/MARVserver/FoliaPhantom)](LICENSE)

**FoliaPhantom** は、旧来の Bukkit / Spigot / Paper プラグインを、Folia サーバー（PaperMC のマルチスレッド対応版）で動作させるための画期的な互換性レイヤーです。

プラグインの JAR ファイルをサーバー起動時に動的に解析・修正し、Folia のスレッドモデルに適合しないスケジューラAPI呼び出しを、FoliaネイティブのAPI呼び出しに置き換えます。これにより、開発者が Folia 対応を施していないプラグインでも、多くの場合はそのまま動作させることが可能になります。

---

## 🚀 主な特徴

- **バイトコード変換技術**: サーバー起動時にプラグインのクラスファイルを直接解析し、`BukkitScheduler` の呼び出しを Folia の `RegionScheduler` や `AsyncScheduler` を使うコードに書き換えます。これにより、リフレクションやプロキシ方式よりもクリーンで高速な動作を実現します。
- **`plugin.yml` の自動パッチ**: 対象プラグインの `plugin.yml` に `folia-supported: true` フラグを自動的に追加・修正し、Folia サーバーに正式対応プラグインとして認識させます。
- **幅広いスケジューラ対応**: `runTask`, `runTaskTimer` といった主要なメソッドに加え、`scheduleSyncDelayedTask` などの古いメソッドにも対応。同期・非同期タスクの両方を適切に変換します。
- **設定ベースの簡単な導入**: `config.yml` に対象プラグインの情報を記述するだけで、複雑な設定なしに利用できます。
- **複数プラグインの同時対応**: 複数のプラグインを同時に、かつ安全にラップします。

---

## ⚙️ 導入手順

1.  **FoliaPhantom のダウンロード**: [Releasesページ](https://github.com/MARVserver/FoliaPhantom/releases)から最新版の `FoliaPhantom.jar` をダウンロードし、サーバーの `plugins` フォルダに配置します。
2.  **初回起動**: 一度サーバーを起動すると、`plugins/FoliaPhantom/` フォルダと `config.yml` が自動生成されます。
3.  **設定ファイルの編集**: `config.yml` を開き、ラップしたいプラグインの情報を `wrapped-plugins` リストに追加します。

    ```yaml
    # FoliaPhantom - Configuration
    # ここでラップしたいプラグインのリストを定義します。
    wrapped-plugins:
      # 例：MyPlugin という名前のプラグインをラップする場合
      - name: "MyPlugin"
        # 元となるプラグインのJARファイルへのパス
        original-jar-path: "original_jars/MyPlugin.jar"
    ```

    -   **`name`**: ログなどで使用される識別名です。分かりやすい名前を付けてください。
    -   **`original-jar-path`**: ラップ対象のプラグインJARファイルへのパスを `plugins/FoliaPhantom/` からの相対パスで指定します。安全のため、元のJARは `plugins` フォルダの外（例えば、`plugins/FoliaPhantom/original_jars/` など）に配置することを強く推奨します。

4.  **対象プラグインの配置**: 上記で指定したパスに、ラップしたいプラグインのJARファイルを配置します。
5.  **サーバーの再起動**: サーバーを再起動すると、FoliaPhantom が対象プラグインを解析し、パッチを適用した一時JARを生成してサーバーにロードします。これにより、対象プラグインが Folia 上で有効になります。

---

## ⚠️ 制限事項と注意点

-   **NMS/CB依存コード**: このプラグインはスケジューラの互換性を解決しますが、`net.minecraft.server` (NMS) や `org.bukkit.craftbukkit` (CB) のコードに直接依存するプラグインの互換性までは保証しません。特に、独自にエンティティやワールドの処理を行うプラグインは正常に動作しない可能性があります。
-   **高度なクラスローダー操作**: 一部のセキュリティ系プラグインや、特殊なクラスローダー処理を行うプラグインとは競合する可能性があります。
-   **100%の互換性保証ではない**: このプラグインは多くのケースで有効ですが、全てのプラグインの動作を保証するものではありません。問題が発生した場合は、[GitHub Issues](https://github.com/MARVserver/FoliaPhantom/issues) での報告をお願いします。

---

## 🛠️ 技術的な仕組み

FoliaPhantom は、単なるプロキシやリフレクションとは一線を画す、高度なバイトコードエンジニアリング技術を採用しています。

1.  **JARの解析**: `onLoad` フェーズで `config.yml` に記載されたプラグインのJARを読み込みます。
2.  **一時JARの生成**: パッチを適用するための一時的なJARファイルを `plugins/FoliaPhantom/temp-jars/` 内に作成します。
3.  **バイトコード変換 (ASM)**: 各クラスファイル（`.class`）をバイトコードレベルで解析します。`org.bukkit.scheduler.BukkitScheduler` のメソッド呼び出しを発見すると、それを `FoliaPatcher` クラスの静的メソッド呼び出しに置き換えます。
4.  **`FoliaPatcher` の役割**: この内部クラスは、元の呼び出しに対応する Folia のスケジューラ（`RegionScheduler`, `AsyncScheduler` など）を呼び出すロジックを持っています。タスクの実行場所（リージョン）は、可能であればメインワールドのスポーン地点が選択されます。
5.  **`plugin.yml` のパッチ**: `folia-supported: true` をYAMLに追加・上書きします。
6.  **パッチ済みJARのロード**: 全ての変換とパッチが完了した一時JARを、Bukkit の `PluginManager` を通じてサーバーにロードさせます。

このアプローチにより、ラップ対象のプラグインは自身のコードが一切変更されていないかのように動作しつつ、その実態は Folia に最適化されたスケジューリング処理が実行されることになります。

---
---

# English

[![GitHub release](https://img.shields.io/github/v/release/MARVserver/FoliaPhantom.svg)](https://github.com/MARVserver/FoliaPhantom/releases)
[![License](https://img.shields.io/github/license/MARVserver/FoliaPhantom)](LICENSE)

**FoliaPhantom** is a groundbreaking compatibility layer designed to run legacy Bukkit, Spigot, and Paper plugins on a Folia server (the multi-threaded version of PaperMC).

It works by dynamically analyzing and patching plugin JAR files at server startup, replacing scheduler API calls incompatible with Folia's threading model with their native Folia equivalents. This allows many plugins, even those not updated by their developers for Folia, to run seamlessly.

---

## 🚀 Key Features

-   **Bytecode Transformation Technology**: Directly analyzes plugin class files at startup and rewrites `BukkitScheduler` calls to use Folia's `RegionScheduler` and `AsyncScheduler`. This provides a cleaner, faster solution than reflection or proxy-based methods.
-   **Automatic `plugin.yml` Patching**: Automatically adds or corrects the `folia-supported: true` flag in the target plugin's `plugin.yml`, ensuring it is recognized as a compliant plugin by the Folia server.
-   **Broad Scheduler Compatibility**: Supports major methods like `runTask` and `runTaskTimer`, as well as legacy methods like `scheduleSyncDelayedTask`, properly converting both synchronous and asynchronous tasks.
-   **Simple Configuration-Based Setup**: Just add the target plugin's information to the `config.yml` list to get started, no complex setup required.
-   **Multi-Plugin Support**: Safely wraps multiple plugins at the same time.

---

## ⚙️ Installation Guide

1.  **Download FoliaPhantom**: Download the latest `FoliaPhantom.jar` from the [Releases page](https://github.com/MARVserver/FoliaPhantom/releases) and place it in your server's `plugins` folder.
2.  **First Run**: Start the server once to automatically generate the `plugins/FoliaPhantom/` folder and `config.yml`.
3.  **Edit the Configuration**: Open `config.yml` and add information about the plugin(s) you want to wrap to the `wrapped-plugins` list.

    ```yaml
    # FoliaPhantom - Configuration
    # Define the list of plugins you want to wrap here.
    wrapped-plugins:
      # Example: Wrapping a plugin named MyPlugin
      - name: "MyPlugin"
        # Path to the original plugin JAR file
        original-jar-path: "original_jars/MyPlugin.jar"
    ```

    -   **`name`**: An identifier used in logs. Choose a descriptive name.
    -   **`original-jar-path`**: Specify the path to the target plugin JAR, relative to the `plugins/FoliaPhantom/` directory. For safety, it is **strongly recommended** to place original JARs outside the main `plugins` folder (e.g., in `plugins/FoliaPhantom/original_jars/`).

4.  **Place the Target Plugin**: Place the JAR file of the plugin you want to wrap at the path specified above.
5.  **Restart the Server**: When you restart the server, FoliaPhantom will analyze the target plugin, create a patched temporary JAR, and load it. The target plugin will then be enabled on your Folia server.

---

## ⚠️ Limitations & Disclaimers

-   **NMS/CB Dependencies**: While this plugin resolves scheduler issues, it does not guarantee compatibility for plugins that depend directly on `net.minecraft.server` (NMS) or `org.bukkit.craftbukkit` (CB) code. Plugins with custom entity or world handling may not function correctly.
-   **Advanced Class-loading**: May conflict with certain security plugins or other plugins that perform complex class-loader manipulations.
-   **Not a 100% Guarantee**: Although effective in many scenarios, this plugin does not guarantee that every plugin will work. If you encounter issues, please report them on our [GitHub Issues](https://github.com/MARVserver/FoliaPhantom/issues).

---

## 🛠️ How It Works (Technical Deep Dive)

FoliaPhantom employs sophisticated bytecode engineering, setting it apart from simple proxies or reflection.

1.  **JAR Analysis**: During the `onLoad` phase, it reads the plugin JARs specified in `config.yml`.
2.  **Temporary JAR Creation**: It creates a temporary JAR file for patching inside `plugins/FoliaPhantom/temp-jars/`.
3.  **Bytecode Transformation (ASM)**: It parses each class file (`.class`) at the bytecode level. When it finds a method call to `org.bukkit.scheduler.BukkitScheduler`, it replaces it with a static method call to the `FoliaPatcher` class.
4.  **The `FoliaPatcher`'s Role**: This internal class contains the logic to invoke the appropriate Folia scheduler (`RegionScheduler`, `AsyncScheduler`, etc.) corresponding to the original call. The execution location (region) for tasks defaults to the main world's spawn point if available.
5.  **`plugin.yml` Patching**: It adds or overwrites the YAML file to include `folia-supported: true`.
6.  **Loading the Patched JAR**: The fully transformed and patched temporary JAR is then loaded into the server via Bukkit's `PluginManager`.

This approach allows the wrapped plugin to operate as if its code were unchanged, while its scheduling is actually being handled by Folia-optimized processes.
