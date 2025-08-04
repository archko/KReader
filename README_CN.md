# KReader https://www.pgyer.com/kreader-android

基于 Kotlin Multiplatform 和 Jetpack Compose 构建的现代 PDF 阅读器，为多平台提供流畅的阅读体验。目前支持 pdf, epub, mobi, xps, fb, cb, 图片, (无图的docx, pptx).

## 关于

为什么有这个app？市面上基于 Android View 系统的 PDF 阅读器很多，基于 Compose 的，目前没有看到有。

那些说是 Compose 的 PDF 阅读器，要么基于的是旧的 View 系统的阅读器使用 AndroidView 包装，要么就是简单的列表，加载文档，没有丰富的手势，流畅的缩放与滚动的效果。

所以我做了这个 app，因为我已经做了一个基于 Android View 的 PDF 阅读器了，我想实现一个基于 Compose 的 PDF 阅读器。

目前缩放、滚动已经非常流畅，旧阅读器的完整功能未迁移过来，以后慢慢做。

此 app 支持桌面端、iOS 等多平台。时间有限，目前在 Android 已经实现，iOS 则需要编译 MuPDF，桌面端的 MuPDF 容易编译就不上传了。

## 功能特性

- **流畅缩放与滚动**: 优化的阅读体验
- **丰富手势**: 直观的触摸交互
- **多平台支持**: Android、iOS 和桌面端
- **现代界面**: 基于 Jetpack Compose 构建
- **原生性能**: Kotlin Multiplatform 架构

## 技术栈

- **Kotlin Multiplatform**: 跨平台开发
- **Jetpack Compose**: 现代 UI 工具包
- **MuPDF**: PDF 渲染引擎
- **SQLDelight**: 数据库管理
- **Compose Multiplatform**: 多平台 UI 框架

## 支持的平台

- ✅ Android
- 🔄 iOS (需要编译 MuPDF)
- 🔄 桌面端 (需要编译 MuPDF)

## 开发状态

- **核心功能**: ✅ 已实现
- **流畅滚动**: ✅ 正常工作
- **缩放功能**: ✅ 正常工作
- **手势支持**: ✅ 正常工作
- **完整功能迁移**: 🔄 进行中

## 构建

### 前置要求

- Kotlin 1.9+
- Android Studio / IntelliJ IDEA
- Gradle 8.0+

### Android

```bash
./gradlew :composeApp:assembleDebug
```

### 桌面端

```bash
./gradlew :composeApp:run
```

## 许可证

本项目遵循 LICENSE 文件中指定的条款。

## 贡献

欢迎贡献！请随时提交 Pull Request。 