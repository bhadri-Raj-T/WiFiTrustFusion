# Existing repository: main-branch update and release

Repository: **https://github.com/bhadri-Raj-T/WiFiTrustFusion**. This workflow uses Git already installed on your Windows laptop. No GitHub plugin or GitHub CLI is required. It preserves the repository's collaborator access and history. The remote `dev-core` and `data-acquisition` branches are not merged or pushed.

## 1. Prepare the main review worktree and build

Save `WiFiTrustFusion_Final_UI_Update.zip` in Downloads. In **Command Prompt**:

```bat
powershell -NoProfile -Command "Expand-Archive -LiteralPath (Join-Path $env:USERPROFILE 'Downloads\WiFiTrustFusion_Final_UI_Update.zip') -DestinationPath 'D:\Important\Forensics Analytics' -Force"

powershell -NoProfile -ExecutionPolicy Bypass -File "D:\Important\Forensics Analytics\WiFiTrustFusion_Final_UI_Update\Prepare-FinalReview.ps1" -ProjectRoot "D:\Important\Forensics Analytics\WiFiTrustFusion_MainReview" -Build
```

The preparation helper checks the existing repository's origin, fetches `main`, creates a separate detached worktree and applies the update there. The original working tree, including uncommitted M-stage work and local data, is not reset or overwritten. Its branch selection is not changed. The new review source combines the inspected remote `main` with the published 1.1-demo source plus the final interface, twenty-case Demo tab and source-separated live responses. Unseen local changes cannot be included automatically.

The default worktree is:

`D:\Important\Forensics Analytics\WiFiTrustFusion_FinalReview`

The patch was prepared against main commit `986c741a12baadd50eb1c8a237cd2b05bbda097b`. If remote files have changed incompatibly, application stops rather than overwriting them. Share the first error to adapt the patch. If the review folder already exists, keep it and resume there; do not delete a worktree containing local edits.

`-Build` invokes `Build-ReviewApk.ps1` locally. The script runs clean debug assembly and JVM unit tests, verifies XML results and exports only after success. It keeps local SDK settings ignored and uses the laptop's normal debug-signing configuration. The supplied log confirms the preceding 1.1-demo passed 46 tests and installed. No build, tests or app execution were performed for this new 1.2-demo update during preparation.

The final export is:

`D:\Important\Forensics Analytics\WiFiTrustFusion_FinalReview\Final_APK\WiFiTrustFusion_Final.apk`

This is version **1.2-demo**, code **4**, minimum Android **8.1/API 27**. It is a classroom debug-signed APK, not a production-signed Android release build. Do not use an older APK from another export folder for this version.

If a build fails, fix that error and rerun the build in the review worktree:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File "D:\Important\Forensics Analytics\WiFiTrustFusion_FinalReview\Build-ReviewApk.ps1" -OriginalProjectRoot "D:\Important\Forensics Analytics\WiFiTrustFusion_MainReview"
```

## 2. Install and verify the phone

```bat
"D:\Android\platform-tools\adb.exe" devices -l
"D:\Android\platform-tools\adb.exe" -s 5203cb41fa4d534d install -r "D:\Important\Forensics Analytics\WiFiTrustFusion_FinalReview\Final_APK\WiFiTrustFusion_Final.apk"
"D:\Android\platform-tools\adb.exe" -s 5203cb41fa4d534d shell am start -n com.bhadrirajt.wifitrustfusion/.wifi.explorer.WifiExplorerActivity
```

Use the current device serial if it differs. Confirm the existing connection/analytics still work, open **Demo**, play a case and rehearse the separate actual foreground response on your own phone. See [DEMO_GUIDE.md](DEMO_GUIDE.md). Updating an installed copy requires the same signing certificate; do not uninstall an evidence-bearing copy simply to bypass a signing error.

## 3. Commit and push to the existing main

After the build and manual phone check succeed:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File "D:\Important\Forensics Analytics\WiFiTrustFusion_FinalReview\Publish-Main.ps1"
```

This checks the actual APK checksum and source fingerprint, stages the explicit public file list, makes a review commit, pushes **`HEAD:refs/heads/main`** and pushes the annotated **`v1.2-demo`** tag. It never force-pushes or updates `dev-core` or `data-acquisition`. Git uses your existing local authentication; complete Git's sign-in if prompted. If Git needs a commit identity, configure your own name and verified/no-reply email.

The review worktree is detached so it can coexist with any branch checked out in the original project. The explicit push still updates remote main. Your original local `main` may then be behind the remote; update it later with a normal fast-forward pull when that working tree is clean. No local branch is forcibly reset.

If main has advanced incompatibly, or branch protection denies a direct push, the script stops and leaves all local files intact. Do not use `--force`; use the repository's required review/merge process. If an existing `v1.2-demo` tag points to different code, it is not moved.

## 4. Create the release using GitHub's website

Git pushes commits and tags; creating the release page and uploading its binary assets are separate operations.

```bat
start "" "https://github.com/bhadri-Raj-T/WiFiTrustFusion/releases/new"
explorer "D:\Important\Forensics Analytics\WiFiTrustFusion_FinalReview\Final_APK"
```

In the **existing repository**:

1. Choose the already pushed tag **`v1.2-demo`**.
2. Set the title to **WiFiTrustFusion 1.2-demo — Third Review**.
3. Copy the description from `docs/RELEASE_NOTES_v1.2-demo.md` in the review worktree.
4. Attach `WiFiTrustFusion_Final.apk`, `WiFiTrustFusion_Final.sha256`, `BUILD_SUMMARY.md`, `RELEASE_BUILD.json` and `SOURCE_MANIFEST.txt` from `Final_APK`.
5. Mark this classroom demo as a **prerelease**, then select **Publish release**.

Your release URL will be:

**https://github.com/bhadri-Raj-T/WiFiTrustFusion/releases/tag/v1.2-demo**

Keep the already created v1.1-demo release and tag intact; publish the new source/APK under v1.2-demo. The same repository and collaborator access are retained. No new repository is created. No collaborator invitation is sent by the scripts.

## Review evidence and privacy

The top of README contains the prescribed title, course, institution, team, subject code, semester and professor. `PROJECT_EXPLANATION.md` follows eight suggested PPT sections. Align the PPT headings with that file before presenting; the actual PPT was not supplied here.

The update excludes new private datasets, collected evidence, signing keys, raw build logs and papers. Existing repository files/history are preserved; ignore rules cannot make previously committed content private. If writing a paper, follow the professor's instruction to keep the work local until it has been submitted for publication.

## Official references

- [Git worktrees](https://git-scm.com/docs/git-worktree)
- [Git push](https://git-scm.com/docs/git-push)
- [Manage GitHub releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
- [Android Wi-Fi control restrictions](https://developer.android.com/about/versions/10/privacy/changes#enable-disable-wifi)
