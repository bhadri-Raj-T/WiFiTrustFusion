# Third-review demonstration

## Before the review

1. Build with `Build-ReviewApk.ps1` and confirm it prints `BUILD AND UNIT TESTS PASSED`. Keep `Final_APK/BUILD_SUMMARY.md` available.
2. Install that exported APK on the phone. Allow the required permissions and turn on the phone's Location setting if network details are hidden.
3. Use a Wi-Fi network or hotspot you control. Keep its owner/password visible only on the phone's system connection screen.
4. Rehearse normal readings and one real warning. Prepare a screenshot of a warning that you actually observed if the room does not reproduce it reliably.
5. Keep the APK and checksum available offline. A working GitHub link is helpful, but the live demonstration should not depend on downloading it.

## Final Demo tab and live-response rehearsal

The Demo tab contains twenty invented scenarios and never changes the real phone's Wi-Fi. Use **Run selected case** for a short presentation or **Run all 20 scenarios** for the whole suite. The chart, statuses, timings and virtual pause are SIMULATED. Stop/reset and leaving the tab cancel its remaining callbacks. Expand the run summary to review completed fixtures; copy retains the SIMULATED label.

See [FINAL_DEMO_FLOW.md](FINAL_DEMO_FLOW.md) for the full case matrix and presentation sequence.

For a real signal response on your own phone:

1. Connect and verify the actual live RSSI in Analysis.
2. Open **Demo → Open live Wi-Fi pause controls**.
3. Select **Low signal (demonstration)**. For a predictable rehearsal, select **Below -40 dBm (quick demo)** only if the actual signal is below -40, such as -43. This deliberately chosen threshold does not mean the network is weak or malicious.
4. Tap **Arm live response** and accept the real-action explanation. Observe three consecutive live polls and the one-shot response, roughly six seconds under normal scheduling.
5. On Android 8.1/9, inspect whether the Wi-Fi radio is observed off. On Android 10+, finish switching it off in the opened system controls and return. A request or handoff is not automatically labelled as a successful shutdown.
6. Restore Wi-Fi through Android's controls. Nothing rearms automatically.

For a real configuration preference, use a test hotspot you control with an Open configuration, allow network visibility and return promptly from Android Wi-Fi settings so a matching advertisement may be recent. Arm **Open/WEP configuration**. The rule requires current Android security or a matching-AP advertisement aged 0–30 seconds for three foreground polls. If the metadata is missing/stale, it waits and does not disconnect. No active scan was added to force this observation. You can demonstrate that missing-evidence behaviour too. Do not perform sensitive activity on the temporary Open hotspot; restore its normal protection after the demonstration. WEP can remain a labelled fixture; no WEP network needs to be created.

Show a cancellation by arming then leaving the live-response screen. Connection changes or timing gaps also disarm it. The simulator cannot invoke the physical response. This affects this phone's Wi-Fi only and does not block mobile data, secure the router or guarantee that personal information is safe.

Check the phone OS if needed:

```bat
"D:\Android\platform-tools\adb.exe" -s 5203cb41fa4d534d shell getprop ro.build.version.release
```

## Five-minute presentation demo

| Time | Show | Explain |
|---|---|---|
| 0:00–0:40 | README and project title/team/course details | The project explains Wi-Fi observations and diagnoses connection problems |
| 0:40–1:20 | My Wi-Fi and Android's Wi-Fi chooser | Android manages the password; the report uses the actual connection after returning |
| 1:20–2:10 | Analysis chart and summary | Latest RSSI, median, range, SD, count and duration are separate descriptive values |
| 2:10–3:00 | Check internet connection | DNS, TCP and HTTPS use the Wi-Fi route; TCP time is not download speed |
| 3:00–4:10 | Demo: one labelled simulated example and the separate live response | Explain invented versus measured inputs; report the actual shutdown or system-control handoff |
| 4:10–5:00 | Explanation file, build summary and GitHub release | Source, actual local test result, APK download and project limits |

Project Tools are an optional extra if time permits: open a development session/history and explain CSV as the reference record and Room as its mirror. Avoid opening collected data or private identifiers on a public screen without checking them first.

## Real warning conditions you can demonstrate

| Scenario | What to do on your own network | What must actually be observed |
|---|---|---|
| Weak reception | Move farther from your access point while keeping Analysis open | A valid RSSI below −75 dBm produces “Try a closer position” |
| Changing reception | Observe near the access point, then change position | At least 5 readings over at least 8 seconds with range ≥12 dB produces the variation explanation |
| Connected but internet unavailable | Keep the phone joined to a test hotspot and disable that hotspot's upstream internet | Android may remain unvalidated and DNS/HTTPS may fail or time out; report whichever result occurs |
| Captive login | Use an authorised network that actually has a portal | The sign-in explanation appears if Android reports the captive-portal flag |
| Open network configuration | Temporarily use an isolated test hotspot with security set to None, if your hotspot permits it; restore its normal security afterward | If available metadata identifies Open Wi-Fi, show the configuration explanation; no open network needs to be created for the basic demo |
| Disconnection | Turn the phone's Wi-Fi off, then restore it | The interface asks the user to connect and disables checks without a Wi-Fi route |
| Connection change | Change between two authorised Wi-Fi connections | A new signal history begins; a running check from the previous connection must not be presented as the new network's result |

No attack or packet interference is needed for these demonstrations. Do not enter passwords or perform sensitive activity on the temporary open hotspot. A device may hide security metadata, so use the signal or connectivity scenario as the main rehearsed example.

The Tools assessment demo, if used, contains synthetic examples. Identify them as synthetic. Do not present them as live phone measurements.

## Screenshot from the laptop

In Command Prompt in the review folder:

```bat
if not exist screenshots mkdir screenshots
"D:\Android\platform-tools\adb.exe" -s 5203cb41fa4d534d exec-out screencap -p > ".\screenshots\review-demo.png"
start "" ".\screenshots\review-demo.png"
```

Run this from Command Prompt, rather than using Windows PowerShell 5.1 text redirection for the binary PNG stream. Replace the serial for a different phone. Screenshots are ignored by the submission repository; review the image for network identifiers before deliberately adding one to presentation material.

## Questions to be ready for

- **What is new about your implementation?** Integrating connection-aware measurements, a short statistical view, Wi-Fi-bound diagnostics and explicit explanations in one usable Android flow.
- **Does strong signal mean secure Wi-Fi?** No. Reception, advertised link protection and hotspot ownership are different questions.
- **Is this an attack detector or speed test?** No. It is a monitoring and connection-diagnostics prototype.
- **Why is this related to forensic analytics?** It records and interprets observations with source, timestamp and connection context, while explaining limitations in evidence quality.
- **Are reports uploaded to a community server?** No. They are local drafts; sharing is explicitly initiated by the user.
- **What passed testing?** Show the actual local `BUILD_SUMMARY.md`. Instrumented tests require separate execution on a device or emulator.
