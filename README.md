# Status Updater

A small native Android app and home-screen widget for a Ulanzi TC001 running **AWTRIX 3 / AWTRIX Light**. Save your matrix address, type a message, and choose how long it should appear. The default is **one hour**.

## Use

1. Install the APK from [Releases](https://github.com/efremandrei/StatusUpdater/releases).
2. Connect your phone and matrix to the same reachable Wi-Fi network.
3. Enter the matrix IP (for example `192.168.1.50`). Hostnames such as `awtrix.local`, optional ports, and explicit HTTP/HTTPS addresses also work. Most AWTRIX devices use HTTP.
4. Use **Test connection** to check the AWTRIX API. Enter your text and duration in minutes (1–1440). Tap **Text color** under the message to choose one of 12 swatches or enter a custom six-digit RGB hex color (`#RRGGBB`). The picker previews your message; **Use color** applies it to the draft and the LED preview. **Cancel** leaves your previous choice intact. Amber (`#FFBD66`) remains the default.
5. Tap **Show text on matrix** to save and send, or **Save settings** to prepare the widget without sending.
6. Tap **Add home-screen widget**, choose **Compact · 1×1** or **Full · 3×2**, and confirm with your launcher. You can also long-press the home screen → Widgets → Status Updater. The compact widget is a single-cell button: green **SHOW** starts your saved text, amber **STOP** returns to the clock, and **WAIT** prevents duplicate taps while a request runs. Before setup, tapping it opens the app. The full widget also shows your message and status, with an **Edit** shortcut.
7. Tap the widget's **Show text** button to send the saved settings directly. It becomes an amber **Stop text** button while the message is running. Tap **Stop text** to cancel the display timer, dismiss the notification, and select AWTRIX's clock (`Time`). **Edit** opens the app.

A new send replaces the matrix's current notification and restarts the duration. The firmware owns the timer, so the phone does not need to remain awake or connected after the request succeeds. At expiry, AWTRIX resumes its normal display. Matrix buttons, another sender, a reboot, or firmware settings can interrupt the notification earlier.

The app's **Stop text** action also dismisses the current notification and selects the clock. It asks for confirmation; the widget stops immediately with one tap. Dismissing a notification can affect one sent by another app. Normal AWTRIX clock/app rotation settings remain in effect.

The widget remembers the active message, its original IP, and its deadline. Saving a different IP/text/duration prepares the next send without changing the running message or its Stop target. All widget instances share this state. A failed start keeps **Show text**; a failed Stop keeps **Stop text** for retry. The state survives app restarts and updates. Expiry resets it to **Show text**. Android may defer the visual expiry refresh while idle; a still-visible **Stop text** button always stops and never starts a new message. No exact-alarm permission is needed.

The widget's “Text on · until …” state is based on the last accepted request and elapsed time, not a live matrix feed. Other senders, matrix buttons, or reboots can change the display independently. Unsaved edits are not sent by the widget.

Your chosen text color is saved with the IP, message, and duration when you tap **Save settings** or **Show text on matrix**. Both widget sizes send that saved color. The full widget previews the saved color, or the original running message's color while active. Changing settings does not recolor a running message; send again to replace it. Existing settings and active sessions from earlier versions retain the original amber color on upgrade. Color drafts survive theme changes and activity recreation.

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

`GET /api/stats` tests the connection. Stop sends `POST /api/notify/dismiss`, then `POST /api/switch` with `{"name":"Time"}`. Only after success does it clear the local active session and cancel its expiry refresh. Redirects, web login pages, non-2xx responses, and network failures are reported as errors.

## Build and test

Text-color tests cover RGB validation and normalization, the notification payload, preset/custom picker controls, invalid input, Cancel, actual preview pixels, draft recreation, saved color persistence, and amber defaults for settings and running sessions created before color support. Both widget tests verify that their requests contain the saved color and that an active full widget retains the running message's color after settings change.

Use JDK 17+, Android SDK platform 35, and the included Gradle 8.13 wrapper. Set `ANDROID_HOME` or create an ignored `local.properties` with `sdk.dir`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

Unit tests exercise address/duration validation, Unicode JSON, exact start/stop/clock API requests, and failure handling against a mock HTTP server. Android instrumentation tests cover defaults, theme recreation, settings persistence, foreground requests, failed-start/failed-stop state, elapsed-time expiry, and real AppWidgetHost start/stop cycles that complete on the home screen. The compact widget is tested at its 40dp fallback minimum alongside the full widget, including setup, busy controls, shared state, failed-stop retry, and stale Stop actions. The widget tests change the saved IP during the session and verify Stop still reaches the original matrix. Tests use a simulated AWTRIX endpoint; they do not validate a physical Ulanzi or Samsung device.

Release signing uses a private keystore kept outside the repository. Configure `STATUSUPDATER_KEYSTORE`, `STATUSUPDATER_STORE_PASSWORD`, and `STATUSUPDATER_KEY_PASSWORD`, with alias `statusupdater`, then run `./gradlew testReleaseUnitTest lintRelease assembleRelease`. Preserve this keystore for future in-place updates. Never commit it or its passwords. Release filenames include the version and build number.

GitHub Actions runs unit tests, lint, and a debug build on pushes/PRs. Its debug APK is a CI artifact; install the signed release APK for normal use. A debug APK uses different signing and cannot update an installed release.

## Author

Andrei Efremuahkin · [andrei.efr@gmail.com](mailto:andrei.efr@gmail.com)

[Repository](https://github.com/efremandrei/StatusUpdater)
