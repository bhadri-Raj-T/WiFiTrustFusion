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

The main interface contains **My Wi-Fi**, **Analysis**, **Demo** and **Reports**, with a **Tools** entry for the earlier development workflow.

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
| `FailureScenarios` and `FailureLabPanel` | Twenty labelled fixtures, step-by-step reports, virtual response and run summary |
| `ConnectionCheckReports` and `CheckReportRenderer` | A consistent source-labelled live/simulated report format |
| `SafetyDemoActivity`, `LivePauseRule`, `LiveSecurityPauseRule` | Explicitly armed foreground live signal/configuration response, separated from simulation |
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

### Demo tab and real response controls

The dedicated Demo tab offers twenty baseline/failure/edge-case fixtures. It supports run-one/run-all, step progress, an illustrative chart, stop/reset, a completed-case summary and labelled report copying. All inputs are invented. Eligible fixtures demonstrate an automatic pause of a virtual session; this cannot change the real phone's connection. Unknown evidence, legitimate AP changes and cancellation are handled separately rather than becoming attacker verdicts.

The live-response screen is a separate, explicit user action from the Demo tab. The user can arm a low-signal demonstration or a current/recent Open/WEP configuration preference. Three matching foreground polls are needed. Configuration data must come from Android current security or a matching-AP advertisement no older than 30 seconds. Leaving the screen, a timing gap or a changed connection disarms the rule. Android 8.1/9 can request Wi-Fi off and check the radio; Android 10+ requires the user to finish in system controls. This does not block mobile data or guarantee personal-data safety.

## Slide 6 — Demonstration and observed output

Start with the normal connected network, keep Analysis open for 20–30 seconds, then show the current value, sample count, chart, median, range and standard deviation. Run the connection checks and explain each outcome separately. The exact preparation and warning demonstrations are in [DEMO_GUIDE.md](docs/DEMO_GUIDE.md).

One previously supplied working-phone screenshot showed **−43 dBm**, **29 readings over 58 seconds**, **median −44 dBm**, **minimum −55 dBm**, **maximum −39 dBm** and **standard deviation 3.2 dB**. Its derived range is 16 dB. This is one demonstration observation, not a benchmark or an accuracy result, and it may differ from the live presentation's readings.

To demonstrate a warning, move farther from an access point you control and observe whether the real signal crosses the rule threshold. Alternatively, use an authorised test hotspot with its upstream internet disabled to show incomplete internet checks while the phone remains connected to Wi-Fi. Explain the actual observed result; do not promise a particular failure stage.

## Slide 7 — Validation and current limitations

The supplied Windows log confirms a successful 1.1-demo build, 46 passing JVM tests, zero skipped, successful APK installation and main/tag pushes. The new 1.2-demo revision has not been compiled, tested or executed during preparation. It adds 23 focused test methods; their presence is not a passing result.

Run `Build-ReviewApk.ps1` locally to build, check actual unit-test XML and export the APK/checksum/build summary. Then inspect the phone using `docs/DEMO_GUIDE.md`. Instrumented tests require separate device/emulator execution. Source and APK hashes associate this submission with the local build; they are not a safety certification or a reproducible-build guarantee.

Limits remain Android/device differences, permission restrictions, a short signal window, a single diagnostic endpoint and limited device testing. The twelve-item live checklist includes passive observations and three active-check types; it is not twelve separate probes or a vulnerability scan. The application does not identify every attack, inspect packets, measure throughput or run a cloud reputation service.

## Slide 8 — Contribution and conclusion

The completed contribution is an Android interface that combines real connection selection, short-window signal statistics, Wi-Fi-bound diagnostics, explicit explanations and retained development-session tools. The design makes the relationship between an observation and its interpretation visible to the user.

See [the final demo flow](docs/FINAL_DEMO_FLOW.md) for the sequence and source-labelled cases.

Future work would prioritise more devices and Android versions, controlled diagnostic evaluation, clearer privacy/export controls and stronger evidence-integrity handling. Additional security classification would require a separate validated method and dataset.

**Closing line:** “WiFiTrustFusion makes Wi-Fi observations understandable and helps the user investigate connection problems. We report what was measured and avoid conclusions that those measurements cannot establish.”
