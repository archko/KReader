#!/bin/bash

# KReader 多平台打包脚本
# 用法: ./build-packages.sh [universal|intel|arm|windows|all]

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目配置
PROJECT_NAME="KReader"
VERSION="1.2.1"
BUILD_DIR="build/packages"
COMPOSE_BUILD_DIR="composeApp/build/compose/binaries/main"

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查操作系统
check_os() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        OS_TYPE="macos"
    elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
        OS_TYPE="windows"
    else
        print_error "不支持的操作系统: $OSTYPE"
        exit 1
    fi
    print_info "检测到操作系统: $OS_TYPE"
}

# 清理构建目录
clean_build() {
    print_info "清理构建目录..."
    #rm -rf "$BUILD_DIR"
    #mkdir -p "$BUILD_DIR"
}

# 编译项目 - 根据架构编译
compile_project() {
    local arch=$1
    local task_name="createDistributable"
    
    # 根据架构选择对应的 Gradle 任务
    case $arch in
        "x64"|"intel")
            task_name="createDistributableIntel"
            print_info "编译 Intel (x64) 版本..."
            ;;
        "aarch64"|"arm")
            task_name="createDistributableArm"
            print_info "编译 ARM (aarch64) 版本..."
            ;;
        "universal")
            task_name="createDistributableUniversal"
            print_info "编译 Universal 版本..."
            ;;
        "windows")
            task_name="createDistributableWindows"
            print_info "编译 Windows 版本..."
            ;;
        *)
            print_info "编译默认版本..."
            ;;
    esac
    
    # 检查是否有 gradlew
    if [ ! -f "./gradlew" ]; then
        print_error "找不到 gradlew 文件，请确保在项目根目录运行此脚本"
        exit 1
    fi
    
    # 清理之前的构建产物
    #./gradlew clean
    
    # 编译项目
    ./gradlew :composeApp:$task_name
    
    # 检查构建产物
    if [ ! -d "$COMPOSE_BUILD_DIR/app" ]; then
        print_error "编译失败，找不到构建产物在: $COMPOSE_BUILD_DIR/app"
        print_info "当前目录: $(pwd)"
        if [[ "$OS_TYPE" == "macos" ]]; then
            print_info "查找所有 .app 文件:"
            find . -name "*.app" -type d 2>/dev/null | head -10
        else
            print_info "查找所有应用文件:"
            find . -name "KReader*" -type f 2>/dev/null | head -10
        fi
        print_info "检查构建目录结构:"
        ls -la composeApp/build/compose/binaries/ 2>/dev/null || echo "构建目录不存在"
        exit 1
    fi
    
    print_info "找到构建产物: $COMPOSE_BUILD_DIR/app"
    print_success "项目编译完成 ($arch)"
}

# 创建 Universal 包（无运行时 + 双架构dylib）
build_universal() {
    if [[ "$OS_TYPE" != "macos" ]]; then
        print_warning "Universal 包只能在 macOS 上构建，跳过..."
        return
    fi
    
    # 编译 Universal 版本
    compile_project "universal"
    
    print_info "构建 Universal 包（无运行时）..."
    
    local output_dir="$BUILD_DIR/universal"
    mkdir -p "$output_dir"
    
    # 复制基础应用包
    cp -R "$COMPOSE_BUILD_DIR/app/${PROJECT_NAME}.app" "$output_dir/"
    
    # 移除运行时（如果存在）
    local runtime_dir="$output_dir/${PROJECT_NAME}.app/Contents/runtime"
    if [ -d "$runtime_dir" ]; then
        rm -rf "$runtime_dir"
        print_info "已移除运行时"
    fi
    
    # 修改 Info.plist 移除运行时相关配置
    local info_plist="$output_dir/${PROJECT_NAME}.app/Contents/Info.plist"
    if [ -f "$info_plist" ]; then
        # 移除 JVMRuntime 相关配置
        /usr/libexec/PlistBuddy -c "Delete :JVMRuntime" "$info_plist" 2>/dev/null || true
        print_info "已更新 Info.plist"
    fi
    
    # 创建 DMG
    print_info "创建 Universal DMG..."
    create_dmg "$output_dir/${PROJECT_NAME}.app" "$BUILD_DIR/${PROJECT_NAME}-${VERSION}-Universal.dmg"
    
    print_success "Universal 包构建完成: ${PROJECT_NAME}-${VERSION}-Universal.dmg"
}

# 创建平台特定包（含运行时）
build_platform_specific() {
    local arch=$1
    local arch_name=$2
    
    if [[ "$OS_TYPE" != "macos" ]]; then
        print_warning "macOS 平台特定包只能在 macOS 上构建，跳过..."
        return
    fi
    
    # 编译特定架构版本
    compile_project "$arch"
    
    print_info "构建 ${arch_name} 包（含运行时）..."
    
    local output_dir="$BUILD_DIR/${arch}"
    mkdir -p "$output_dir"
    
    # 复制基础应用包
    cp -R "$COMPOSE_BUILD_DIR/app/${PROJECT_NAME}.app" "$output_dir/"
    
    # 运行时已经在 createDistributable 时包含了，不需要额外处理
    # dylib 也已经根据架构选择性复制了
    
    # 创建 DMG
    print_info "创建 ${arch_name} DMG..."
    create_dmg "$output_dir/${PROJECT_NAME}.app" "$BUILD_DIR/${PROJECT_NAME}-${VERSION}-${arch_name}.dmg"
    
    print_success "${arch_name} 包构建完成: ${PROJECT_NAME}-${VERSION}-${arch_name}.dmg"
}

# 创建 Windows 包
build_windows() {
    if [[ "$OS_TYPE" != "windows" ]]; then
        print_warning "Windows 包只能在 Windows 系统上构建，跳过..."
        return
    fi
    
    # 编译 Windows 版本
    compile_project "windows"
    
    print_info "构建 Windows 包..."
    
    local output_dir="$BUILD_DIR/windows"
    mkdir -p "$output_dir"
    
    # 复制应用文件
    cp -R "$COMPOSE_BUILD_DIR/app/"* "$output_dir/"
    
    # 构建 MSI 安装包
    print_info "创建 Windows MSI 安装包..."
    ./gradlew :composeApp:packageMsi
    
    # 查找生成的 MSI 文件并复制到输出目录
    local msi_file=$(find composeApp/build/compose/binaries/main -name "*.msi" | head -1)
    if [ -f "$msi_file" ]; then
        cp "$msi_file" "$BUILD_DIR/${PROJECT_NAME}-${VERSION}-Windows.msi"
        print_success "Windows MSI 包构建完成: ${PROJECT_NAME}-${VERSION}-Windows.msi"
        
        # 创建包含文件关联工具的 ZIP 包
        print_info "创建包含文件关联工具的 ZIP 包..."
        (cd "$output_dir" && zip -r "../${PROJECT_NAME}-${VERSION}-Windows-with-FileAssociations.zip" .)
        print_success "Windows ZIP 包（含文件关联工具）构建完成: ${PROJECT_NAME}-${VERSION}-Windows-with-FileAssociations.zip"
    else
        print_warning "未找到 MSI 文件，创建 ZIP 包..."
        # 如果没有 MSI，创建 ZIP 包
        (cd "$output_dir" && zip -r "../${PROJECT_NAME}-${VERSION}-Windows.zip" .)
        print_success "Windows ZIP 包构建完成: ${PROJECT_NAME}-${VERSION}-Windows.zip"
    fi
}

# 创建 DMG 文件（仅 macOS）
create_dmg() {
    if [[ "$OS_TYPE" != "macos" ]]; then
        print_warning "DMG 文件只能在 macOS 上创建"
        return 1
    fi
    
    local app_path=$1
    local dmg_path=$2
    
    # 创建临时 DMG 目录
    local temp_dmg_dir=$(mktemp -d)
    cp -R "$app_path" "$temp_dmg_dir/"
    
    # 创建 Applications 链接
    ln -s /Applications "$temp_dmg_dir/Applications"
    
    # 创建 DMG
    hdiutil create -volname "${PROJECT_NAME}" -srcfolder "$temp_dmg_dir" -ov -format UDZO "$dmg_path"
    
    # 清理临时目录
    rm -rf "$temp_dmg_dir"
}

# 显示帮助信息
show_help() {
    echo "KReader 多平台打包脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  universal         构建 Universal 包（无运行时 + 双架构dylib）[仅 macOS]"
    echo "  intel             构建 Intel 包（含运行时 + 仅x64 dylib）[仅 macOS]"
    echo "  arm               构建 ARM 包（含运行时 + 仅aarch64 dylib）[仅 macOS]"
    echo "  windows           构建 Windows 包（含运行时 + x64 dll）[仅 Windows]"
    echo "  platform-specific 构建当前平台的包（自动检测架构和系统）"
    echo "  all               构建所有适用于当前系统的包"
    echo "  help              显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 all               # 构建所有适用包"
    echo "  $0 universal         # 只构建 Universal 包（macOS）"
    echo "  $0 windows           # 只构建 Windows 包（Windows）"
    echo "  $0 intel arm         # 构建 Intel 和 ARM 包（macOS）"
    echo "  $0 platform-specific # 构建当前平台的包"
    echo ""
    echo "注意:"
    echo "  - Universal 包需要用户安装 Java 17+"
    echo "  - 平台特定包包含运行时，开箱即用"
    echo "  - Windows 包生成 MSI 安装程序或 ZIP 压缩包"
    echo "  - macOS 包生成 DMG 磁盘映像"
}

# 显示构建结果
show_results() {
    print_success "构建完成！生成的包："
    echo ""
    
    # 显示 DMG 文件（macOS）
    for dmg in "$BUILD_DIR"/*.dmg; do
        if [ -f "$dmg" ]; then
            local size=$(du -h "$dmg" | cut -f1)
            echo "  📦 $(basename "$dmg") (${size})"
        fi
    done
    
    # 显示 MSI 文件（Windows）
    for msi in "$BUILD_DIR"/*.msi; do
        if [ -f "$msi" ]; then
            local size=$(du -h "$msi" | cut -f1)
            echo "  📦 $(basename "$msi") (${size})"
        fi
    done
    
    # 显示 ZIP 文件（Windows 备选）
    for zip in "$BUILD_DIR"/*.zip; do
        if [ -f "$zip" ]; then
            local size=$(du -h "$zip" | cut -f1)
            echo "  📦 $(basename "$zip") (${size})"
        fi
    done
    
    echo ""
    print_info "所有包位于: $BUILD_DIR"
}

# 主函数
main() {
    check_os
    
    # 如果没有参数或参数是 help，显示帮助
    if [ $# -eq 0 ] || [ "$1" = "help" ]; then
        show_help
        exit 0
    fi
    
    clean_build
    
    # 处理参数（每个架构单独编译）
    for arg in "$@"; do
        case $arg in
            universal)
                build_universal
                ;;
            intel)
                build_platform_specific "x64" "Intel"
                ;;
            arm)
                build_platform_specific "aarch64" "ARM"
                ;;
            windows)
                build_windows
                ;;
            platform-specific)
                # 构建当前平台的包
                if [[ "$OS_TYPE" == "macos" ]]; then
                    local current_arch=$(uname -m)
                    if [[ "$current_arch" == "arm64" ]]; then
                        build_platform_specific "aarch64" "ARM"
                    else
                        build_platform_specific "x64" "Intel"
                    fi
                elif [[ "$OS_TYPE" == "windows" ]]; then
                    build_windows
                fi
                ;;
            all)
                if [[ "$OS_TYPE" == "macos" ]]; then
                    build_universal
                    build_platform_specific "x64" "Intel"
                    build_platform_specific "aarch64" "ARM"
                elif [[ "$OS_TYPE" == "windows" ]]; then
                    build_windows
                fi
                ;;
            *)
                print_error "未知选项: $arg"
                show_help
                exit 1
                ;;
        esac
    done
    
    show_results
}

# 运行主函数
main "$@"