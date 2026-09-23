# Agent Guidelines for Kids OS (`kids_os`)

## 🚨 MANDATORY RELEASE SYNCHRONIZATION RULE

**Every time any code changes, bug fixes, or new features are added to this project, you MUST publish and update the GitHub Release page:**

1. **Bump Version in `app/build.gradle`**:
   - Increment `versionCode` (e.g., `1` -> `2` -> `3`).
   - Increment `versionName` (e.g., `"1.0.0"` -> `"1.0.1"` -> `"1.0.2"`).

2. **Compile the APK**:
   - Build using Gradle:
     ```powershell
     cmd /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot&& gradlew.bat assembleDebug"
     ```

3. **Commit & Push Code to GitHub**:
   - Repository: `https://github.com/mirzaarsyad74-cmyk/kids_os`
   - Push to `main` branch:
     ```powershell
     git add .
     git commit -m "Describe updates here"
     git push origin main
     ```

4. **Publish Release on GitHub**:
   - Create a new GitHub Release matching the version tag (e.g. `v1.0.1`):
     - API: `POST https://api.github.com/repos/mirzaarsyad74-cmyk/kids_os/releases`
     - Provide clear changelog highlights in the release body.
   - Upload the newly built APK file (`KidsOS_vX.X.X.apk`) as the release asset so the in-app OTA Updater can detect and download it.

5. **Deploy to Device**:
   - Install to the connected tablet via ADB:
     ```powershell
     adb -s 0123456789ABCDEF install -r app/build/outputs/apk/debug/app-debug.apk
     ```

---

## 🌸 Design & System Guidelines

- **Aesthetics**: Maintain the My Melody soft pastel-pink (#FFF5F7, #FCE7F3, #DB2777, #831843) theme across all activities, buttons, and HUDs.
- **OTA Updater**: The in-app updater (`MelodyUpdaterActivity`) checks `https://api.github.com/repos/mirzaarsyad74-cmyk/kids_os/releases/latest`. Keeping releases updated is critical for OTA functionality.
- **Constraints**: User has requested strictly NO screenshots or UI testing unless explicitly asked.

---

## 🧒 DEDICATED OS FOR KIDS (NO RAW ANDROID EXPOSURE)

**Kids OS is a dedicated, self-contained operating system environment for children. Kids must NEVER be thrown into or exposed to stock Android OS:**

1. **NO Stock Android Settings or Unstyled System Dialogs**:
   - Under NO circumstances should children ever be redirected to stock Android Settings (`Settings.ACTION_*`, `ACTION_LOCATION_SOURCE_SETTINGS`, `ACTION_WIFI_SETTINGS`, etc.).
   - All quick controls (Wi-Fi, Bluetooth, Location/GPS, Screen Rotation, Brightness, Sound, Flashlight) MUST be executed programmatically in the background using granted system permissions (`WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`, `CHANGE_WIFI_STATE`, `ACCESS_FINE_LOCATION`) or with custom My Melody UI dialogs.
   - Advanced settings (e.g. scanning and connecting to new Wi-Fi access points) are strictly reserved for parents and MUST reside inside `ParentZoneActivity` behind the Parent PIN (`1234`).

2. **Permanent Status Bar & Notification Shade Lockdown**:
   - The stock Android notification bar pull-down must be completely and permanently blocked from being pulled down anywhere on the device.
   - Enforce this with a top touch shield overlay (`TYPE_ACCESSIBILITY_OVERLAY`) in `MelodyGlobalService` that intercepts downward gestures before `PhoneWindowManager` or `com.android.systemui` can receive them.
   - Always call `StatusBarManager.collapsePanels()` on any system UI interaction to instantly dismiss any stock panel.
   - Downward swipes or taps on the top edge MUST open our custom, pastel-pink My Melody Control Center / Notification Center.

3. **System Auto-Brightness Integration**:
   - Ambient light sensor auto-brightness must be enabled by default (`true`).
   - `MainActivity` and `MelodyGlobalService` must harmonize system brightness without window attribute overrides blocking ambient light responsiveness.

