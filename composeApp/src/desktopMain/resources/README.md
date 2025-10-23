# 桌面端资源文件

## 应用图标

为了完整的应用体验，请在此目录下放置以下图标文件：

- `icon.icns` - macOS 应用图标 (推荐尺寸: 1024x1024)
- `icon.ico` - Windows 应用图标 (推荐尺寸: 256x256)
- `icon.png` - Linux 应用图标 (推荐尺寸: 512x512)

## 支持的文件格式

应用支持以下文件类型：
- PDF 文档: `.pdf`
- TIFF 图像: `.tiff`, `.tif`
- 常见图像格式: `.jpg`, `.jpeg`, `.png`, `.gif`, `.bmp`, `.webp`

## 构建和安装

1. 构建应用:
   ```bash
   ./gradlew packageDistributionForCurrentOS
   ```

2. 安装生成的安装包（位于 `build/compose/binaries/main/` 目录）

## 使用方法

### 1. 命令行打开
```bash
./KReader /path/to/document.pdf
```

### 2. 手动设置文件关联

#### macOS:
1. 右键点击 PDF 文件
2. 选择"显示简介"
3. 在"打开方式"部分选择 KReader
4. 点击"全部更改"以应用到所有同类型文件

#### Windows:
1. 右键点击 PDF 文件
2. 选择"打开方式" -> "选择其他应用"
3. 浏览并选择 KReader.exe
4. 勾选"始终使用此应用打开 .pdf 文件"

#### Linux:
1. 右键点击 PDF 文件
2. 选择"属性" -> "打开方式"
3. 添加 KReader 作为默认应用

### 3. 拖拽打开
- 将文件拖拽到应用图标上
- 或者拖拽到已打开的应用窗口中