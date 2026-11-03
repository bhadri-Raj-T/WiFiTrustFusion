# Final interface and presentation workflow — 1.2-demo

## What the user does

| Tab | Action | Output |
|---|---|---|
| My Wi-Fi | Analyse the connected network, or choose Wi-Fi through Android and return | Confirmed actual connection; Android retains password handling |
| Analysis | Run checks, watch DNS/TCP/HTTPS progress, inspect or cancel | Source-labelled twelve-item checklist, signal chart, explanations and private report copy |
| Demo | Choose a case or Run All | Twenty invented fixtures, step progress, illustrative chart, response and completed-case summary |
| Reports | Save an optional local note | Local draft; sharing occurs only through a user-selected Android action |

The main header retains the logo and Tools entry; the four tabs remain visible while scrolling. The former sample-preview entry now opens Demo. The live checklist has readable status labels and tap-to-expand explanations. Copying a live report omits SSID/BSSID and adds capture/last-check times.

## Meaning of twelve live checklist items

The items are: Wi-Fi route, network visibility, RSSI, signal window, security configuration, portal flag, Android internet validation, DNS, TCP, HTTPS endpoint identity, HTTP response and connection continuity. Only DNS, TCP and HTTPS are active-check types. HTTP status comes from the same HTTPS HEAD request. The remaining items are observations or integrity checks, not extra vulnerability probes.

Unknown/not-run is never a pass. HTTP 503 can coexist with successful TLS verification. DNS failure leaves dependent TCP/HTTPS work uncompleted. Connection changes and cancellation do not create successful missing results. In-flight operating-system requests remain bounded by their timeouts; cancellation prevents subsequent phases/results from being accepted.

## Twenty Demo cases

All of these are **SIMULATED**, including their measurements, names, timings, flags and automatic responses. They do not run attacks, send probes, alter real settings or enter collector evidence.

| Case | Expected teaching point | Virtual pause when simulator policy is on |
|---|---|---|
| Healthy baseline | Compare independent observations and probe results | No |
| Wi-Fi radio off | No probes can run without the virtual route | No new pause; already off |
| Wi-Fi on, disconnected | An enabled radio is not an associated network | No |
| Identity hidden | Missing permission/Location evidence is not a detected attack | No |
| Low-signal pause rule | A chosen demo threshold can trigger a response; signal does not identify hackers | Yes |
| Signal variation | Environment/movement can explain variation | No |
| Open Wi-Fi | Link protection is absent in the fixture | Yes |
| WEP | Legacy configuration requires attention | Yes |
| Security unknown | Do not invent a secure or unsafe configuration | No |
| Look-alike name | A similar name does not authenticate the operator | No |
| Different AP with same name | Legitimate roaming is possible; discard old results | No |
| Captive portal | Complete and verify sign-in | No |
| DNS timeout | TCP/HTTPS dependencies remain uncompleted | Yes, as an optional simulator response |
| All TCP attempts fail | Failure may be filtering, routing or endpoint-specific | Yes, as an optional simulator response |
| Partial TCP failure | Partial access differs from total failure | No |
| Slow TCP | Timing is not throughput or an attack verdict | No |
| HTTPS identity rejection | Do not bypass identity warnings; exact live failure cause may be unknown | Yes |
| HTTP 503 | Application status and TLS identity are separate | No |
| Connection changes mid-run | Discard previous-connection results | No |
| User cancellation | Missing results must not become passes | No |

The checkbox controls the **simulator's virtual response only**. Run All demonstrates all twenty cases in roughly one minute, depending on UI scheduling. The summary is a record of simulated cases, not a count of measured network failures or passed unit tests. Copy exports the last completed case with its SIMULATED label.

## Real response controls

Open **Demo → Open live Wi-Fi pause controls**. This opens a separate foreground screen. Nothing is armed by default.

| Profile | Accepted inputs | Trigger |
|---|---|---|
| Open/WEP configuration | Android current connection security, or a matching-AP advertisement aged 0–30s | Three consecutive foreground polls reporting Open Wi-Fi or WEP |
| Low signal demonstration | Valid RSSI from the current phone connection | Three consecutive polls below the selected threshold |

Both rules are one-shot and cancel on a changed/lost connection, a long timing gap or leaving the screen. Cached observations can repeat; three polls are not three independent scans. Unknown/stale security cannot trigger the configuration profile. OWE is not treated as unencrypted Open Wi-Fi.

Android 8.1/9 can accept a Wi-Fi-off request and the app checks the observed radio state. Android 10+ opens system controls, where the user completes the switch-off. A requested action is not reported as confirmed until the radio is observed off. The action affects this phone only; mobile data, the router and other devices remain outside its scope.

This is not a detector for every vulnerability, an authenticated hotspot-ownership check, a firewall, a VPN or a guarantee of personal-data protection. Simulated inputs cannot arm or invoke the real response.

## Five-minute presentation sequence

1. **40 seconds:** show My Wi-Fi, the actual connection and Android-managed selection.
2. **60 seconds:** show the live signal chart, run the small checks and open the formatted checklist. Explain one pass, one unavailable item and HTTP/TLS separation if relevant.
3. **90 seconds:** Demo: show healthy baseline, DNS timeout, HTTPS rejection and unknown security. Show virtual pause for eligible cases and no pause for missing evidence.
4. **60 seconds:** on your own phone, demonstrate an explicitly armed real response. Identify the selected condition and Android-version behaviour. Restore Wi-Fi through system controls afterward.
5. **50 seconds:** show the actual local build summary, README/course details, explanation file and new release assets. State the measured-versus-simulated boundary.

The implementation contribution is a consistent report format, connection-aware result handling, a controlled failure harness and explicit source-separated response controls. Do not describe it as validated attack detection or claim all scenarios were reproduced on a live network.
