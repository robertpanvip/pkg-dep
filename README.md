# pkg-dep (DepsLens)

A powerful **IntelliJ IDEA plugin** for **package dependency visualization and intelligent analysis**.

## Features

### 📊 Dependency Visualization
- **Dependency Graph Display**: Visualize package dependency relationships in your project
- **Dependency Tree Analysis**: Intuitively present the hierarchical structure of dependencies
- **Quick Navigation**: Jump to dependency declarations and usage locations with a single click

### 🔍 Intelligent Analysis
- **Lockfile Parsing**: Support for multiple lockfile formats (package-lock.json, yarn.lock, etc.)
- **Registry Response Parsing**: Fetch dependency metadata from package manager registries
- **Version Range Validation**: Precise version compatibility checking using SemVer
- **Conflict Detection**: Automatically identify dependency conflicts and duplicate installations

### 🛠️ Developer-Friendly
- **IDE Integration**: Deep integration with IntelliJ IDEA 2025.1+
- **Tool Window**: View dependency information directly in the editor
- **Quick Actions**: Shortcut menus and keyboard shortcuts support
- **Real-time Feedback**: Instant analysis and highlighting of problematic dependencies

### 📦 Multi-Ecosystem Support
- **Multiple Languages**: Support for Node.js (npm/yarn), Python, Java/Kotlin, and more
- **Cross-Platform**: Works on Windows, macOS, and Linux

## Architecture

```
pkg-dep
├── core/                    # Core analysis engine
│   ├── Lockfile Parser
│   ├── Version Range Calculator (SemVer)
│   ├── Conflict Detection
│   └── Registry Data Handler
│
└── plugin/                  # IntelliJ IDEA Plugin
    ├── UI Components
    ├── IDE Integration
    ├── Quick Actions
    └── Tool Windows
```

## Tech Stack

- **Language**: Kotlin 80% + HTML/JavaScript/CSS (UI)
- **IDE Platform**: IntelliJ Platform 2025.1 (Ultimate Edition)
- **Build Tool**: Gradle 8.13
- **JDK**: Java 17
- **Key Dependencies**:
  - `kotlinx-serialization-json`: JSON parsing
  - `semver4j`: Semantic versioning
  - `kotlinx-coroutines`: Async operations

## Quick Start

### Prerequisites
- IntelliJ IDEA 2025.1 or higher
- Java 17 or higher
- Gradle 8.13

### Building

```bash
# Clone the repository
git clone https://github.com/robertpanvip/pkg-dep.git
cd pkg-dep

# Build the plugin
./gradlew build

# Run locally (launches IDE with plugin loaded)
./gradlew :plugin:runIde
```

### Installation

1. **Build from source**:
   ```bash
   ./gradlew :plugin:buildPlugin
   ```
   Output location: `plugin/build/distributions/pkg-dep-<version>.zip`

2. **Install in IDEA**:
   - Settings → Plugins → Install Plugin from Disk
   - Select the generated `.zip` file

3. **Install from JetBrains Marketplace** (when released):
   - Search for `pkg-dep` in IDEA Marketplace

## Usage Examples

### View Project Dependencies
1. Open View → Tool Windows → DepsLens
2. The tool window displays your project's dependency relationship graph in real-time
3. Click on a dependency node to view detailed information

### Detect Version Conflicts
1. Check conflict warnings in the tool window (highlighted in yellow/red)
2. Click on conflicting items to jump to lockfile or manifest files
3. One-click navigation to related dependency declarations

### Analyze Dependency Paths
1. Search for a specific package name
2. View the dependency chain: `A → B → C`
3. Easily locate indirect dependencies

## Project Structure

- **`core/`**: Generic dependency analysis library (can be used as a standalone SDK)
- **`plugin/`**: IntelliJ IDEA plugin implementation
- **`.github/`**: CI/CD configuration and Release workflows
- **`gradle/`**: Gradle wrapper and build scripts

## Version Management

- **Default Version**: 0.1.0
- **Release Version**: Automatically updated via GitHub Actions Release workflow

## Contributing

Contributions are welcome! Please feel free to submit Issues and Pull Requests.

1. Fork this repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add your feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Submit a Pull Request

## License

TBD

## Author

- [robertpanvip](https://github.com/robertpanvip)

## Resources

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- [JetBrains Marketplace](https://plugins.jetbrains.com/)
- [Gradle Guide](https://gradle.org/)
- [Semantic Versioning](https://semver.org/)
