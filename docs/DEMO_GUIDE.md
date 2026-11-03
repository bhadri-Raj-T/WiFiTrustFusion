# Third-review demonstration

## Before the review

1. Build with `Build-ReviewApk.ps1` and confirm it prints `BUILD AND UNIT TESTS PASSED`. Keep `Final_APK/BUILD_SUMMARY.md` available.
2. Install that exported APK on the phone. Allow the required permissions and turn on the phone's Location setting if network details are hidden.
3. Use a Wi-Fi network or hotspot you control. Keep its owner/password visible only on the phone's system connection screen.
4. Rehearse normal readings and one real warning. Prepare a screenshot of a warning that you actually observed if the room does not reproduce it reliably.
5. Keep the APK and checksum available offline. A working GitHub link is helpful, but the live demonstration should not depend on downloading it.

## New Safety screen: teaching scenarios and a real pause

Open **Safety** in the app header. Its two panels have different purposes:

- **SIMULATED:** select one of eight teaching scenarios and tap Play simulated example. Both its chart and security conditions are invented and labelled. No attack runs, no phone setting changes and this panel cannot activate a real pause. Playing an example disarms the live rule.
- **LIVE:** the first chart reads the phone's actual Wi-Fi. An explicitly armed rule can request Wi-Fi off after three consecutive polls below your threshold. This is a connectivity demonstration, not a validated security rule.

For a predictable rehearsal on your own phone:

1. Connect to your hotspot, allow network details and keep Safety open until live RSSI appears.
2. Choose **Below -40 dBm (quick demo)** if the actual signal is already below -40 (for example, -43). This deliberately selects a threshold above the current signal; it does not mean the connection is weak or malicious.
3. Tap **Arm live pause rule** and read/accept its real-action explanation. Observe 1/3, 2/3 and then the one-shot trigger after approximately six seconds. Android may repeat cached values, so these are consecutive polls, not independent scans.
4. On Android 8.1/9, inspect whether the phone's Wi-Fi actually turns off. The app first says requested, then confirms only after observing the radio off. A refused/unconfirmed request is shown as such.
5. On Android 10+, Android's Wi-Fi controls open. Switch Wi-Fi off yourself and return to observe its state. Do not describe this as automatic shutdown on those versions.
6. Restore Wi-Fi through **Open Wi-Fi controls**. The app never automatically rearms or reconnects the phone.

For a movement-based demonstration, choose -55, -65 or -75 dBm and move away until the actual RSSI stays below it for three polls. Rehearse the distance first. If the room does not reproduce that condition, use the quick-demo threshold or the separately confirmed **Pause Wi-Fi on this phone now** button, and explain exactly which trigger was used.

Show one cancellation: arm, then press Back or start a simulated example. The real rule must be disarmed. Rotation, backgrounding, a connection change or a long timing gap also cancels it. No background protection service is implemented.

Turning off this phone's Wi-Fi stops use of that Wi-Fi radio; it does not secure the router, disconnect another device, block mobile data or guarantee that personal information is safe. The app does not inspect packets or identify hackers, evil twins, DNS poisoning or deauthentication attacks. The scenarios teach what such concerns mean and what observations do and do not support.

Check the connected phone's Android version if needed:

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
| 3:00–4:10 | Safety: one labelled simulated example and the live pause | Explain invented versus measured inputs; report the actual shutdown or system-control handoff |
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
