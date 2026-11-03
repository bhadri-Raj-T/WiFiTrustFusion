# WiFiTrustFusion: Explainable Wi-Fi Monitoring and Connection Diagnostics

Developed as part of the course on **"Forensic Analytics"** at **Vellore Institute of Technology, Chennai**.

by **"Bhadri Raj T", "Ajay A", "Vyomika R"**

**Subject Code:** CSE4074  
**Semester:** Fall 2026-27  
**Professor:** Dr. S. Johanan Joysingh

<img src="android/app/src/main/res/drawable-nodpi/wtf_brand_mark.png" alt="WiFiTrustFusion logo" width="96">

## About the project

WiFiTrustFusion is an Android application that helps a user understand the Wi-Fi connection on their phone. It combines a live signal chart, descriptive analytics, available security information and small, user-triggered internet checks in one interface.

A Wi-Fi connection can have strong reception and still require a login or fail to reach the internet. The app explains these conditions separately and associates diagnostic results with the actual Wi-Fi connection. It is a course prototype with transparent rules; it does not classify a hotspot as malicious or certify its owner.

**Version:** `1.1-demo` (version code `3`)  
**Application ID:** `com.bhadrirajt.wifitrustfusion`  
**Minimum supported OS:** Android 8.1 / API 27

## User workflow

1. Open **My Wi-Fi** and allow the permissions needed to display network details.
2. Tap **Analyse connected Wi-Fi**, or choose another network and complete the connection through Android's settings.
3. Return to the app and view the actual connection, signal history and available configuration.
4. Tap **Check internet connection** to run Wi-Fi-bound DNS, TCP and HTTPS checks.
5. Read the explanations and compare observations after changing position or connection.

Android handles Wi-Fi passwords and connection approval. The app does not request or store the Wi-Fi password. Location access and the phone's Location setting may be needed for network identity; hidden identity does not by itself block an available Wi-Fi diagnostic route.

## Implemented features

| Area | What the application does |
|---|---|
| Connection interface | Analyses the phone's connected Wi-Fi; opens Android to select or change networks |
| Signal analytics | Polls RSSI about every two seconds in the foreground; keeps up to 60 valid readings; shows median, minimum, maximum, range and population standard deviation |
| Signal chart | Uses elapsed time and clears history when the connection changes or a long observation gap occurs |
| Configuration | Shows available current security type, or a labelled recent matching scan advertisement when supported |
| Internet diagnostics | Resolves `example.com`, attempts up to three TCP connections to port 443 and performs an HTTPS HEAD check through the Wi-Fi `Network` |
| Safety demo | Eight clearly labelled simulated scenarios, plus an opt-in foreground live-RSSI pause rule; Wi-Fi off is confirmed from the observed radio state |
| Explanations | Shows rules for weak or varying signal, open/legacy configuration, captive sign-in and incomplete checks |
| Optional reports | Keeps local report drafts; the user can choose to share text through Android's share sheet |
| Project Tools | Retains scan-session collection, timestamp freshness analysis, CSV records, a Room mirror, session history and heuristic evidence assessments |

The **Safety** button opens the new demonstration screen. Simulated look-alike/AP-change examples are not live attacker detections. The real pause rule requires explicit arming, three consecutive live polls below the chosen threshold, and the same connection. It disarms on a connection change, timing gap, backgrounding or starting a simulation. Android 8.1/9 can request Wi-Fi off; Android 10+ opens system controls for the user to finish. It affects this phone only and does not block mobile data, protect the router or guarantee personal-data safety.

The main contribution is integrating these observations with clear explanations and connection-aware result handling. No research novelty or measured attack-detection accuracy is claimed.

## Technology and source layout

The implementation is a native Kotlin Android app using Android framework Wi-Fi/connectivity APIs, AppCompat, Material components, Room and KSP. The supplied configuration uses Gradle 9.6.0, Android Gradle Plugin 9.4.1, SDK 36 and a JDK 25 daemon toolchain. Java source/target compatibility is 11.

| Path | Purpose |
|---|---|
| `android/app/src/main/` | Application source, manifest, layouts and branding |
| `android/app/src/test/` | Local JVM unit tests |
| `android/app/src/androidTest/` | Device/emulator test sources |
| `android/app/schemas/` | Room schema definition |
| `android/gradle/` | Wrapper and version/toolchain configuration |
| [PROJECT_EXPLANATION.md](PROJECT_EXPLANATION.md) | Presentation-aligned explanation for the third review |
| [docs/DEMO_GUIDE.md](docs/DEMO_GUIDE.md) | Demonstration sequence and warning conditions |
| [docs/GITHUB_RELEASE.md](docs/GITHUB_RELEASE.md) | Windows build and GitHub publication steps |
| [docs/RELEASE_NOTES_v1.1-demo.md](docs/RELEASE_NOTES_v1.1-demo.md) | Release description |

This submission contains the implemented Android application. It does not depend on an online backend, cloud reputation service, trained model or an accompanying dataset.

## Build and install

This update targets the existing repository [bhadri-Raj-T/WiFiTrustFusion](https://github.com/bhadri-Raj-T/WiFiTrustFusion), using a separate review worktree based on `origin/main`. `dev-core` and `data-acquisition` are not merged or pushed by the supplied workflow. See [the existing-repository instructions](docs/GITHUB_RELEASE.md).

Use the Windows laptop on which the project already builds. Prepare the update with `Prepare-MainReview.ps1`; it creates the separate `WiFiTrustFusion_MainReview` worktree. Keep the Gradle wrapper and toolchain configuration supplied with the source.

If you cloned this repository after the update, the source is already applied: run the build command directly from the repository root. The preparation helper is only needed when applying the downloaded update to the earlier checkout.

From Command Prompt in that folder:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File .\Build-ReviewApk.ps1 -OriginalProjectRoot "D:\Important\Forensics Analytics\WiFiTrustFusion"
```

The script can copy the existing, ignored `android/local.properties` and the original `LICENSE`, if present, from that local project. It builds the review source, runs local unit tests, verifies the test XML and exports only after success. It does not modify the original application's source or install the app automatically.

Outputs appear in `Final_APK`:

- `WiFiTrustFusion_Final.apk` — the locally signed debug APK for the classroom demo.
- `WiFiTrustFusion_Final.sha256` — SHA-256 of those exact APK bytes.
- `BUILD_SUMMARY.md` — actual local build and unit-test counts.
- `RELEASE_BUILD.json` and `SOURCE_MANIFEST.txt` — build/source association used by the publisher.

Install on a connected compatible phone:

```bat
"D:\Android\platform-tools\adb.exe" devices -l
"D:\Android\platform-tools\adb.exe" -s 5203cb41fa4d534d install -r ".\Final_APK\WiFiTrustFusion_Final.apk"
"D:\Android\platform-tools\adb.exe" -s 5203cb41fa4d534d shell am start -n com.bhadrirajt.wifitrustfusion/.wifi.explorer.WifiExplorerActivity
```

Replace the serial for another phone. Alternatively, transfer the APK to an Android 8.1+ phone and open it with that phone's installation permission. Compatibility still depends on the device and its policy. An existing installation must use a matching signing certificate for an in-place update; retain any important local records before resolving a mismatch.

## Validation status

The preceding working phone-fix revision was reported to pass **36 local JVM tests**, and live signal analytics were demonstrated on a Samsung SM-J710F. This is historical evidence, not a new test run of this package. The final branding and new Safety-demo source have not been compiled or executed during preparation of this submission. Ten new pure-rule unit tests are supplied for one-shot triggering, invalid readings, timing, disarming and connection changes; their presence is not a passing result.

`Build-ReviewApk.ps1` records a fresh result on the student's Windows laptop. The release helper requires that successful build record, checks the APK checksum and checks that the listed source files have not changed since the build. Device/emulator tests are included as source but are not run by that script. Manual phone verification is still required before presenting.

## Scope and limitations

- RSSI polls can repeat Android's cached radio values; they are not independent active scans.
- TCP connection time is not ICMP ping, download speed or a throughput measurement.
- A strong signal or WPA2/WPA3 label does not establish hotspot legitimacy.
- A failed check can result from a portal, filtering, timeout, DNS issue or the test endpoint; it is not proof of an attack.
- Reports are local drafts, not a deployed crowdsourced reputation service.
- In Project Tools, CSV is the reference development record and Room is a mirror that can be incomplete. The prototype does not establish a forensic chain of custody.
- Warning messages are displayed in the foreground; background alerts, calibrated risk scores and validated malicious-hotspot detection are outside this version.

## Third-review submission

Use `README.md` for the overview and `PROJECT_EXPLANATION.md` alongside the presentation. Use `Publish-Main.ps1` after the successful local build and manual phone check to commit/push this review to the existing repository's `main` and tag `v1.1-demo`. Create the release in GitHub's existing repository and attach the APK/checksum as **release assets**, rather than committing the APK to source control. The explicit update file list excludes new local evidence, collected data, keys, caches and build outputs. Existing repository history and previously committed documents remain intact; ignore rules do not remove already tracked files.

No paper or unpublished manuscript is included. If the project becomes part of a paper before publication, follow the professor's instruction to keep the repository local until the paper has been submitted for publication.
