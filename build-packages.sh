#!/bin/bash

# KReader 多平台打包脚本
# 用法: ./build-packages.sh [universal|intel|arm|all]

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目配置
PROJECT_NAME="KReader"
VERSION="1.0.0"
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

# 检查是否在 macOS 上运行
check_macos() {
    if [[ "$OSTYPE" != "darwin"* ]]; then
        print_error "此脚本只能在 macOS 上运行"
        exit 1
    fi
}

# 清理构建目录
clean_build() {
    print_info "清理构建目录..."
    #rm -rf "$BUILD_DIR"
    #mkdir -p "$BUILD_DIR"
}

# 编译项目（只编译一次）
compile_project() {
    print_info "编译项目..."
    
    # 检查是否有 gradlew
    if [ ! -f "./gradlew" ]; then
        print_error "找不到 gradlew 文件，请确保在项目根目录运行此脚本"
        exit 1
    fi
    
    # 编译项目（包含所有 dylib）
    ./gradlew :composeApp:createDistributable
    
    # 检查构建产物
    if [ ! -d "$COMPOSE_BUILD_DIR/app" ]; then
        print_error "编译失败，找不到构建产物在: $COMPOSE_BUILD_DIR/app"
        print_info "当前目录: $(pwd)"
        print_info "查找所有 .app 文件:"
        find . -name "*.app" -type d 2>/dev/null | head -10
        print_info "检查构建目录结构:"
        ls -la composeApp/build/compose/binaries/ 2>/dev/null || echo "构建目录不存在"
        exit 1
    fi
    
    print_info "找到构建产物: $COMPOSE_BUILD_DIR/app"
    print_success "项目编译完成"
}

# 创建 Universal 包（无运行时 + 双架构dylib）
build_universal() {
    print_info "构建 Universal 包（无运行时）..."
    
    local output_dir="$BUILD_DIR/universal"
    mkdir -p "$output_dir"
    
    # 复制基础应用包
    cp -R "$COMPOSE_BUILD_DIR/app/${PROJECT_NAME}.app" "$output_dir/"
    
    # 确保 Resources 目录存在
    local resources_dir="$output_dir/${PROJECT_NAME}.app/Contents/Resources"
    mkdir -p "$resources_dir"
    
    # 复制两个架构的 dylib
    if [ -d "composeApp/src/commonMain/resources/macos-x64" ]; then
        cp -R "composeApp/src/commonMain/resources/macos-x64" "$resources_dir/"
        print_info "已复制 x64 dylib"
    fi
    
    if [ -d "composeApp/src/commonMain/resources/macos-aarch64" ]; then
        cp -R "composeApp/src/commonMain/resources/macos-aarch64" "$resources_dir/"
        print_info "已复制 aarch64 dylib"
    fi
    
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
    
    print_info "构建 ${arch_name} 包（含运行时）..."
    
    local output_dir="$BUILD_DIR/${arch}"
    mkdir -p "$output_dir"
    
    # 复制基础应用包
    cp -R "$COMPOSE_BUILD_DIR/app/${PROJECT_NAME}.app" "$output_dir/"
    
    # 确保 Resources 目录存在
    local resources_dir="$output_dir/${PROJECT_NAME}.app/Contents/Resources"
    mkdir -p "$resources_dir"
    
    # 移除不需要的架构的 dylib
    if [ "$arch" = "x64" ]; then
        # Intel 包：保留 x64，移除 aarch64
        if [ -d "$resources_dir/macos-aarch64" ]; then
            rm -rf "$resources_dir/macos-aarch64"
            print_info "已移除 aarch64 dylib"
        fi
        print_info "保留 x64 dylib"
    elif [ "$arch" = "aarch64" ]; then
        # ARM 包：保留 aarch64，移除 x64
        if [ -d "$resources_dir/macos-x64" ]; then
            rm -rf "$resources_dir/macos-x64"
            print_info "已移除 x64 dylib"
        fi
        print_info "保留 aarch64 dylib"
    fi
    
    # 运行时已经在 createDistributable 时包含了，不需要额外处理
    
    # 创建 DMG
    print_info "创建 ${arch_name} DMG..."
    create_dmg "$output_dir/${PROJECT_NAME}.app" "$BUILD_DIR/${PROJECT_NAME}-${VERSION}-${arch_name}.dmg"
    
    print_success "${arch_name} 包构建完成: ${PROJECT_NAME}-${VERSION}-${arch_name}.dmg"
}

# 创建 DMG 文件
create_dmg() {
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
    echo "  universal         构建 Universal 包（无运行时 + 双架构dylib）"
    echo "  intel             构建 Intel 包（含运行时 + 仅x64 dylib）"
    echo "  arm               构建 ARM 包（含运行时 + 仅aarch64 dylib）"
    echo "  platform-specific 构建当前平台的包（自动检测架构）"
    echo "  all               构建所有类型的包"
    echo "  help              显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 all               # 构建所有包"
    echo "  $0 universal         # 只构建 Universal 包"
    echo "  $0 intel arm         # 构建 Intel 和 ARM 包"
    echo "  $0 platform-specific # 构建当前平台的包"
    echo ""
    echo "注意:"
    echo "  - Universal 包需要用户安装 Java 17+"
    echo "  - 平台特定包包含运行时，开箱即用"
    echo "  - 每种包类型会单独编译，确保 dylib 架构正确"
}

# 显示构建结果
show_results() {
    print_success "构建完成！生成的包："
    echo ""
    
    for dmg in "$BUILD_DIR"/*.dmg; do
        if [ -f "$dmg" ]; then
            local size=$(du -h "$dmg" | cut -f1)
            echo "  📦 $(basename "$dmg") (${size})"
        fi
    done
    
    echo ""
    print_info "所有包位于: $BUILD_DIR"
}

# 主函数
main() {
    check_macos
    
    # 如果没有参数或参数是 help，显示帮助
    if [ $# -eq 0 ] || [ "$1" = "help" ]; then
        show_help
        exit 0
    fi
    
    clean_build
    
    # 只编译一次
    compile_project
    
    # 处理参数
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
            platform-specific)
                # 构建当前平台的包
                local current_arch=$(uname -m)
                if [[ "$current_arch" == "arm64" ]]; then
                    build_platform_specific "aarch64" "ARM"
                else
                    build_platform_specific "x64" "Intel"
                fi
                ;;
            all)
                build_universal
                build_platform_specific "x64" "Intel"
                build_platform_specific "aarch64" "ARM"
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