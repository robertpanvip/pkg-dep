# pkg-dep (DepsLens)

一个强大的 **IntelliJ IDEA 插件**，用于**包依赖可视化与智能分析**。

## 功能介绍

### 📊 依赖可视化
- **依赖关系图展示**：以可视化的方式展示项目中的包依赖关系
- **依赖树分析**：直观呈现依赖的层级结构
- **快速定位**：一键定位到依赖声明和使用位置

### 🔍 智能分析
- **Lockfile 解析**：支持解析多种 lockfile 格式（package-lock.json、yarn.lock 等）
- **Registry 响应解析**：从包管理器 registry 获取依赖元数据
- **版本范围判断**：使用 SemVer 精准判断版本兼容性
- **依赖冲突检测**：自动发现项目中的依赖冲突和重复安装

### 🛠️ 开发者友好
- **IDE 集成**：深度集成 IntelliJ IDEA 2025.1+
- **UI 工具窗口**：在编辑器中直接查看依赖信息
- **快捷操作**：快捷菜单和快捷键支持
- **即时反馈**：实时分析和高亮显示问题依赖

### 📦 支持范围
- **多语言生态**：支持 Node.js（npm/yarn）、Python、Java/Kotlin 等多种包管理系统
- **跨平台**：支持 Windows、macOS、Linux

## 技术架构

```
pkg-dep
├── core/                    # 核心分析引擎
│   ├── Lockfile 解析模块
│   ├── 版本范围计算（SemVer）
│   ├── 依赖冲突检测
│   └── Registry 数据处理
│
└── plugin/                  # IntelliJ IDEA 插件
    ├── UI 组件
    ├── IDE 集成
    ├── 快捷操作
    └── 工具窗口
```

## 技术栈

- **语言**：Kotlin 80% + HTML/JavaScript/CSS (UI)
- **IDE 平台**：IntelliJ Platform 2025.1 (Ultimate Edition)
- **构建工具**：Gradle 8.13
- **JDK**：Java 17
- **关键依赖**：
  - `kotlinx-serialization-json`：JSON 解析
  - `semver4j`：语义版本控制
  - `kotlinx-coroutines`：异步处理

## 快速开始

### 前置要求
- IntelliJ IDEA 2025.1 或更高版本
- Java 17 或更高版本
- Gradle 8.13

### 本地构建

```bash
# 克隆仓库
git clone https://github.com/robertpanvip/pkg-dep.git
cd pkg-dep

# 编译插件
./gradlew build

# 本地调试（启动 IDE 并加载插件）
./gradlew :plugin:runIde
```

### 安装插件

1. **从源码构建**：
   ```bash
   ./gradlew :plugin:buildPlugin
   ```
   产物位置：`plugin/build/distributions/pkg-dep-<version>.zip`

2. **在 IDEA 中安装**：
   - Settings → Plugins → Install Plugin from Disk
   - 选择生成的 `.zip` 文件

3. **从 JetBrains Marketplace 安装**（上线后）：
   - 直接在 IDEA Marketplace 中搜索 `pkg-dep`

## 使用示例

### 查看项目依赖
1. 打开 View → Tool Windows → DepsLens
2. 工具窗口将实时展示当前项目的依赖关系图
3. 点击依赖节点查看详细信息

### 检测版本冲突
1. 在工具窗口中查看冲突警告（黄/红高亮）
2. 点击冲突项跳转到 lockfile 或 manifest 文件
3. 一键导航到相关依赖声明

### 分析依赖路径
1. 搜索特定包名
2. 查看依赖链：`A → B → C`
3. 轻松定位间接依赖

## 项目结构

- **`core/`**：通用依赖分析库（可独立作为 SDK 使用）
- **`plugin/`**：IntelliJ IDEA 插件实现
- **`.github/`**：CI/CD 配置、Release 工作流
- **`gradle/`**：Gradle wrapper 和脚本

## 版本管理

- **默认版本**：0.1.0
- **发布版本**：通过 GitHub Actions Release 工作流自动打标签更新

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'Add your feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 提交 Pull Request

## 许可证

暂无许可证（待添加）

## 作者

- [robertpanvip](https://github.com/robertpanvip)

## 相关资源

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- [JetBrains Marketplace](https://plugins.jetbrains.com/)
- [Gradle Plugin 开发](https://gradle.org/)
