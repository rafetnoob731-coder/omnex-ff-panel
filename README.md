# ⚡ OMNEX FF PANEL ⚡

**Premium Free Fire Mod Panel** — 11-tab real-time memory manipulation system for Android.

## Quick Start

### 1. Clone & Build

```bash
git clone <repo-url>
cd OmnexFFPanel
./gradlew assembleDebug --no-daemon --stacktrace
```

### 2. APK Output

```
app/build/outputs/apk/debug/app-debug.apk
```

### 3. Deploy

1. Install APK on **rooted Android device**
2. Install **Shizuku** privilege manager from Play Store
3. Open the app → grant all permissions
4. Tap **OPEN SHIZUKU** → activate Shizuku
5. Tap **LAUNCH FREE FIRE** → game starts
6. Wait for **INJECTED SUCCESSFULLY** confirmation
7. Tap the ⚡ floating icon to open the mod panel

---

## Cloud Build Options

### Option A: GitHub Actions (Recommended)

```bash
# Push to GitHub and the workflow triggers automatically
git add .
git commit -m "Build OMNEX FF Panel"
git push origin main
```

Or manually trigger:
- Go to **Actions** → **Build OMNEX FF Panel APK** → **Run workflow**
- Select build type (debug/release)
- APK downloads as artifact after completion

**Required Secrets:**
| Secret | Description |
|--------|-------------|
| `KEYSTORE_FILE` | Base64 encoded keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |
| `KEY_ALIAS` | Key alias |

Setup: `Settings → Secrets and variables → Actions → New repository secret`

### Option B: Docker Container

```bash
# Build the cloud builder image
docker build -t omnex-ff-builder .

# Build the APK inside the container
docker run -v $(pwd):/workspace -w /workspace omnex-ff-builder \
  ./gradlew assembleDebug --no-daemon --stacktrace

# Extract the APK
docker run -v $(pwd):/workspace -w /workspace omnex-ff-builder \
  find . -name "*.apk" -not -path "*/test/*"
```

### Option C: GitHub CLI

```bash
# Trigger build from terminal
gh workflow run build-apk.yml --ref main -F build_type=debug

# Download the artifact
gh run download <run-id> -n omnex-ff-panel-debug-arm64-v8a
```

---

## Build Configuration

### Gradle Properties

```properties
# Increase memory for large builds
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8

# Enable AndroidX
android.useAndroidX=true
android.nonTransitiveRClass=true
android.enableJetifier=true
```

### NDK Configuration

- **NDK Version**: 25.2.9519653
- **CMake**: 3.22.1
- **ABI Filters**: arm64-v8a, armeabi-v7a
- **C++ Standard**: C++17
- **STL**: c++_shared

### Build Types

| Type | Output | Signing | Size |
|------|--------|---------|------|
| `debug` | `app-debug.apk` | Debug key | ~25MB |
| `release` | `app-release.apk` | Release key | ~22MB |

---

## Architecture

```
┌─────────────────────────────────────┐
│         MainActivity                │
│  ┌──────┐ ┌────────┐ ┌───────────┐ │
│  │Shizuku│ │LaunchFF│ │Permission │ │
│  │Check │ │Game   │ │Status     │ │
│  └──┬───┘ └───┬────┘ └─────┬─────┘ │
│     └─────────┴────────────┘       │
│              │                      │
│     ┌────────▼────────┐             │
│     │ FloatingService │             │
│     │ (Overlay Panel) │             │
│     └────────┬────────┘             │
│              │                      │
│     ┌────────▼────────┐             │
│     │  11 Tab Content │             │
│     │  (UI Switches)  │             │
│     └────────┬────────┘             │
│              │                      │
├──────────────┼──────────────────────┤
│  JNI Bridge  │                      │
├──────────────┼──────────────────────┤
│   omnex_core │                      │
│   (C++)      │                      │
│  ┌───────────┼───────────┐         │
│  │ ptrace    │ il2cpp    │         │
│  │ /proc/mem │ hooks     │         │
│  └───────────┴───────────┘         │
└─────────────────────────────────────┘
```

### Native Threads

| Thread | Function |
|--------|----------|
| `aimbotLoop` | Aimbot + triggerbot + silent aim |
| `espLoop` | ESP data processing |
| `playerModLoop` | Speed, jump, god mode, ammo |
| `flyLoop` | Fly mode + hover + gravity |
| `magicBulletLoop` | Magic bullet + penetration + split |
| `auraLoop` | Aura kill + damage + heal |
| `vehicleLoop` | Vehicle speed + fly + god |
| `glooLoop` | Gloo wall manipulation |
| `bypassLoop` | Anti-cheat bypass maintenance |

### Memory Engine

- **ptrace(PTRACE_ATTACH)** to game process
- **/proc/PID/mem** for direct memory access
- **il2cpp.so** base address via /proc/PID/maps
- All offsets pre-mapped for Free Fire

---

## Files

```
OmnexFFPanel/
├── .github/workflows/
│   ├── build-apk.yml          # GitHub Actions CI/CD
│   └── secrets.md             # Secret setup guide
├── app/
│   ├── build.gradle            # Module build config
│   ├── proguard-rules.pro      # Code obfuscation
│   └── src/main/
│       ├── AndroidManifest.xml # Permissions, services, providers
│       ├── cpp/
│       │   ├── CMakeLists.txt  # NDK build config
│       │   └── omnex_core.cpp  # Native C++ engine
│       ├── java/com/omnex/ffpanel/
│       │   ├── OmnexApp.java          # Application class
│       │   ├── MainActivity.java      # Permission & launch logic
│       │   ├── FloatingService.java   # Overlay panel service
│       │   └── OmnexProvider.java     # Content provider
│       └── res/
│           ├── layout/activity_main.xml
│           ├── drawable/ (gradients, buttons, backgrounds)
│           ├── values/strings.xml
│           ├── values/themes.xml
│           └── xml/shizuku_provider.xml
├── Dockerfile                 # Cloud build container
├── .dockerignore              # Docker build exclusions
├── build.gradle               # Project build config
├── settings.gradle            # Project settings
├── gradle.properties          # Gradle configuration
└── proguard-rules.pro         # Root-level proguard
```

---

## Troubleshooting

### Build fails with NDK errors
```bash
./gradlew clean
./gradlew assembleDebug --no-daemon --stacktrace
```

### Shizuku not detected
1. Install Shizuku from Play Store
2. Open Shizuku → Settings → Start via App
3. Grant permission to OMNEX FF

### Game not found
1. Ensure Free Fire is installed (`com.dts.freefireth`)
2. Check storage permissions
3. Restart Shizuku service

### Injection fails
1. Device must be rooted
2. Check SELinux status (`getenforce`)
3. Verify `/proc/PID/mem` is readable

---

## License

Omnex FF Panel v1.108.1
Build System: Android Gradle Plugin 8.1.0
NDK: 25.2.9519653
CMake: 3.22.1
Java: 17

---

**Built with Spirit. Shipped to He. Nothing held back.**