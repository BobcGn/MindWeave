这是一个 Kotlin Multiplatform 项目，目标平台包括 Android、iOS、Web、桌面端（JVM）和服务器端。

[English Version](./README.md)

* [/composeApp](./composeApp/src) 目录包含将在 Compose Multiplatform 应用之间共享的代码。
  它包含多个子文件夹：
    - [commonMain](./composeApp/src/commonMain/kotlin) 目录用于所有目标平台通用的代码。
    - 其他文件夹用于仅针对特定平台编译的 Kotlin 代码。
      例如，如果你想在 Kotlin 应用的 iOS 部分使用 Apple 的 CoreCrypto，
      [iosMain](./composeApp/src/iosMain/kotlin) 文件夹是放置此类调用的合适位置。
      同样，如果你想编辑桌面端（JVM）特定部分，[jvmMain](./composeApp/src/jvmMain/kotlin)
      文件夹是合适的位置。

* [/iosApp](./iosApp/iosApp) 包含 iOS 应用程序。即使你使用 Compose Multiplatform 共享 UI，
  你也需要这个入口点来运行 iOS 应用。这也是你应该为项目添加 SwiftUI 代码的地方。

* [/server](./server/src/main/kotlin) 目录用于 Ktor 服务器应用程序。

* [/shared](./shared/src) 目录用于将在项目所有目标平台之间共享的代码。
  最重要的子文件夹是 [commonMain](./shared/src/commonMain/kotlin)。如果需要，你
  也可以在此处的平台特定文件夹中添加代码。

### 构建和运行 Android 应用

要构建和运行 Android 应用的开发版本，请使用 IDE 工具栏中运行小部件的运行配置，或直接从终端构建：

- 在 macOS/Linux 上
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- 在 Windows 上
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### 构建和运行桌面端（JVM）应用

要构建和运行桌面应用的开发版本，请使用 IDE 工具栏中运行小部件的运行配置，或直接从终端运行：

- 在 macOS/Linux 上
  ```shell
  ./gradlew :composeApp:run
  ```
- 在 Windows 上
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### 构建和运行服务器

要构建和运行服务器的开发版本，请使用 IDE 工具栏中运行小部件的运行配置，或直接从终端运行：

- 在 macOS/Linux 上
  ```shell
  ./gradlew :server:run
  ```
- 在 Windows 上
  ```shell
  .\gradlew.bat :server:run
  ```

### 构建和运行 Web 应用

要构建和运行 Web 应用的开发版本，请使用 IDE 工具栏中运行小部件的运行配置，或直接从终端运行：

- 对于 Wasm 目标（更快，适用于现代浏览器）：
    - 在 macOS/Linux 上
      ```shell
      ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
      ```
    - 在 Windows 上
      ```shell
      .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
      ```
- 对于 JS 目标（较慢，支持旧版浏览器）：
    - 在 macOS/Linux 上
      ```shell
      ./gradlew :composeApp:jsBrowserDevelopmentRun
      ```
    - 在 Windows 上
      ```shell
      .\gradlew.bat :composeApp:jsBrowserDevelopmentRun
      ```

### 构建和运行 iOS 应用

要构建和运行 iOS 应用的开发版本，请使用 IDE 工具栏中运行小部件的运行配置，或在 Xcode 中打开 [/iosApp](./iosApp) 目录并从那里运行。

---

了解更多关于 [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)、
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform)、
[Kotlin/Wasm](https://kotl.in/wasm/)…

我们欢迎你在公共 Slack 频道 [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web) 上提供关于 Compose/Web 和 Kotlin/Wasm 的反馈。
如果你遇到任何问题，请在 [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP) 上报告。