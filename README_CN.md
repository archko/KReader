# KReader https://www.pgyer.com/kreader-android

基于 Kotlin Multiplatform 和 Jetpack Compose 构建的现代 PDF 阅读器，为多平台提供流畅的阅读体验。目前支持 pdf, epub, mobi, djvu, xps, fb, cb, 图片, (无图的docx, pptx).
还支持tiff, bigtiff的浏览.

## 关于

为什么有这个app,市面上基于android view系统的pdf阅读器很多,基于compose的,目前没有看到有.\n
所以我做了这个app,一个基于compose的pdf阅读器,支持全平台.\n
目前缩放,滚动已经非常流畅,旧阅读器的完整功能未迁移过来.\n
时间有限,目前在android已经实现,ios则需要编译mupdf,桌面端的mupdf容易编译就不上传了.\n
epub/mobi支持自定义字体,放在/sdcard/fonts/,ttf与otf两种字体

## 功能特性

- **流畅缩放与滚动**: 优化的阅读体验
- **丰富手势**: 直观的触摸交互
- **语音朗读**: 使用系统语音朗读功能
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