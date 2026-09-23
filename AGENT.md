# 🎀 AGENT.md - Kids Tablet Launcher (Melody OS)

This document contains essential context, architectural rules, environment configuration, and task guidelines for any AI agent working on this repository.

---

## 🌸 1. Project Overview & Target Audience
* **Project Name**: Kids Launcher ("Melody OS")
* **Target Audience**: 9–10 year old girls.
* **Theme & Aesthetic**: Cute, pastel pink, soft cream, pastel purple, and white inspired by Sanrio's My Melody. Rich tactile animations, rounded bubbly cards, sweet encouragement badges, and playful emojis.
* **Target Device**: Budget Android Tablet (10-inch landscape, 4GB RAM).
* **Package Name**: `com.kids.launcher`
* **Default PIN**: `1234` (configurable in Parent Zone).

---

## 🛠️ 2. Core Architecture & Features

### 🏠 Launcher Core (`MainActivity`)
* **Grid Layout**: 4-column landscape app grid with category tabs (`All Fun`, `Games`, `Art`, `Learn`).
* **Top Bar Auto RAM Booster**: Visible `"🚀 Boost ⚡"` button on top bar that triggers `DeviceBooster`, plays bounce animation, and displays a toast with freed RAM in MB. Also auto-cleans memory before app launches and in `onResume`.
* **Stock App Replacement**: Suppresses duplicate AOSP stock apps (`com.android.calculator2`, `com.android.deskclock`, `com.android.gallery3d`, etc.) in favor of the built-in cute Melody apps.
* **YouTube Logo Rules**:
  * **YouTube Kids** (`com.google.android.apps.youtube.kids`): Retains its **own** official colorful logo.
  * **Modded YouTube** (`app.morphe.android.youtube`): Explicitly mapped to the official red play button icon (`ic_youtube_official`).

### 📱 Built-In Apps
1. **Camera 📸 (`MelodyCameraActivity`)**:
   * Continuous torch/flashlight toggle (stays ON for back camera until exit).
   * Cute filter overlays (Melody Bows, Sparkles, Warm Pastel, B&W, No Filter).
   * Front/Back camera switching with mirror correction.
   * Auto-saves to `/sdcard/Pictures/MelodyCamera`.
2. **Music 🎶 (`MelodyMusicActivity`)**:
   * Scans all external and internal storage for audio (`mp3`, `m4a`, `aac`, `wav`, `ogg`, `flac`).
   * Cute waveform visualizer, pastel playback controls, volume slider.
   * Built-in 8-note cute synthesizer piano.
3. **Calculator 🔢 (`MelodyCalculatorActivity`)**:
   * Large pastel pink tactile buttons with sound effects.
   * Expressions display, backspace, percentages, and cute cheer badges (*"Keep Learning! 🌸"*).
4. **Clock & Timer ⏰ (`MelodyClockActivity`)**:
   * Digital clock with day-part greetings (morning, afternoon, evening, bedtime).
   * Study & play timer with quick presets (1m, 3m, 5m, 10m, 15m, 25m) and chimes.
   * Stopwatch with lap times.
5. **Gallery 🖼️ (`MelodyGalleryActivity`)**:
   * Thumbnail grid displaying photos from Melody Camera and device storage.
   * Full-screen preview modal with zoom and delete confirmation.
   * Quick shortcut to launch the camera.
6. **Battery 🔋 (`MelodyBatteryActivity` - Battery Guru style)**:
   * Real-time battery health, temperature in °C, voltage in mV, technology (Li-ion), charging speed, and estimated battery time remaining.
   * 1-Tap "⚡ Optimize Battery & Boost" action.
   * Running background apps drain list with individual "Sleep 💤" buttons.
   * Directly accessible from home app grid, top-bar battery capsule, or floating battery widget.

### 🛡️ Parent Zone (`ParentZoneActivity`)
* PIN-protected dashboard (`1234`).
* Per-app category customization (re-assign any app to Games, Art, Learn, etc.).
* Daily playtime limit (15m, 30m, 60m, Unlimited) with automated lockout screen.
* Eye protection break timer (reminds child to rest eyes every 20 minutes).
* Curfew / Bedtime mode (locks device between 8:00 PM and 7:00 AM).
* **Auto Battery Saver Toggle**: Automatically switches on power saving mode when battery falls below 20%.
* **Full Battery Sound Notice Toggle**: Chimes an audio alert tone (`ToneGenerator`) and notifies when battery is 100% full.

### 🔋 Global Floating Battery Widget (`MelodyGlobalService`)
* Runs via Accessibility Overlay (`TYPE_ACCESSIBILITY_OVERLAY`).
* Floating and draggable across any app or screen.
* **Percentage Drawn Inside Battery**: Percentage number is rendered directly inside the battery icon (no ribbon).
* **Charger Speed Detection & Animations**:
  * **Fast Charger** (AC Wall Adapter): Rapid cyan-emerald wave pulse (450ms) with `⚡ Fast` badge.
  * **Slow Charger** (USB port): Gentle pink-gold pulse (1200ms) with `⚡ Slow` badge.
  * Tactile bounce animation on plug / unplug events.
* Tapping opens `MelodyBatteryActivity`.

### 🌸 Melody Touch Assistant (`MelodyGlobalService`)
* **Apple AssistiveTouch-style floating orb** with a cute translucent pink frosted ring and flower icon (🌸).
* Draggable anywhere on screen with edge auto-snapping (left or right edge) and idle translucency (75% alpha).
* Tapping the orb opens the **Melody Touch Floating Menu** with 8 quick actions:
  1. ◀ **Back**: Calls `performGlobalAction(GLOBAL_ACTION_BACK)`
  2. 🏠 **Home**: Calls `performGlobalAction(GLOBAL_ACTION_HOME)`
  3. 📂 **Recents**: Calls `performGlobalAction(GLOBAL_ACTION_RECENTS)`
  4. 📸 **Screenshot / Capture**: Calls `performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)`
  5. 🔊 **Volume Up**: Increases media volume with system UI
  6. 🔉 **Volume Down**: Decreases media volume with system UI
  7. 📶 **WiFi Toggle**: Switches WiFi ON/OFF with live state badge
  8. 🚀 **Boost**: Triggers `DeviceBooster.boostAndGetFreedMb` and displays freed RAM toast

---

## ⚠️ 3. Critical Rules & Design Constraints
1. **STOCK NAVIGATION BAR MUST ALWAYS BE VISIBLE**:
   * NEVER use `SYSTEM_UI_FLAG_HIDE_NAVIGATION` or immersive mode that removes the standard Android navigation bar (`◀ ⚪ ◼`).
   * Activities must set `View.SYSTEM_UI_FLAG_LAYOUT_STABLE`.
2. **APP LABELS ON LAUNCHER MUST NOT CONTAIN "MELODY"**:
   * Camera: `"Camera 📸"` (NOT "Melody Camera").
   * Music: `"Music 🎶"` (NOT "Melody Music").
   * Calculator: `"Calculator 🔢"`, Clock: `"Clock ⏰"`, Gallery: `"Gallery 🖼️"`, Battery: `"Battery 🔋"`.
3. **YOUTUBE LOGO MAPPING**:
   * YouTube Kids retains its own original colorful logo (never override).
   * Modded YouTube (`app.morphe.android.youtube`) uses `R.drawable.ic_youtube_official`.
4. **FLOATING BATTERY REQUIREMENTS**:
   * No ribbon (`tv_floating_bow` removed).
   * Battery percentage drawn inside the battery icon.
   * Fast charger (`⚡ Fast`) vs Slow charger (`⚡ Slow`) animations.
5. **PERFORMANCE FIRST (4GB RAM OPTIMIZATION)**:
   * Always downsample images with `inSampleSize` when loading into memory.
   * Release Camera, MediaPlayer, and AudioTrack instances in `onPause()` and `onDestroy()`.
   * Heavy disk scans must run on background threads (`AsyncTask` or `Executors`).
6. **OS FOR KIDS (ZERO RAW ANDROID EXPOSURE)**:
   * Kids OS is a dedicated, self-contained operating system environment for children.
   * Kids must NEVER be thrown into or exposed to stock Android Settings (`Settings.ACTION_*`) or unstyled system dialogs.
   - All quick controls (Wi-Fi, Bluetooth, Location/GPS, Screen Rotation, Brightness, Sound, Flashlight) MUST be executed programmatically in the background using granted system permissions (`WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`, `CHANGE_WIFI_STATE`, `ACCESS_FINE_LOCATION`) or with custom My Melody UI dialogs.
   - Stock Android Notification Bar and Quick Settings MUST be permanently disabled and physically touch-shielded via `TYPE_ACCESSIBILITY_OVERLAY` top touch barrier in `MelodyGlobalService` and `collapsePanels()`. Pulling down from top edge opens our Melody Control Center.
   - Auto-Brightness must be enabled by default and dynamically adjust system brightness based on hardware ambient light sensor.


---

## 💻 4. Development & Build Environment

* **Java Home**: `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`
* **Android SDK**: `C:\Users\admin\AppData\Local\Android\Sdk`
* **ADB Path**: `C:\Users\admin\AppData\Local\Android\Sdk\platform-tools\adb.exe`
* **Workspace Directory**: `C:\Users\admin\.gemini\antigravity-ide\scratch\kids-launcher`

### Build Command
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"; $env:ANDROID_HOME = "C:\Users\admin\AppData\Local\Android\Sdk"; .\gradlew.bat assembleDebug
```

### Install Command
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "C:\Users\admin\.gemini\antigravity-ide\scratch\kids-launcher\app\build\outputs\apk\debug\app-debug.apk"
```

---

## 📋 5. Agent Workflow Protocol
Before executing any task:
1. **Analyze Request**: Understand what the user wants in the context of a 9–10 year old girl's tablet experience.
2. **Formulate Task List**: Always write down a clear step-by-step task list before modifying code.
3. **Execute & Test**:
   - Make clean, focused changes.
   - Run Gradle build (`assembleDebug`).
   - Install APK onto connected tablet via ADB.
   - Verify functionality on device. (Note: Do not attach screenshots to chat responses when requested by user; maintain documentation in `AGENT.md`).
4. **Update Status**: Keep this `AGENT.md` task roadmap updated as items are completed.

---

## 🚀 6. Roadmap & Task List

### Completed Tasks ✅
- [x] Create kids home launcher with cute My Melody aesthetic.
- [x] Rename category `Barbie & Games` to `Games`.
- [x] Parent Zone: Add ability to choose app category.
- [x] Built-in Camera app with continuous torch, stickers, and front/back toggle.
- [x] Built-in Music Player supporting MP3, M4A, AAC, and WAV from all storage.
- [x] Replace custom navigation bar with stock Android navigation bar (`◀ ⚪ ◼`).
- [x] Rename Camera and Music to remove the word "Melody" from launcher labels.
- [x] Built-in Melody-themed Calculator app (`Calculator 🔢`).
- [x] Built-in Melody-themed Clock & Timer app (`Clock ⏰`).
- [x] Built-in Melody-themed Gallery app with full-screen viewer and delete (`Gallery 🖼️`).
- [x] YouTube Kids uses its own original colorful logo.
- [x] Modded YouTube uses official red YouTube play logo (`ic_youtube_official`).
- [x] Launcher Top Bar: Auto RAM Booster button (`🚀 Boost ⚡`) with bounce animation and freed MB toast.
- [x] Floating Battery Widget: Removed ribbon, drawn percentage inside battery icon.
- [x] Floating Battery Widget: Charging slow/fast animations and `⚡ Fast` / `⚡ Slow` badges.
- [x] Battery Saver: Automatically enables battery saver when battery falls below 20%.
- [x] Parent Zone & Battery App: Added full battery sound notice (audio chime at 100%).
- [x] Battery Guru-style App (`Battery 🔋`): Health, temp, voltage, tech, drain list with "Sleep 💤", and 1-tap boost.
- [x] Melody Touch Assistant (Apple AssistiveTouch style): Draggable floating orb (🌸) with edge snap.
- [x] Melody Touch Menu: 8 Actions (Back, Home, Recents, Screenshot, Vol Up, Vol Down, WiFi Toggle, Boost).

### Upcoming Tasks & Polish 📌

#### 🛡️ Safety & Parental Controls
- [ ] **App Lock / Block List**:
  - Let parents individually lock or hide specific installed apps (e.g., Settings, browser, Play Store).
  - Locked apps show a cute "🔒 Ask a grown-up!" screen instead of launching.
- [ ] **Safe Browsing / URL Whitelist**:
  - If a browser is allowed, restrict navigation to a parent-approved whitelist of URLs only.
  - Block access to address bar; only show bookmarked safe sites.
- [ ] **Content Filtering**:
  - Scan gallery images and block inappropriate content from appearing in the Gallery app.
- [ ] **Usage Stats / Screen Time Report**:
  - Track daily per-app usage time and show parents a visual weekly report in Parent Zone (bar charts / pie charts).
  - "Top 3 apps this week", total screen time today vs yesterday, etc.
- [ ] **Panic / SOS Button**:
  - Add a discreet "📞 Call Mama / Papa" emergency button on the home screen that dials a preset emergency contact (configurable in Parent Zone).
- [ ] **Bedtime / Curfew Custom Schedule**:
  - Add custom start/end time pickers in Parent Zone for curfew hours (not just hardcoded 8PM–7AM).
  - Different schedules for weekdays vs weekends.
- [ ] **App Install Approval**:
  - Intercept new app installs and require parent PIN approval before the app becomes visible in the launcher.
- [ ] **Location Sharing (optional)**:
  - Show device location to parents via a simple map view in Parent Zone (useful if the tablet is taken outside).

#### 🎨 UI / UX Polish
- [ ] **Welcome / Onboarding Flow**:
  - First-launch wizard: set child's name, pick avatar, set parent PIN, choose theme color, set daily time limit.
  - Personalized greeting on home screen: "Hi, [Name]! 🌸".
- [ ] **Child Avatar / Profile**:
  - Replace the generic Melody icon in the top bar with the child's chosen avatar or photo.
  - Let child pick from cute preset avatars (bunny, kitty, bear, unicorn, etc.).
- [ ] **Theme / Color Picker**:
  - Let the child choose between 3–4 pastel color themes (Pink, Lavender, Mint, Peach) in a fun settings screen.
- [ ] **Clock & Timer UI Polish**:
  - Add top margin to clock tabs so they never collide with system status bar or floating battery.
- [ ] **Smooth App Launch Transition**:
  - Add a cute scale-up + fade transition animation when launching apps from the home grid.
- [ ] **Empty State Screens**:
  - Show cute illustrations with helpful messages when Gallery has no photos, Music has no songs, etc.
- [ ] **Wallpaper Picker**:
  - Let child choose from a set of cute built-in wallpapers (Melody, flowers, stars, rainbow, etc.) from a simple picker.

#### 📱 App Enhancements
- [ ] **Gallery Gestures**:
  - Add swipe left/right between photos in full-screen preview.
- [ ] **Camera Fun Stickers**:
  - Add sticker selection palette (cute Melody bows, hearts, stars) movable on photo preview before capture.
- [ ] **Music Player Playlist / Favorites**:
  - Add a "⭐ Favorites" playlist tab for favorite songs.
- [ ] **Notes / Diary App 📝**:
  - Simple cute notepad for the child to write daily diary entries, drawings, or to-do lists.
  - Date-stamped entries with emoji sticker support.
- [ ] **Drawing / Doodle App 🎨**:
  - Finger painting canvas with brush size, color palette, undo, and save to Gallery.
  - Could replace or complement the existing third-party "Draw" app.
- [ ] **Alarm / Wake-Up in Clock App**:
  - Let the child set a cute morning alarm with a gentle melody chime.

#### 💚 Health & Wellbeing
- [ ] **Eye Protection Break Timer (Enhanced)**:
  - Full-screen overlay every 20 minutes: "🌸 Time to rest your eyes! Look at something far away for 20 seconds 👀".
  - Cute countdown animation before resuming.
- [ ] **Posture Reminder**:
  - Periodic toast or overlay: "Sit up straight, sweetie! 🧸" every 30–45 minutes.
- [ ] **Blue Light Filter / Night Mode**:
  - Warm tint overlay in the evening hours to reduce eye strain (auto-enable after 7PM).
- [ ] **Volume Limiter**:
  - Cap max media volume at 70% in Parent Zone to protect hearing.
  - Show a cute warning if child tries to go above the limit.

#### ⭐ Fun & Engagement
- [ ] **Daily Reward / Streak System**:
  - Track daily usage and award cute badges/stickers: "🌟 7-Day Streak!", "📖 Bookworm!", "🎨 Artist!".
  - Show badge collection in a fun trophy shelf screen.
- [ ] **Study Timer Rewards**:
  - After completing a study timer session, award a virtual sticker or "Good Job! 🌸" celebration animation.
- [ ] **Cute Loading Screens / Splash Tips**:
  - Show fun facts, motivational quotes, or cute tips while apps load: "Did you know? 🦋 Butterflies taste with their feet!"
- [ ] **Home Screen Widgets**:
  - Mini weather widget, mini clock widget, or "Today's Goal" widget on the home screen.
- [ ] **Voice Greeting**:
  - Play a short TTS greeting when the launcher starts: "Good morning, sweetie!" / "Welcome back! 🌸"
