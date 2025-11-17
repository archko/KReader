# KReader https://www.pgyer.com/kreader-android

A modern PDF reader built with Kotlin Multiplatform and Jetpack Compose, providing a smooth reading experience across multiple platforms.Support pdf, epub, mobi, djvu, xps, fb, cb, images, (docx, pptx)
also support tiff, bigtiff.

## About

Why did I create this app? There are many PDF readers based on the Android View system on the market, but so far, none have been built on Compose.\n
That’s why I developed this app—a Compose-based PDF reader that supports all platforms.\n
Currently, zooming and scrolling functions are very smooth, but the full range of features from the old reader has not yet been migrated.\n
Due to time constraints, the app has been implemented on Android. For iOS, compiling MuPDF is required, while the desktop version of MuPDF is easy to compile, so it won’t be uploaded.\n
At present, epub/mobi supported custom font. It should be placed in the directory /sdcard/fonts/, and both TTF and OTF font formats are compatible.\n

## Features

- **Smooth Zooming & Scrolling**: Optimized for fluid reading experience
- **Rich Gestures**: Intuitive touch interactions
- **Tts**: Use System Tts
- **Multi-platform Support**: Android, iOS, and Desktop
- **Modern UI**: Built with Jetpack Compose
- **Native Performance**: Kotlin Multiplatform architecture

## Technology Stack

- **Kotlin Multiplatform**: Cross-platform development
- **Jetpack Compose**: Modern UI toolkit
- **MuPDF**: PDF rendering engine
- **SQLDelight**: Database management
- **Compose Multiplatform**: UI framework for multiple platforms

## Supported Platforms

- ✅ Android
- 🔄 iOS (requires MuPDF compilation)
- 🔄 Desktop (MuPDF compilation needed)

## Development Status

- **Core Features**: ✅ Implemented
- **Smooth Scrolling**: ✅ Working
- **Zoom Functionality**: ✅ Working
- **Gesture Support**: ✅ Working
- **Full Feature Migration**: 🔄 In Progress

## Building

### Prerequisites

- Kotlin 1.9+
- Android Studio / IntelliJ IDEA
- Gradle 8.0+

### Android

```bash
./gradlew :composeApp:assembleDebug
```

### Desktop

```bash
./gradlew :composeApp:run
```

## License

This project is licensed under the terms specified in the LICENSE file.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.