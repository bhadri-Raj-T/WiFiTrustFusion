# WiFiTrustFusion — Third Review Project Explanation

**Full project title:** WiFiTrustFusion: Explainable Wi-Fi Monitoring and Connection Diagnostics  
**Course:** Forensic Analytics, CSE4074  
**Institution:** Vellore Institute of Technology, Chennai  
**Semester:** Fall 2026-27  
**Team:** Bhadri Raj T, Ajay A, Vyomika R  
**Professor:** Dr. S. Johanan Joysingh

This file follows an eight-slide presentation sequence. Use the same headings in the PPT so the explanation and demonstration stay aligned. A PPT was not supplied with this submission package; the mapping below is a suggested presentation structure.

## Slide 1 — Problem and project objective

A phone's Wi-Fi symbol does not explain why an application is slow or unable to reach the internet. The phone may have weak reception, need a captive-portal login, fail a DNS lookup or have an access point without a working internet connection. A familiar SSID also does not establish who operates the network.

Our objective is to help a user inspect the actual connected Wi-Fi through a simple Android interface. We combine measurements, small connection checks and understandable explanations. The project focuses on a working course prototype and evidence-aware analysis.

**Presenter line:** “We help the user understand what the phone can observe about its Wi-Fi connection, and explain the limits of those observations.”

## Slide 2 — Proposed solution and user workflow

The main interface contains **My Wi-Fi**, **Analysis** and **Reports**, with a **Tools** entry for the earlier development workflow.

1. The user opens My Wi-Fi and analyses the already connected network.
2. If a change is needed, the app opens Android's Wi-Fi controls. Android collects the password and manages the connection.
3. On returning, the app reads the actual current connection. Opening settings is not treated as a successful connection.
4. Analysis displays live signal history, available security information and Android's validation/portal state.
5. The user explicitly starts internet checks and reads the result and advice.

The network list is based on information Android makes available to the phone. Device permissions, Location settings, cached results and Android-version differences affect what can be shown. If the network name is hidden but a usable Wi-Fi route exists, the app can still attempt its connection checks.

## Slide 3 — Architecture and implementation

The project has one Android application module, `:app`. It uses native activities and views; no online server is required for the implemented workflow.

| Component | Responsibility |
|---|---|
| `WifiExplorerActivity` | Main connection, analysis and local-report interface |
| `WifiConnectionMonitor` | Reads connection metadata and Android network state |
| `WifiConnectionPolicy` | Decides whether results still belong to the same Wi-Fi connection |
| `ConnectionAnalytics` | Maintains the signal window, calculates statistics and generates rule-based advice |
| `SignalChartView` | Draws the RSSI history against elapsed time |
| `WifiSecurityRules` | Formats supported security observations and their source |
| `WifiDiagnostics` | Runs DNS, TCP and HTTPS checks through the selected Wi-Fi `Network` |
| `SafetyDemoActivity` and `LivePauseRule` | Labelled training examples and an explicitly armed foreground live-signal pause demonstration |
| `CrowdReports` | Stores optional local drafts and prepares user-selected sharing |
| `MainActivity` and collector modules | Earlier development collection and review tools |
| CSV logger and Room database | Reference CSV development records and an asynchronous database mirror |

Kotlin implements the application logic. Room/KSP support local development-session history. AppCompat and Material components support the interface. The application keeps Wi-Fi passwords within Android's system-managed connection flow.

## Slide 4 — Analytics and interpretation

### Signal analysis

While the interface is in the foreground, the app polls current connection RSSI approximately every two seconds. It keeps up to 60 valid values in memory. Unavailable or invalid RSSI values are rejected. A connection change or a gap longer than six seconds clears the old window, so unrelated observations are not joined in one chart.

For valid readings, it calculates:

| Measure | Meaning in this application |
|---|---|
| Latest RSSI, dBm | The most recent available signal value; values closer to zero usually mean stronger reception |
| Median | The middle signal value, averaging the two middle values for an even count |
| Minimum and maximum | Lowest and highest recorded signal values in the current window |
| Range | Maximum minus minimum, expressed in dB |
| Population standard deviation | Descriptive variation of the values in this observation window |
| Count and duration | How many valid polls were kept and the time between the first and last |

These are descriptive statistics. Repeated polled values may come from Android's cache, and the short window does not support a long-term performance claim.

### Explainable rules

| Observation | Implemented condition | User-facing explanation |
|---|---|---|
| Weak recent signal | Valid latest RSSI below −75 dBm | Try a closer position |
| Signal variation | At least 5 readings, at least 8 seconds and a range of at least 12 dB | Signal changed during this observation |
| Open/legacy configuration | Reported security is Open Wi-Fi or WEP | Review the Wi-Fi security setting |
| Login required | Android reports a captive portal | Complete the network sign-in |
| DNS unsuccessful | The user-triggered name lookup fails | Name lookup did not complete |
| HTTPS unsuccessful | The user-triggered endpoint check fails | HTTPS check did not complete |
| Android has not validated access | Not validated and not identified as a portal | Internet access is not yet confirmed |

Multiple explanations may appear together. The variation rule uses the **range**, not a standard-deviation threshold. The descriptive signal label uses separate thresholds: Strong at −55 dBm or higher, Good at −70 or higher, Fair at −82 or higher, and Weak below −82. The closer-position advice begins below −75 dBm, so a Fair label and that advice can appear together.

## Slide 5 — Connection diagnostics and forensic relevance

The user-triggered diagnostic uses `example.com` as a small test endpoint:

1. Resolve its hostname through the selected Wi-Fi network.
2. When DNS succeeds, attempt up to three TCP connections to port 443 and report the median successful connection time and failures.
3. Make an HTTPS HEAD request using normal certificate and hostname verification.

The implementation binds these operations to Android's Wi-Fi `Network`, rather than silently treating a mobile-data success as a Wi-Fi success. It checks connection identity during the run and discards results after a connection change. A 30-second start cooldown reduces repeated requests. A timeout is an incomplete observation, not a security verdict.

The forensic-analytics connection is the care taken with provenance, timestamps, measurement scope and explanations. The retained Project Tools record requested scan sessions, analyse timestamp freshness and mirror CSV records into five Room tables. CSV remains the reference development record because the mirror can be incomplete. Session-local identity/configuration changes and evidence-quality levels are transparent heuristics; they do not prove an attack or provide a legal chain of custody.

### Safety demonstration and real action

The Safety screen keeps two sources separate. Its eight training examples use invented readings and descriptions for weak/changing signal, open/legacy Wi-Fi, look-alike names, same-name AP changes, captive portals, DNS/HTTPS failure and connection loss. These examples do not detect or generate attacks and cannot trigger the real pause action.

The LIVE panel polls the actual phone connection. The user can explicitly arm a one-shot demonstration that requests Wi-Fi off after three consecutive valid polls below a selected RSSI threshold. It cancels on a connection change, invalid timing gap, leaving the screen or starting a simulation. On Android 8.1/9, it requests Wi-Fi shutdown and then observes the radio state; on Android 10+, it opens system controls for the user to finish. Signal is a demonstration trigger, not evidence of a hacker. Turning off this phone's Wi-Fi does not protect the router, block mobile data or guarantee personal-data safety.

## Slide 6 — Demonstration and observed output

Start with the normal connected network, keep Analysis open for 20–30 seconds, then show the current value, sample count, chart, median, range and standard deviation. Run the connection checks and explain each outcome separately. The exact preparation and warning demonstrations are in [DEMO_GUIDE.md](docs/DEMO_GUIDE.md).

One previously supplied working-phone screenshot showed **−43 dBm**, **29 readings over 58 seconds**, **median −44 dBm**, **minimum −55 dBm**, **maximum −39 dBm** and **standard deviation 3.2 dB**. Its derived range is 16 dB. This is one demonstration observation, not a benchmark or an accuracy result, and it may differ from tomorrow's live readings.

To demonstrate a warning, move farther from an access point you control and observe whether the real signal crosses the rule threshold. Alternatively, use an authorised test hotspot with its upstream internet disabled to show incomplete internet checks while the phone remains connected to Wi-Fi. Explain the actual observed result; do not promise a particular failure stage.

## Slide 7 — Validation and current limitations

The earlier phone-fix revision was reported to pass 36 JVM unit tests, and the phone screenshots show working live signal analytics on Samsung SM-J710F. The subsequent branding and Safety-demo revision was prepared without a new build or app execution. Ten additional pure-rule unit-test methods are supplied but have not been executed during preparation. Therefore, that earlier test count must not be presented as a verified result of the latest source.

For this review, run `Build-ReviewApk.ps1` locally. It builds the packaged source, checks actual unit-test XML, calculates the exported APK's SHA-256 and writes a public build summary. The publisher verifies that source and APK still match that build record. Included instrumented test sources are not executed by this build script. Complete the manual phone checklist before the presentation.

Remaining limits include Android/device differences, permission restrictions, the short observation window, one diagnostic endpoint and limited physical-device evidence. The prototype has no trained ML model, calibrated risk score, validated malicious-hotspot classification, packet inspection, throughput test or deployed cloud-reporting service. Warning messages are foreground UI explanations, not background notifications.

## Slide 8 — Contribution and conclusion

The completed contribution is an Android interface that combines real connection selection, short-window signal statistics, Wi-Fi-bound diagnostics, explicit explanations and retained development-session tools. The design makes the relationship between an observation and its interpretation visible to the user.

Future work would prioritise more devices and Android versions, controlled diagnostic evaluation, clearer privacy/export controls and stronger evidence-integrity handling. Additional security classification would require a separate validated method and dataset.

**Closing line:** “WiFiTrustFusion makes Wi-Fi observations understandable and helps the user investigate connection problems. We report what was measured and avoid conclusions that those measurements cannot establish.”
