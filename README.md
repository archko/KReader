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
- **Auto crop margin**: Auto crop margin
- **Rich Gestures**: Intuitive touch interactions
- **Tts**: Use System Tts
- **Text Selection**: text selection for pdf/epub/mobi
- **Support album**: support album/image
- **Multi-platform Support**: Android, iOS, and Desktop
- **Modern UI**: Built with Jetpack Compose
- **Support format**: pdf, epub, mobi, djvu, xps, fb, cbz, images, (docx, pptx)
- **Editor**: create pdf from images. convert mobi/azw3 to epub. encrypt/decrypt pdf, export images, webdav backup

## Technology Stack

- **Kotlin Multiplatform**: Cross-platform development
- **Jetpack Compose**: Modern UI toolkit
- **MuPDF**: PDF rendering engine
- **SQLDelight**: Database management
- **Compose Multiplatform**: UI framework for multiple platforms

## Supported Platforms

- ✅ Android
- 🔄 iOS (requires MuPDF compilation)
- 🔄 Desktop (support mac, in windows MuPDF compilation needed)

## Development Status

- **Core Features**: ✅ Implemented
- **Smooth Scrolling**: ✅ Implemented
- **Zoom Functionality**: ✅ Implemented
- **Gesture Support**: ✅ Implemented
- **Windows dll**: 🔄 Pause

## Building

### Prerequisites

- Kotlin 1.9+
- Android Studio / IntelliJ IDEA
- Gradle 8.0+

### Android
- cd kreader
- git clone https://github.com/archko/dav4kmp.git -branch dev
download release page zip, unzip to .m2下,aar for mupdf, tiff, djvu, mobi

```bash
./gradlew assembleDebug installDebug
```

### Desktop

```bash
./gradlew composeApp:desktopRun -DmainClass=com.archko.reader.viewer.MainKt --quiet
```

## License

This project is licensed under the terms specified in the LICENSE file.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.