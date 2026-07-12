<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# JIW Tracker (Japanese Interval Walking Tracker)

An advanced, premium scientific fitness tracker designed to help users execute 信州大学 (Shinshu University) style Interval Walking. The app manages and schedules interval profiles, calculates precise metabolic calorie burns based on walking speeds (easy stroll vs. brisk walk) and body metrics, and syncs seamlessly with Android Health Connect.

---

## 🚀 Release v1.0.2 (Feature Release)

This release introduces a fully-featured onboarding flow with Jetpack DataStore persistence, segmented workout cycle tracking, and light mode visual enhancements.

### ✨ New Features & Key Additions
1. **Interactive Onboarding Wizard:**
   - Designed a multi-step introduction flow (`OnboardingScreen`) to guide users through feature explanations (Steps, Metabolic Insights, Calorie burning equations).
   - Collects initial user metrics (body weight calibration, weekly walk goals) to customize calculation models.
   - Integrated live permission checks and requests for physical Activity Recognition, System Notifications, and Android Health Connect permissions directly in-app.
2. **DataStore Persistence Layer:**
   - Created `OnboardingPrefs` utilizing Jetpack DataStore for thread-safe, reactive key-value storage.
   - Persists onboarding status, user weight, weekly walk goal, and session seeding configuration across application restarts.
3. **Advanced Session HUD Segmenting:**
   - Replaced continuous progress bars with a discrete Segmented Progress Bar during active interval walks.
   - Animating completed cycles (high-intensity fast walk segments vs easy strolls) and features a pulse animation on the active segment.
4. **Light Mode & Accessibility Polish:**
   - Extended the CarbonBlack/MinimalLight theme to HUD metrics and action buttons (Play, Pause, Stop, Skip).
   - Standardized icons to use auto-mirrored variants where appropriate.

---

## 🚀 Release v1.0.1 (Minor Release)

This release implements targeted UX bug fixes to improve navigation flow, reduce clinical jargon, and add weight metric configurations.

### 🛠️ Bug Fixes & UX Enhancements
1. **Health Connect Sync Consolidation:** Removed duplicate sync cards and stats from the Settings screen. Users now see a consolidated `"Connected"` status indicator and a `"Manage Sync"` link redirecting them directly to the **Dashboard** stats screen where sync details reside.
2. **Safe History Deletion (Danger Zone protection):** 
   - Swapped the prominent, full-width red filled delete button in settings with a smaller, outlined button.
   - Added a confirmation `AlertDialog` overlay to prevent accidental profile or session database deletion.
3. **Approachable Wording (Clinical Copy Removal):** Rewrote clinical, stiff, and enterprise-sounding copy into consumer-friendly alternatives:
   - `SYSTEM PREFERENCES` → `Settings`
   - `ATHLETE MOTIVATION ALERTS` → `Reminders`
   - `CONNECTION PERFORMANCE` → `Sync Status`
   - `INTERACTIVE SONIC CUES` → `Sounds & Voice`
   - `ATHLETE DASHBOARD` → `Your Progress`
   - `CUSTOM INTERVAL DEFINITION` → `Your Interval Settings`
   - `Weight calibration` → `Weight settings`
4. **Weight Unit Toggle Support:** Added an interactive `kg` / `lb` segmented unit toggle inside the Settings weight card, enabling display weight conversion and unit-based edit prompts. The preference is persisted across restarts, while internal databases preserve the canonical metrics in kilograms.

---

## 📦 Assets
- Built debug APK is available at [assets/app-v1.0.1.apk](assets/app-v1.0.1.apk).

---

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
