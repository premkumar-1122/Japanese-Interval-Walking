# CI/CD & Release Pipeline

This project ships Android releases through a **tag-triggered GitHub Actions workflow**.
There is **no local release build** — signing happens in CI using repository secrets.

## Workflow: `.github/workflows/release.yml`

| Trigger | Event |
| --- | --- |
| `v*` tag push | `on.push.tags: ['v*']` |
| Manual | `workflow_dispatch` (input `tag_name`) |

### What it does
1. Checks out the repo with full history (`fetch-depth: 0`).
2. Sets up JDK 17 (Zulu).
3. **Decodes the keystore** — uses the committed `keystore-base64.txt` if present,
   otherwise falls back to the `KEYSTORE_BASE64` secret.
4. Builds the **release APK** (`./gradlew :app:assembleRelease`) with signing env vars.
5. Builds the **release AAB** (`./gradlew :app:bundleRelease`).
6. Renames artifacts to `japanese-interval-walking-<tag>.apk/.aab`.
7. **Generates the changelog** from conventional commits since the previous tag,
   grouped into Features / Bug Fixes / Chores / Other, plus an Assets section.
8. Creates the GitHub release, uploads the APK + AAB, and attaches the changelog.

## Required secrets (GitHub Actions → repo/organization)
| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of the upload keystore (fallback if `keystore-base64.txt` is absent) |
| `STORE_PASSWORD` | Keystore store password |
| `KEY_ALIAS` | Key alias (default `upload`) |
| `KEY_PASSWORD` | Key password |
| `GITHUB_TOKEN` | Auto-provided; used to publish the release |

> The keystore file (`my-upload-key.jks`) is git-ignored and lives only in CI. Do **not**
> look for signing secrets in the local working copy — previous releases were built the
> same tag-triggered way.

## How to cut a release (for humans / agents)
See [`SKILL.md`](./SKILL.md) for the full runbook. In short:

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`; commit to `main`.
2. Commit only source/version files (never `*.log`, keys, or `.env`).
3. Push `main` (rebase if the remote moved forward).
4. `git tag -a vX.Y.Z -m "..."` then `git push origin vX.Y.Z`.
5. Wait for the `Package & Release Android App` run to finish `success`.
6. The release (APK + AAB + changelog) is created automatically.
   An agent may optionally curate the release notes via the GitHub API using the token
   from the local git credential manager (see `SKILL.md` step 6).
