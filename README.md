# KReader https://www.pgyer.com/kreader-android

A modern PDF reader built with Kotlin Multiplatform and Jetpack Compose, providing a smooth reading experience across multiple platforms.Support pdf, epub, mobi, xps, fb, cb, images, (docx, pptx)

## About

Why this app? There are many PDF readers based on the Android View system on the market, but I haven't seen any based on Compose.

Those that claim to be Compose PDF readers either use AndroidView wrappers around old View system readers, or are just simple lists that load documents without rich gestures and smooth zooming and scrolling effects.

So I made this app because I had already created a PDF reader based on Android View, and I wanted to implement a PDF reader based on Compose.

Currently, zooming and scrolling are very smooth, but the complete functionality from the old reader hasn't been migrated yet. I'll work on that gradually.

This app supports desktop, iOS and other platforms. Time is limited, so it's currently implemented on Android, while iOS requires compiling MuPDF, and the desktop version of MuPDF is easy to compile so I won't upload it.

## Features

- **Smooth Zooming & Scrolling**: Optimized for fluid reading experience
- **Rich Gestures**: Intuitive touch interactions
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