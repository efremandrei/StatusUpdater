# Status Updater

A small native Android app and home-screen widget for a Ulanzi TC001 running **AWTRIX 3 / AWTRIX Light**. Save your matrix address, type a message, and choose how long it should appear. The default is **one hour**.

## Use

1. Install the APK from [Releases](https://github.com/efremandrei/StatusUpdater/releases).
2. Connect your phone and matrix to the same reachable Wi-Fi network.
3. Enter the matrix IP (for example `192.168.1.50`). Hostnames such as `awtrix.local`, optional ports, and explicit HTTP/HTTPS addresses also work. Most AWTRIX devices use HTTP.
4. Use **Test connection** to check the AWTRIX API. Enter your text and duration in minutes (1–1440).
5. Tap **Show text on matrix** to save and send, or **Save settings** to prepare the widget without sending.
6. Tap **Add home-screen widget** and confirm with your launcher. You can also long-press the home screen → Widgets → Status Updater.
7. Tap the widget's **Show text** button to send the saved settings directly. **Edit** opens the app.

A new send replaces the matrix's current notification and restarts the duration. The firmware owns the timer, so the phone does not need to remain awake or connected after the request succeeds. At expiry, AWTRIX resumes its normal display. Matrix buttons, another sender, a reboot, or firmware settings can interrupt the notification earlier.

**Clear display** dismisses the current notification, including one sent by another app. It does not turn the matrix off. The app asks before dismissing it.

The widget shows request results, not live matrix state. “Sent” means the matrix accepted the HTTP request; it is not a live display monitor. All widget instances use the same last-saved configuration. Unsaved edits are not sent by the widget.

## Compatibility and privacy

- Android 8.0+ (API 26+), including Samsung arm64 phones. The app contains no native libraries, so the same APK also works on other Android ABIs.
- Firmware: AWTRIX 3 and compatible AWTRIX Light builds with the documented `/api/notify` API. Stock Ulanzi firmware, AWTRIX 2, and AWTRIX NG use different APIs and are not supported.
- This version expects an API without HTTP authentication. Authentication failures are reported explicitly.
- Text is sent as UTF-8 JSON. Supported characters, font, letter case, and scrolling depend on the firmware. The local 32×8 preview is approximate; it is not a live screen feed.
- No accounts, cloud services, analytics, ads, or periodic network activity. Settings are stored privately on the phone, with backup disabled. HTTP sends the message unencrypted on the local network. Use a trusted network.
- Widget requests use a short foreground service with a temporary system notification and a bounded wake lock. No exact alarm or battery optimization exemption is required. Failed sends are not automatically retried later.
- First launch uses dark mode; moon/sun controls select and save your preferred theme. A custom adaptive icon and branded native startup splash are included.

## API

Based on the [official AWTRIX API](https://github.com/Blueforcer/awtrix3/blob/main/docs/api.md).

```http
POST http://MATRIX_IP/api/notify
Content-Type: application/json; charset=utf-8

{"text":"In a meeting","duration":3600,"hold":false,"repeat":-1,"stack":false,"wakeup":true,"textCase":2,"color":"#FFBD66"}
```

`GET /api/stats` tests the connection. `POST /api/notify/dismiss` clears the current notification. Redirects, web login pages, non-2xx responses, and network failures are reported as errors.

## Build and test

Use JDK 17+, Android SDK platform 35, and the included Gradle 8.13 wrapper. Set `ANDROID_HOME` or create an ignored `local.properties` with `sdk.dir`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

Unit tests exercise address/duration validation, Unicode JSON, exact API requests, and failure handling against a mock HTTP server. Android instrumentation tests cover defaults, theme recreation, settings persistence, foreground requests, error reporting, and a real AppWidgetHost button request that completes on the home screen. Tests use a simulated AWTRIX endpoint; they do not validate a physical Ulanzi or Samsung device.

Release signing uses a private keystore kept outside the repository. Configure `STATUSUPDATER_KEYSTORE`, `STATUSUPDATER_STORE_PASSWORD`, and `STATUSUPDATER_KEY_PASSWORD`, with alias `statusupdater`, then run `./gradlew testReleaseUnitTest lintRelease assembleRelease`. Preserve this keystore for future in-place updates. Never commit it or its passwords. Release filenames include the version and build number.

GitHub Actions runs unit tests, lint, and a debug build on pushes/PRs. Its debug APK is a CI artifact; install the signed release APK for normal use. A debug APK uses different signing and cannot update an installed release.

## Author

Andrei Efremuahkin · [andrei.efr@gmail.com](mailto:andrei.efr@gmail.com)

[Repository](https://github.com/efremandrei/StatusUpdater)
