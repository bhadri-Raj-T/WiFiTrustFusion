# WiFiTrustFusion 1.2-demo — Final Interface and Failure Lab

Developed for Forensic Analytics (CSE4074), Fall 2026-27, Vellore Institute of Technology, Chennai, by Bhadri Raj T, Ajay A and Vyomika R, under Dr. S. Johanan Joysingh.

## Changes

- Fixed four-tab interface: My Wi-Fi, Analysis, Demo and Reports.
- Twelve-item live checklist with status badges and expandable explanations.
- DNS/TCP/HTTPS progress, cancellation, discarded-result handling and HTTP status from the same HTTPS response.
- Live report copy omits Wi-Fi identifiers and includes capture/check times.
- Dedicated in-app twenty-case baseline/failure/edge-case simulator with run-one/run-all, stop/reset, chart, virtual automatic response and labelled summary/copy.
- Optional explicitly armed foreground response for current/recent Open/WEP configuration, alongside the live signal pause demonstration.
- Updated project explanation, final presentation flow and local build/publish instructions.

## Install

Download `WiFiTrustFusion_Final.apk` from Assets. Version name: **1.2-demo**; code: **4**; minimum Android: **8.1 / API 27**. This is a locally debug-signed classroom prerelease. An existing installation requires the matching signing certificate for an in-place update.

The checksum file identifies the attached APK bytes. `BUILD_SUMMARY.md` records the actual local JVM result; `RELEASE_BUILD.json` and `SOURCE_MANIFEST.txt` associate the build with its source. Instrumented and manual phone checks are separate. Source text hashes normalise UTF-8 BOM/CRLF; binary PNG/WEBP/JAR hashes use exact bytes. These records are not a safety certificate or byte-identical-build guarantee.

## Demonstration and response limits

Every Demo-tab fixture and virtual pause is labelled SIMULATED. No attack is run and no real radio change is performed by the simulator. Real response controls use separate actual observations and explicit user arming. Current Android security or a matching-AP advertisement no older than 30 seconds is required for the Open/WEP profile; unknown/stale metadata does not trigger it. Low signal is a demonstration condition, not evidence of an attacker.

Android 8.1/9 can request Wi-Fi off; Android 10+ requires user completion in system controls. Actual radio state is checked before confirming it is off. Mobile data and the router are unaffected. The app does not detect every vulnerability, authenticate hotspot owners or guarantee personal-data protection.

Keep the existing `v1.1-demo` release and tag intact. This release contains the new APK for the new source revision. No unpublished paper, private dataset or signing key is included.
