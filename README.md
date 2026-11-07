# Systemless AdBlock

Android ad blocker with two modes:

- **VPN** filters DNS on the phone. Traffic is not sent to a remote VPN server.
- **Hosts** applies a systemless hosts file with root.

## Settings

Appearance can follow the phone, or stay light or dark. The choice is saved.

Blocklist providers:

- HaGeZi Light, Normal, Pro, Pro++, Ultimate, and TIF
- StevenBlack unified hosts and its main extensions
- OISD Big and Small
- AdGuard DNS filter
- 1Hosts Lite and Xtra
- URLhaus malware hosts
- Phishing Army
- Dandelion Sprout Anti-Malware

The default list is StevenBlack Unified. A new choice is downloaded the next time protection is updated.

## Build

Requires JDK 17 and the Android SDK. Create `local.properties` with `sdk.dir` pointing at the SDK, then:

```bat
gradlew.bat assembleDebug
```

The debug APK is written to `build\outputs\apk\debug\`.
