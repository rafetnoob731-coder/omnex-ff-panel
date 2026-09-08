# GitHub Secrets Required

Go to: Settings → Secrets and variables → Actions → New repository secret

| Secret Name | Description | Format |
|-------------|-------------|--------|
| `KEYSTORE_FILE` | Base64 encoded keystore file | `base64 -w0 my-release-key.jks` |
| `KEYSTORE_PASSWORD` | Keystore password | Plain text |
| `KEY_PASSWORD` | Key password | Plain text |
| `KEY_ALIAS` | Key alias name | Plain text |
| `KEYSTORE_BASE64` | Alternative: Base64 keystore | `base64 -w0 my-release-key.jks` |
| `FIREBASE_SERVICE_ACCOUNT` | Firebase service account JSON | JSON string |
| `FIREBASE_PROJECT_ID` | Firebase project ID | Plain text |
| `GITHUB_TOKEN` | Auto-provided by GitHub | Not needed to set manually |

## Generate Release Keystore

```bash
keytool -genkey -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias omnex-release \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=OMNEX, OU=Development, O=Omnex, L=City, ST=State, C=US"

# Convert to Base64
base64 -w0 my-release-key.jks
```

## Build Commands

### Build Debug APK
```bash
./gradlew assembleDebug --no-daemon --stacktrace
```

### Build Release APK
```bash
./gradlew assembleRelease --no-daemon --stacktrace
```

### Build with NDK
```bash
./gradlew assembleDebug externalNativeBuildDebug --no-daemon --stacktrace
```