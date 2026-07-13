# Release Runbook (Agent Skill)

This document is the canonical procedure an agent (or maintainer) MUST follow to cut a new
release of **JIW Tracker**. It encodes the exact steps that worked for `v2.0.0` and the
gotchas that previously caused mistakes. Follow it verbatim.

> **TL;DR** — A release is produced by **pushing a `vX.Y.Z` git tag**, which triggers the
> GitHub Actions workflow (`.github/workflows/release.yml`). That workflow builds the signed
> APK + AAB using **CI secrets** and creates the GitHub release automatically. The agent then
> curates the release notes via the GitHub API. **Never attempt a local signed build.**

---

## 0. Pre-flight

- Work on `main`, make sure it is clean of unintended files.
- **Never commit crash/log artifacts.** Always exclude:
  - `hs_err_pid*.log`
  - `replay_pid*.log`
  - any `*.log`, build output, or local secrets (`local.properties`, `my-upload-key.jks`, `.env`).
- The keystore (`my-upload-key.jks`) and its passwords (`STORE_PASSWORD`, `KEY_ALIAS`,
  `KEY_PASSWORD`) are **GitHub Actions secrets only**. They are NOT in the local project
  folder, despite appearances. Do not waste time searching for them locally or trying to
  sign a release build on the dev machine — it will fail.

## 1. Version bump (commit to `main`)

Edit `app/build.gradle.kts`:

```kotlin
versionCode = <increment by 1>
versionName = "X.Y.Z"
```

Commit this as part of the release commit (or a dedicated chore commit).

## 2. Commit source changes

Stage **only** the intended source/version files explicitly (do not `git add -A`):

```powershell
git add app/build.gradle.kts app/src/main/java/...   # list each path
git commit -m "feat: vX.Y.Z - <short summary>" -m "- <bullet>" -m "- <bullet>"
```

Use a **conventional commit** style (`feat:`, `fix:`, `chore:`, `refactor:`, …) because the
CI workflow auto-categorizes the changelog from these prefixes.

## 3. Push `main`

```powershell
git push origin main
```

If rejected (`! [rejected] ... fetch first`), the remote has new commits. Rebase, then push:

```powershell
git pull --rebase origin main
git push origin main
```

## 4. Tag & push the tag (this triggers the release)

```powershell
git tag -a vX.Y.Z -m "Release vX.Y.Z: <summary>"
git push origin vX.Y.Z
```

Pushing the tag starts the `Package & Release Android App` workflow. It will:
1. Decode the keystore (from `keystore-base64.txt` committed in repo, or `KEYSTORE_BASE64` secret).
2. Build `app-release.apk` and `app-release.aab` (signed with CI secrets).
3. Rename artifacts to `japanese-interval-walking-vX.Y.Z.apk/.aab`.
4. Generate a changelog from commits since the previous tag and create the GitHub release
   with both assets attached.

> The workflow builds & publishes. **Do not** try to upload assets manually from the local
> machine — there is no `gh` CLI installed and no local signing possible.

## 5. Wait for the CI run to finish

Poll the run status via the GitHub API (no `gh` needed). Retrieve a token from the local
git credential manager (this is the same token git uses to push):

```powershell
$token = (@("protocol=https","host=github.com","" | Out-String | git credential fill 2>&1) |
    Where-Object { $_ -match '^password=' } | ForEach-Object { $_.Substring(9) }).Trim()

$headers = @{ Authorization = "Bearer $token"; Accept = "application/vnd.github+json" }

# find the run triggered by the tag
$runs = Invoke-RestMethod -Uri "https://api.github.com/repos/<OWNER>/<REPO>/actions/runs?per_page=5" -Headers $headers
$run  = $runs.workflow_runs | Where-Object { $_.head_branch -eq "vX.Y.Z" } | Select-Object -First 1

# poll until completed
do {
    $r = Invoke-RestMethod -Uri "https://api.github.com/repos/<OWNER>/<REPO>/actions/runs/$($run.id)" -Headers $headers
    Start-Sleep -Seconds 30
} while ($r.status -ne 'completed')

if ($r.conclusion -ne 'success') { throw "Release workflow failed: $($r.conclusion)" }
```

Replace `<OWNER>/<REPO>` with `premkumar-1122/Japanese-Interval-Walking`.

## 6. Curate the release notes

The workflow writes a commit-list changelog. To replace it with a polished, categorized
changelog, PATCH the release via the API:

```powershell
$rel = Invoke-RestMethod -Uri "https://api.github.com/repos/<OWNER>/<REPO>/releases/tags/vX.Y.Z" -Headers $headers

$changelog = @"
## Japanese Interval Walking — vX.Y.Z

### Features & Improvements
- ...

### Bug Fixes
- ...

### Chores
- Bumped version to X.Y.Z (versionCode N).

### Assets
- \`japanese-interval-walking-vX.Y.Z.apk\` — installable release APK
- \`japanese-interval-walking-vX.Y.Z.aab\` — Android App Bundle for Play Store
"@

$payload = @{ body = $changelog } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "https://api.github.com/repos/<OWNER>/<REPO>/releases/$($rel.id)" `
    -Method PATCH -Headers $headers -Body $payload -ContentType "application/json"
```

Build the changelog from **changes since the previous tag/commit** (use
`git log <prev-tag>..HEAD --pretty=format:"%s"` and the actual diff) — do not invent entries.

## 7. Cleanup

- Delete any temp file holding the token: `Remove-Item "$env:TEMP\ghtoken.txt" -Force`.
- Verify the release page shows both assets and the curated notes.

---

## Quick checklist

- [ ] Version bumped in `app/build.gradle.kts`, committed.
- [ ] Only source/version files committed (no logs/keys).
- [ ] `main` pushed (rebased if needed).
- [ ] `vX.Y.Z` tag created and pushed → CI triggered.
- [ ] CI run completed `success` with APK + AAB.
- [ ] Release notes curated via API PATCH.
- [ ] Temp token removed.
