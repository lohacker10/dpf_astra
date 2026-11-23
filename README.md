# DPF Astra

<img src="android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="88" height="88" alt="DPF Astra app icon">

An independent Android app for monitoring the diesel particulate filter (DPF) on a supported Opel Astra K using a Bluetooth OBD-II adapter.

> [!WARNING]
> **Built with AI assistance.** Review the code and independently verify the readings and alerts on the vehicle before relying on the app. The ECU profile is experimental and still needs on-vehicle validation. Always follow the vehicle's dashboard warnings and owner's manual.

## 🚘 Who it's for

DPF Astra is intended for owners and testers of the **Opel Astra K 1.6 CDTI 110 PS (B16DT-family ECU)** who want to see when the filter is regenerating. Check your engine and ECU before using it. Other vehicles and engine variants are unverified. DPF Astra is an independent project and has no affiliation with Opel.

You need **Android 12 or newer** and a **Vgate iCar Pro** Bluetooth OBD-II adapter. Depending on the adapter variant, use Bluetooth Classic (SPP) or Bluetooth Low Energy (BLE). The app interface is currently in Italian.

## 📥 Install or update

1. Open [GitHub Releases](https://github.com/lohacker10/dpf_astra/releases/latest) and download **`dpf-astra.apk`** from **Assets**.
2. Open the downloaded APK. If Android asks, allow your browser or file manager to install unknown apps, then confirm installation.
3. Open **DPF Astra** from the app launcher.

The app checks GitHub when it starts. When an update is available, tap **Apri GitHub** in the prompt, download the new APK, and open it in Android's installer. You can update a previous GitHub release in place and keep its settings. If Android reports a conflicting signature from a locally built test version, export any logs you want to keep and uninstall that version before installing the GitHub APK. Uninstalling clears local app data.

## 🔌 Connect and monitor

1. With the vehicle parked, plug the adapter into the OBD-II port and power it on. Pair a Classic adapter in Android's Bluetooth settings.
2. Open **Impostazioni** → **Autorizzazioni Android** in DPF Astra. Grant Bluetooth and notification permissions.
3. Use **Seleziona il Vgate** and choose the transport supported by your adapter. For a BLE adapter that isn't already paired, tap **Cerca Vgate BLE non associato**. Accept the Android companion-device prompt.
4. Return to the dashboard and verify that the adapter connects and the readings match a trusted diagnostic tool. If monitoring is paused, tap **Avvia monitoraggio** in **Impostazioni**.

The dashboard shows **DPF saturation**, **distance since the last regeneration**, and **regeneration status**, including elapsed time when the start was observed. Missing readings are marked as unavailable. The app follows Android's light or dark theme.

The app can send regeneration notifications and monitor with the screen off. Background operation depends on Android permissions, adapter behavior, and the phone's battery settings. See the [background behavior notes](docs/background.md) and the [vehicle validation checklist](docs/vehicle-validation.md).

## 🔒 What the app reads and shares

The app sends a fixed set of read-only DPF queries. It cannot force regeneration, clear fault codes, or change ECU settings. Monitoring works offline. The startup update check requests public release information from GitHub without sending vehicle readings or diagnostic logs. Optional diagnostic logs stay on the device until you export them.

## 🛠️ Build from source

Use Flutter **3.41.4**, JDK **17**, and Android SDK **36**. Run these commands from the repository root.

```sh
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
(cd android && ./gradlew testDebugUnitTest lintDebug)
```

The debug APK is at `build/app/outputs/flutter-apk/app-debug.apk`. [GitHub Actions](.github/workflows/android.yml) runs the checks on pushes and pull requests. After a push to `main` passes the checks, Actions publishes a signed APK on GitHub Releases.
