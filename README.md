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
- **設定不要の自動スキャン**: `plugins`フォルダ内のプラグインを自動的にスキャンし、Folia未対応のものにパッチを適用します。
- **地形・ワールド生成の互換性**: `ChunkGenerator`や同期的な`createWorld`呼び出しをラップし、Foliaの非同期環境で動作するように試みます。

---

## ⚙️ 導入手順

1.  **FoliaPhantom のダウンロード**: [Releasesページ](https://github.com/MARVserver/FoliaPhantom/releases)から最新版の `FoliaPhantom.jar` をダウンロードし、サーバーの `plugins` フォルダに配置します。
2.  **サーバーの起動**: サーバーを起動するだけで、FoliaPhantom は `plugins` フォルダ内のすべてのプラグインを自動的にスキャンし、Foliaに未対応のプラグインに対してパッチを適用します。
3.  **（任意）パッチ対象から除外**: もし、特定のプラグインにパッチを適用したくない場合は、初回起動時に生成される `plugins/FoliaPhantom/config.yml` を編集します。

    ```yaml
    # FoliaPhantom - Configuration
    auto-scan-plugins:
      enabled: true
      # パッチを適用したくないプラグインのリスト
      excluded-plugins:
        - "SomePlugin"
        - "AnotherPlugin"
    ```
    `excluded-plugins` リストにプラグイン名を追加することで、そのプラグインはスキャン対象から除外されます。

---

## ⚠️ 制限事項と注意点

-   **同期的なワールド生成のリスク**: `createWorld`のような、完了までに時間のかかる処理を同期的に呼び出すプラグインをラップした場合、サーバーが一時的にフリーズ（ハングアップ）する可能性があります。Foliaの設計上、これは避けられないリスクであり、Watchdogが警告を出すことがあります。
-   **NMS/CB依存コード**: このプラグインはスケジューラや一部のワールド生成APIの互換性を解決しますが、`net.minecraft.server` (NMS) や `org.bukkit.craftbukkit` (CB) のコードに直接依存するプラグインの互換性までは保証しません。
-   **高度なクラスローダー操作**: 一部のセキュリティ系プラグインや、特殊なクラスローダー処理を行うプラグインとは競合する可能性があります。
-   **100%の互換性保証ではない**: このプラグインは多くのケースで有効ですが、全てのプラグインの動作を保証するものではありません。問題が発生した場合は、[GitHub Issues](https://github.com/MARVserver/FoliaPhantom/issues) での報告をお願いします。

---

## 🛠️ 技術的な仕組み

FoliaPhantom は、単なるプロキシやリフレクションとは一線を画す、高度なバイトコードエンジニアリング技術を採用しています。

1.  **JARの解析**: `onLoad` フェーズで `config.yml` に記載されたプラグインのJARを読み込みます。
2.  **一時JARの生成**: パッチを適用するための一時的なJARファイルを `plugins/FoliaPhantom/temp-jars/` 内に作成します。
3.  **バイトコード変換 (ASM)**: 各クラスファイル（`.class`）をバイトコードレベルで解析し、以下のAPI呼び出しを発見すると、それを `FoliaPatcher` クラスの静的メソッド呼び出しに置き換えます。
    -   `org.bukkit.scheduler.BukkitScheduler` の各種メソッド
    -   `org.bukkit.plugin.Plugin#getDefaultWorldGenerator`
    -   `org.bukkit.Server#createWorld`
4.  **`FoliaPatcher` の役割**: この内部クラスは、元の呼び出しに対応する Folia のAPIを呼び出すロジックを持っています。
    -   **スケジューラ**: `RegionScheduler`や`AsyncScheduler`を適切に使い分けます。
    -   **ワールド生成**: `getDefaultWorldGenerator`が返す`ChunkGenerator`をラッパーで包み、`createWorld`の呼び出しを専用のスレッドで実行してデッドロックを回避します。
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
-   **Zero-Configuration Auto-Scan**: Automatically scans plugins in your `plugins` folder and patches those not yet compatible with Folia.
-   **Terrain & World Gen Compatibility**: Attempts to wrap calls to `ChunkGenerator` and synchronous `createWorld` to work within Folia's asynchronous environment.

---

## ⚙️ Installation Guide

1.  **Download FoliaPhantom**: Download the latest `FoliaPhantom.jar` from the [Releases page](https://github.com/MARVserver/FoliaPhantom/releases) and place it in your server's `plugins` folder.
2.  **Start the Server**: Simply start your server. FoliaPhantom will automatically scan all plugins in the `plugins` folder and apply compatibility patches to any that are not Folia-native.
3.  **Exclude Plugins (Optional)**: If you need to prevent a specific plugin from being patched, edit the `config.yml` file generated in `plugins/FoliaPhantom/` on the first run.

    ```yaml
    # FoliaPhantom - Configuration
    auto-scan-plugins:
      enabled: true
      # List of plugins to exclude from patching.
      excluded-plugins:
        - "SomePlugin"
        - "AnotherPlugin"
    ```
    Add the plugin's name to the `excluded-plugins` list to have it ignored by the scanner.

---

## ⚠️ Limitations & Disclaimers

-   **Risk of Synchronous World Generation**: Wrapping a plugin that calls time-consuming methods like `createWorld` synchronously may cause the server to freeze or hang temporarily. This is an unavoidable risk due to Folia's design, and you may see warnings from the Watchdog.
-   **NMS/CB Dependencies**: While this plugin resolves scheduler and some world-generation API issues, it does not guarantee compatibility for plugins that depend directly on `net.minecraft.server` (NMS) or `org.bukkit.craftbukkit` (CB) code.
-   **Advanced Class-loading**: May conflict with certain security plugins or other plugins that perform complex class-loader manipulations.
-   **Not a 100% Guarantee**: Although effective in many scenarios, this plugin does not guarantee that every plugin will work. If you encounter issues, please report them on our [GitHub Issues](https://github.com/MARVserver/FoliaPhantom/issues).

---

## 🛠️ How It Works (Technical Deep Dive)

FoliaPhantom employs sophisticated bytecode engineering, setting it apart from simple proxies or reflection.

1.  **JAR Analysis**: During the `onLoad` phase, it scans all JARs in the `plugins` directory.
2.  **Temporary JAR Creation**: It creates a temporary JAR file for patching inside `plugins/FoliaPhantom/temp-jars/`.
3.  **Bytecode Transformation (ASM)**: It parses each class file (`.class`) at the bytecode level. When it finds a call to a targeted API, it replaces it with a static method call to the `FoliaPatcher` class. Targeted APIs include:
    -   Methods in `org.bukkit.scheduler.BukkitScheduler`
    -   `org.bukkit.plugin.Plugin#getDefaultWorldGenerator`
    -   `org.bukkit.Server#createWorld`
4.  **The `FoliaPatcher`'s Role**: This internal class contains the logic to invoke the appropriate Folia-native API.
    -   **Schedulers**: It intelligently redirects calls to `RegionScheduler` or `AsyncScheduler`.
    -   **World Generation**: It wraps the `ChunkGenerator` returned by `getDefaultWorldGenerator` and dispatches `createWorld` calls to a dedicated thread to prevent deadlocks.
5.  **`plugin.yml` Patching**: It adds or overwrites the YAML file to include `folia-supported: true`.
6.  **Loading the Patched JAR**: The fully transformed and patched temporary JAR is then loaded into the server via Bukkit's `PluginManager`.

This approach allows the wrapped plugin to operate as if its code were unchanged, while its scheduling is actually being handled by Folia-optimized processes.
