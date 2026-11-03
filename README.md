# WiFiTrustFusion: Explainable Wi-Fi Monitoring and Connection Diagnostics

Developed as part of the course on **"Forensic Analytics"** at **Vellore Institute of Technology, Chennai**.

by **"Bhadri Raj T", "Ajay A", "Vyomika R"**

**Subject Code:** CSE4074  
**Semester:** Fall 2026-27  
**Professor:** Dr. S. Johanan Joysingh

<img src="android/app/src/main/res/drawable-nodpi/wtf_brand_mark.png" alt="WiFiTrustFusion logo" width="96">

## About the project

WiFiTrustFusion helps a phone user connect through Android's Wi-Fi controls, analyse the actual connected Wi-Fi and inspect understandable results. Its separate Demo tab explains controlled failures without running attacks or changing the real connection.

**Version:** `1.2-demo`, code `4`. Minimum Android: **8.1 / API 27**. Application ID: `com.bhadrirajt.wifitrustfusion`.

## Final user workflow

1. **My Wi-Fi:** use the connected network or select a network through Android. Android handles the password and connection approval.
2. **Analysis:** run Wi-Fi-bound DNS, TCP and HTTPS checks; inspect progress, cancel if needed and open the formatted checklist.
3. **Demo:** select a teaching case or run all 20. Review labelled simulated results, an illustrative signal chart and the virtual automatic response.
4. **Reports:** retain optional local drafts and manually choose whether to share them.

The four navigation tabs remain available while the page scrolls. Live and simulated reports share a readable layout but always retain their source labels.

## Implemented features

| Feature | Behaviour |
|---|---|
| Connected-Wi-Fi analysis | Uses the actual Android Wi-Fi Network; known connection changes invalidate prior probe results |
| Signal analytics | Foreground polls about every 2 seconds; up to 60 valid readings with median, range and population SD |
| Live checklist | Twelve items cover route, identity visibility, signal/window, security, portal, Android validation, DNS, TCP, HTTPS, HTTP status and continuity |
| Active check progress | DNS → TCP → HTTPS, with a cancellation control, timeouts and retry cooldown |
| Readable results | Labelled status badges, expandable explanations and a copy action that omits Wi-Fi identifiers |
| Demo tab | Twenty invented baseline/failure/edge-case scenarios, run-one/run-all controls, stop/reset, progress, chart and run summary |
| Virtual response | Eligible failed fixtures can pause their virtual session; no actual phone settings are changed |
| Opt-in real response | Explicitly armed foreground Open/WEP configuration or low-signal demonstration rule, using three matching polls |
| Project Tools | Earlier scan-session tools, reference CSV records, Room mirror, history and heuristic evidence assessments |

**Twelve checklist items are not twelve independent network probes.** The active operations remain DNS resolution, up to three TCP connects to port 443 and HTTPS HEAD to `example.com`. HTTP status is read from that same HTTPS response. Other items are passive observations or result-integrity checks.

## Real pause behaviour

Open **Demo → Open live Wi-Fi pause controls**. Nothing is armed by default. Choose and explicitly arm one profile:

- **Open/WEP configuration:** requires Android current connection security or an advertisement matching the connected AP no older than 30 seconds. Unknown/stale observations do not trigger. Three matching foreground polls request a one-shot pause.
- **Low signal:** a user-selected RSSI demonstration threshold must be crossed for three consecutive valid polls. Signal is not evidence of an attacker.

Both profiles disarm on leaving the screen, connection changes or a long timing gap. Android 8.1/9 can accept a Wi-Fi shutdown request; the UI confirms only after observing the radio off. Android 10+ opens Wi-Fi controls for the user to finish. The action affects this phone only, does not block mobile data and does not secure the router or guarantee personal-data protection.

The simulator cannot arm or trigger these real controls. Name similarity, AP changes, failed probes and signal variation are not validated hacker detections. This prototype cannot disconnect automatically for every possible vulnerability.

## Build and install on Windows

Use the existing repository: [bhadri-Raj-T/WiFiTrustFusion](https://github.com/bhadri-Raj-T/WiFiTrustFusion). The update creates a separate `WiFiTrustFusion_FinalReview` worktree from remote main; development/data branches remain separate.

See [docs/GITHUB_RELEASE.md](docs/GITHUB_RELEASE.md) for exact extraction/preparation commands. After preparation, or directly after cloning the updated source, run from the repository root:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File .\Build-ReviewApk.ps1 -OriginalProjectRoot "D:\Important\Forensics Analytics\WiFiTrustFusion_MainReview"
```

The existing SDK 36 setup, JDK 25 daemon toolchain and supplied Gradle 9.6.0 wrapper are retained. Android Gradle Plugin is 9.4.1. The app uses Kotlin, AppCompat/Material and Room/KSP. The script runs clean debug assembly and local JVM tests, verifies actual XML results and exports only after success.

Outputs in `Final_APK`: `WiFiTrustFusion_Final.apk`, its `.sha256`, `BUILD_SUMMARY.md`, `RELEASE_BUILD.json` and `SOURCE_MANIFEST.txt`. This is a locally debug-signed classroom APK. Keep the same local signing key for an in-place update.

## Review documents and source

| File or folder | Purpose |
|---|---|
| [PROJECT_EXPLANATION.md](PROJECT_EXPLANATION.md) | Eight suggested presentation sections |
| [docs/FINAL_DEMO_FLOW.md](docs/FINAL_DEMO_FLOW.md) | Final workflow, 20-case matrix and presentation sequence |
| [docs/DEMO_GUIDE.md](docs/DEMO_GUIDE.md) | Phone rehearsal and real-response checks |
| [docs/GITHUB_RELEASE.md](docs/GITHUB_RELEASE.md) | Build, push main and create the new release |
| [docs/RELEASE_NOTES_v1.2-demo.md](docs/RELEASE_NOTES_v1.2-demo.md) | Current release notes |
| `android/app/src/main/` | Native application implementation/resources |
| `android/app/src/test/` | JVM test sources |
| `android/app/src/androidTest/` | Device/emulator test sources |
| `android/app/schemas/` | Room schema |

## Validation status

The student's supplied Windows log confirms that **1.1-demo passed 46 JVM tests with zero skipped**, installed successfully on the connected phone, and was pushed to main at `986c741a12baadd50eb1c8a237cd2b05bbda097b` with tag `v1.1-demo`.

This new **1.2-demo** revision was prepared without compiling or executing the app. It adds 23 focused test methods for fixture source labels, dependent-check skipping, discarded results, report interpretation and configuration-response guards. These new tests have not been run during preparation. Run the local build script to obtain the actual result, then perform the manual phone checklist. Instrumented test sources are included but not executed by that script.

## Evidence and scope

RSSI may repeat cached values, TCP time is not download speed, and successful HTTPS authenticates the endpoint under the phone's TLS policy rather than the hotspot operator. Modern Wi-Fi configuration does not certify an owner. Unknown and untested items are never labelled as passes.

Simulated data does not enter collector CSVs, the Room mirror or live probe results. Optional reports are local drafts; no cloud reputation service or backend is deployed. CSV is the reference development record and the Room mirror can be incomplete; the prototype does not establish a forensic chain of custody. No trained model, calibrated attack score, packet inspection, background protection service or validated malicious-hotspot detection is claimed.

Publish this revision to the existing main using `Publish-Main.ps1` after the local build and phone checks. Create a **new `v1.2-demo` prerelease** and attach the new APK/checksum/build records. Keep the existing `v1.1-demo` release/tag intact. No GitHub plugin or CLI is needed; Git pushes code/tags and the website accepts release assets.

No paper or unpublished manuscript is included. If this project becomes part of a paper, follow the professor's instruction to keep it local until the paper is submitted for publication. Existing repository history remains intact; ignore rules do not remove previously tracked files.
