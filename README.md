# Vexis

Vexis is an Android VPN client for Xray and AmneziaWG. Connect to your own server or import a subscription from your VPN provider.

- Import subscriptions and individual servers by link or QR code.
- Search servers and group them by country or subscription.
- Test latency, connect to a chosen server, or let Vexis select and reconnect to a server automatically.
- Use per-app VPN routing and traffic rules for domains and IP addresses.
- Choose a light or dark theme and use English, Russian, Chinese, Arabic, or French.

## Install

Vexis requires Android 7.0 or later. Download the signed APK from the repository’s **Releases** page. Open the **Servers** tab and tap **+** to import a server configuration or subscription.

## Build from source

1. Install JDK 17 and Android SDK Platform 34. Configure the SDK path in Android Studio or set `ANDROID_HOME` for command-line builds.
2. Download `libv2ray.aar` from [AndroidLibXrayLite v26.11.9](https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.11.9) and place it under `app/libs/`. Its SHA-256 is `cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155`.
3. Run the Gradle wrapper from the project root. Use `sh ./gradlew :app:assembleDebug` on Linux or macOS and `.\gradlew.bat :app:assembleDebug` in Windows PowerShell. The APK is generated under `app/build/outputs/apk/debug/`.

On Windows, run `powershell -NoProfile -ExecutionPolicy Bypass -File .\install-debug.ps1` with one authorized Android device connected over USB to build and install the debug version.

## License

Vexis’s own code is available under the [MIT License](LICENSE).

Xray, AmneziaWG, and other bundled components retain their own licenses. See the [component inventory](THIRD-PARTY.md) and [license notices](app/src/main/assets/licenses/). Release archives contain additional third-party source materials and instructions for rebuilding with a replacement Xray library.

Contact the maintainer, Ilya Chevardin, on [Telegram](https://t.me/Ilya_chev).
