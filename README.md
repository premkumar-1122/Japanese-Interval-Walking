# JIW Tracker

**Precision fitness tracking for the Shinshu University interval-walking method.**

![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)
![Latest Release](https://img.shields.io/github/v/release/premkumar-1122/Japanese-Interval-Walking?display_name=tag)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26-FF69B4?logo=android&logoColor=white)

## What is this?

Researchers at [Shinshu University (信州大学)](https://www.shinshu-u.ac.jp/) demonstrated that alternating between a brisk walk and an easy stroll — in precisely timed intervals — produces measurable metabolic benefits beyond steady-pace walking. JIW Tracker puts that protocol into your pocket: you define the interval profile, and the app guides you through each segment in real time with live calorie tracking based on your body metrics and walking speed. Every session syncs to Android Health Connect so your walking data lives alongside the rest of your health ecosystem.

## Features

### Tracking & Science

- **Interval profile scheduling** — define fast-walk and easy-stroll durations that match the Shinshu protocol, then let the app guide you segment by segment
- **Metabolic calorie engine** — calories are calculated from actual walking speed and your personal body metrics, not generic per-minute estimates
- **Segmented session HUD** — a live, animated heads-up display progresses through each interval so you always know which phase you're in and what's next

### Health Connect

- **One-tap sync to Android Health Connect** — walking sessions, calories burned, and step data flow directly into your unified health record alongside apps like Google Fit and Samsung Health

### UX & Personalization

- **Interactive onboarding wizard** — collects your body weight and weekly walking goals on first launch so calorie math is accurate from session one
- **kg / lb unit toggle** — works in whichever system you prefer, with instant conversion
- **Dark & light themes** — choose between *CarbonBlack* (dark) and *MinimalLight* (light) to match your environment
- **Consumer-friendly copy** — every label and prompt is written for the person using it, not the engineer who built it

## Screenshots

| Choose Your workout | Live Session | Dashboard |
|:---:|:---:|:---:|
| ![Train](screenshots/train.png) | ![Session HUD](screenshots/fast_walk.png) | ![Dashboard](screenshots/dashboard.png) |

<details>
<summary>More screenshots</summary>

| Settings | Theme Toggle | Health Connect |
|:---:|:---:|:---:|
| ![Settings](screenshots/settings.png) | ![Themes](screenshots/themeswitching.png) | ![Health Connect](screenshots/healthconnect.png) |

</details>

## Getting Started

### Prerequisites

| Requirement | Details |
|---|---|
| **Android Studio** | Latest stable recommended |
| **JDK** | 17 (bundled with recent Android Studio) |
| **Min SDK** | 26 (Android 8.0 Oreo) |
| **Target SDK** | 36 |

### Build

```bash
# Clone the repository
git clone https://github.com/premkumar-1122/Japanese-Interval-Walking.git
cd Japanese-Interval-Walking

# Build the standard flavor (includes Google Play Services Location)
./gradlew assembleStandardDebug

# Or build the F-Droid–compatible flavor (no proprietary dependencies)
./gradlew assembleFdroidDebug
```

The signed release build and distribution bundle are produced automatically by CI — see [Releases](#releases) below.

## Releases

JIW Tracker uses a **tag-triggered GitHub Actions pipeline** (`.github/workflows/release.yml`). Pushing a `vX.Y.Z` tag to `main` automatically:

1. Checks out the code and sets up JDK 17
2. Decodes the signing keystore from a sealed GitHub secret
3. Builds a **signed APK** and **AAB** via Gradle
4. Generates release notes from conventional commits
5. Creates a GitHub Release with both artifacts attached

The project has shipped 7 releases, the latest being **v2.1.2**.

| Resource | What it covers |
|---|---|
| [`docs/CI-CD.md`](docs/CI-CD.md) | Full pipeline architecture and required secrets |
| [`SKILL.md`](SKILL.md) | Step-by-step release runbook for maintainers |
| [`CHANGELOG.md`](CHANGELOG.md) | Detailed per-version changelog |

## Contributing

JIW Tracker started as a personal project and has grown into something I'm proud to share. Whether you want to fix a typo, add a new feature, improve localization, or tighten the calorie model — your contribution is welcome.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-idea`)
3. Commit with [conventional commit](https://www.conventionalcommits.org/) messages
4. Open a Pull Request

No CLA, no bureaucracy — just good code and thoughtful design.

## License

This project is released under the [GNU General Public License v3.0](LICENSE).

