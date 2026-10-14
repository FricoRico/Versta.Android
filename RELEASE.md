# Release & beta checklist

Owner: QA & release engineering. This is the boring path from `develop` to a
Play testing track. If a step needs judgement, stop and ask.

## Prerequisites

One-time wiring (GCP Workload Identity Federation, Play Console invite,
repo variables/secrets) lives on the NEU-7 issue as the
`play-ci-setup-runbook` document — one-time setup is deliberately not kept
in this repo. Until it's done, every `Release (beta)` run fails fast at the
`preflight` job with a pointer there. Auth is keyless by design: no
service-account JSON key exists anywhere (the GCP org policy
`iam.disableServiceAccountKeyCreation` blocks them, and nothing weaker
should be minted).

## Quality gates (agreed, do not widen mid-flight)

| Gate | Threshold | Where |
|---|---|---|
| CI on the commit | `static-analysis`, `android-lint`, `assemble` all green | Actions → CI |
| Smoke pass on the exact AAB | all steps in *Smoke pass* below | manual until NEU-8 automates it |
| Crash-free sessions | ≥ 99.5 % per cohort before promoting further | Play Console → Android vitals |
| ANR rate | < 0.5 % daily sessions | Android vitals |
| Version | `versionCode` > every code already in Play; `versionName` carries `-beta.N` | `app/build.gradle.kts` (GPP auto-resolves conflicts) |

Promotion rule: a cohort may only be promoted (internal → closed → open →
staged %) after it has held the crash-free + ANR gates for **7 days**.
Regression on any gate = halt rollout in Play Console, file an issue, triage.

## Shipping a beta build (one click)

1. Confirm the develop HEAD commit is green on the CI workflow (all 3 gates).
2. Actions → **Release (beta)** → *Run workflow* → track `internal`
   (or `alpha`/`beta` per rollout phase) → optional release name.
3. `preflight` verifies secrets; `publish` builds the universal AAB and
   uploads via Gradle Play Publisher. The AAB is also attached to the run.
4. Success = release visible in Play Console under the chosen track.
   Upload failures (auth, size, version) come back here with owner + ETA —
   do not retry blindly.

## Smoke pass (run on a device installed from the internal track)

Until NEU-8 puts this suite on an emulator in CI, run by hand on one
mid-range device (e.g. Pixel 6a):

- [ ] Cold install → onboarding completes, no permission traps
- [ ] Download one translation model on Wi‑Fi; progress + completion clean
- [ ] Airplane mode → text translation works (offline promise)
- [ ] Airplane mode → voice translation works (model present)
- [ ] Image translation basic pass (camera → overlay)
- [ ] Conversation screen opens, languages switch, no layout blowups
- [ ] Rotate / background / resume during an active translation
- [ ] No crash dialogs; logcat error burst check

## Crash triage loop (Android vitals as the sink)

- Sink: Play Console → *Android vitals → Crashes and ANRs*. Native C++
  crashes symbolicate because the AAB embeds `SYMBOL_TABLE` debug symbols
  (see `debugSymbolLevel` in `app/build.gradle.kts`; R8 is off today — if it
  turns on, GPP auto-uploads `mapping.txt`).
- Deliberate choice: **no Firebase/Sentry SDK**. Android vitals is zero-code
  and matches the app's no-telemetry license stance. Revisit only with a
  founder decision and an opt-in switch.
- Cadence during beta: I check vitals daily; new crash clusters get an issue
  with repro + affected device/ABI within 24 h. Founder sees a weekly digest
  while a beta cohort is open.

## Rollback

1. Play Console → the track → *halt rollout* (internal/closed: remove the
   release). Immediate.
2. File an issue tagging the smoke/vitals signal that triggered it.
3. Previous good build stays installable; Play serves it to new testers once
   the bad release is pulled.
