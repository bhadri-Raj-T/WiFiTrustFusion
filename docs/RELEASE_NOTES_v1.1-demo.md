# WiFiTrustFusion 1.1-demo — Third Review

WiFiTrustFusion: Explainable Wi-Fi Monitoring and Connection Diagnostics.

Developed for **Forensic Analytics (CSE4074)**, Fall 2026-27, at **Vellore Institute of Technology, Chennai**, by **Bhadri Raj T, Ajay A and Vyomika R**, under **Dr. S. Johanan Joysingh**.

## Included

- New Safety screen with eight labelled simulated security scenarios.
- Explicitly armed, one-shot live-RSSI pause demo with connection/lifecycle guards.
- Legacy-phone Wi-Fi shutdown request with observed-state confirmation; Android 10+ system-control handoff.

- A connected-Wi-Fi interface and Android-managed network selection.
- Live signal chart with median, minimum/maximum, range and population standard deviation.
- Available security information with source limitations explained.
- User-triggered, Wi-Fi-bound DNS, TCP and HTTPS checks.
- Foreground explanations for weak/varying signal, sign-in requirements and incomplete checks.
- Local report drafts and retained development-session tools.
- Final branding, launcher icon, fixed navigation tabs and diagnostic retry countdown.
- README, presentation-aligned project explanation and demonstration guide.

## Download and install

Download **`WiFiTrustFusion_Final.apk`** from this release's Assets section. It requires **Android 8.1 / API 27 or newer**, subject to device installation policy. This is an installable, locally debug-signed classroom **prerelease**. Publishing it as a GitHub release does not change it into a production-signed Android release build.

`WiFiTrustFusion_Final.sha256` contains the checksum of the attached APK. On Windows:

```powershell
Get-FileHash .\WiFiTrustFusion_Final.apk -Algorithm SHA256
```

Compare the result with the checksum file. The hash detects a difference in file bytes; it does not prove that the software is safe or establish a legal chain of custody.

Allow app installation from the source you use to open the APK, then grant the permissions needed by the app. Location settings may affect visible network identity. Updating an existing installation requires a matching signing certificate.

## Safety-demo boundaries

Training examples do not run attacks and cannot activate real Wi-Fi changes. The live pause demonstration uses a user-selected signal threshold, not an attack verdict. It affects this phone only; mobile data and the router are unaffected. No guarantee of personal-data protection is made.

## Build evidence and limits

See the attached **`BUILD_SUMMARY.md`** for the actual local build/unit-test result, **`RELEASE_BUILD.json`** for the APK and source fingerprint, and **`SOURCE_MANIFEST.txt`** for source-file hashes. Source text hashes normalise line endings to LF and omit a UTF-8 BOM; binary asset hashes use the exact bytes. These records associate the build with the supplied source; they do not guarantee a byte-for-byte reproducible APK across machines.

Android instrumented tests and manual phone checks are separate from the unit-test build. The application does not certify hotspot legitimacy, implement validated attack detection, run a throughput test or provide a deployed cloud reputation service. No research paper, raw network evidence, private dataset or signing key is included.
