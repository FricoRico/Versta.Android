# Supertonic 3 TTS — release gates

NEU-71 (plan workstream E, NEU-55 rev 1). This document is the gate contract
for shipping the Supertonic 3 TTS migration. Every gate names its owner, its
evidence, and where the evidence lives. Gate owners: QA/release = Priya,
device-lab execution = Maya (NEU-159 boundary), engine = Elin.

| # | Gate | Evidence | Status |
|---|------|----------|--------|
| 1 | Golden audio ×31 languages, green in CI | `tts-golden` workflow verify run on the release ref | pending engine merge |
| 2 | Device matrix pass | Maya lab report on the release PR | blocked on device lab (NEU-159) |
| 3 | Latency budget: TTFA + RTF ≤ Kokoro baseline | RTF/RSS table posted on the release PR | pending 1 + 2 |
| 4 | int8 spot-gate: ja, ar, vi, hi | WER table + listening checklist sign-off | pending 1 |
| 5 | Rollout notes + store copy ready | `docs/qa/supertonic-tts-rollout-notes.md` | ready (this change) |
| 6 | Feed flipped to supertonic3 (NEU-173) | models.versta.app feed diff | NEU-173 open |

## 1. Golden-audio regression

Harness: `tools/tts-golden/` (corpus contract, bless/verify flow, thresholds).
Corpus consistency is gated per-commit by `Supertonic3GoldenCorpusTest` (CI
`test` job). The full hash+WER verify runs via the manual `TTS golden audio`
workflow on the release ref, and after any change to `supertonic.cc`, the
tokenizer, the MNN pin, or the model bundle.

- Hash gate: bit-drift of synthesis output on a blessed platform+threads+seed.
  Any drift fails the gate and must be explained in the PR that blesses new
  references.
- WER gate: whisper.cpp (`ggml-base`) round-trip per language vs
  `thresholds.json`. Provisional floors until the first blessed run; the
  release candidate run must tighten them to observed values + 0.05 headroom.

## 2. Device matrix

Minimum matrix (all on the release APK, fresh install + bundle download):

| Slot | ABI | Form | Checks |
|------|-----|------|--------|
| Reference | arm64-v8a | physical, SDK 34/35 (lab `android-a` class) | full: golden WER, latency, RSS, all 31 languages speak |
| Legacy ARM | armeabi-v7a | physical, min SDK 28 | smoke: en + ja synthesize, RSS under ceiling |
| Emulator | x86_64 | API 36 AVD | smoke: en synthesizes, no native crash |

Memory ceiling: peak RSS during synthesis ≤ the Kokoro baseline measured on
the same device (int8 bundle is 99.8 MB on disk vs Kokoro's 163.9 MB; RAM is
the metric that gates, not disk). Lab records `dumpsys meminfo` after a
5-sentence loop per language slot.

Device-lab requests go to Maya by issue comment, naming this table. The lab
is currently down (NEU-159); the matrix pass is scheduled there, never from
an agent sandbox.

## 3. Latency budget

Budget (must both hold on the reference arm64 device, int8, default threads):

- **RTF** (synthesis wall time ÷ audio duration) ≤ Kokoro's RTF on the same
  device, same sentences.
- **TTFA** (synthesize() call → first PCM callback) ≤ Kokoro's TTFA, same
  setup.

Kokoro is removed from `develop`; the baseline is measured from the last
pre-removal release build (v1.3.2-beta.2 tag) on the same device, 10 runs,
median. Supertonic measured identically on the release candidate. Reference
points already observed (not device numbers): community MNN port claims RTF
≈ 0.07 on-device; engine host benchmarks (x86_64, 10 tokens) are int8 t=4
RTF 0.165 / RSS 535 MB, fp16 0.164 / 620 MB, fp32 0.172 / 789 MB.

The release PR publishes the TTFA/RTF/RSS table (per language slot: en, ja,
ar, vi, hi + a 5-language random draw).

## 4. int8 per-language spot-gate (Decision 4)

Languages: **ja, ar, vi, hi**. For each, on the reference arm64 device and
the CI host:

1. WER from the golden harness must pass that language's threshold — these
   four carry tighter provisional thresholds (0.30/0.15 CER) than the 0.25
   default.
2. Listening check: one reviewer plays the four wavs (uploaded by the gate
   workflow) and confirms no dropped phonemes, garbled tones (vi), or wrong
   gemination (ja/ ar). Checklist sign-off is recorded on the release PR.

If a language fails on a device class: ship the fp16 engine set for that
device class (bundle ships the fp16 variants alongside int8 default),
re-run the gate for that language, and record the exception in the release
notes. fp16 costs RSS (+~85 MB host-side) — the exception list must stay
short.

## 5. Rollout

See `docs/qa/supertonic-tts-rollout-notes.md` — phased track plan, guardrail
metrics, rollback, store copy (including the zh/ca/fa/he unavailability
note), and support copy.

## 6. Feed flip

NEU-173 swaps `text-to-speech/models.json` kokoro → supertonic3 and drops
`versta-tts-data` from `data.json`. The app release and the feed flip land
together; the strict baked-catalog gate (`validateBakedCatalogsStrict`,
NEU-177) covers registry drift at release time.

## Sign-off checklist (release PR)

- [ ] `TTS golden audio` verify green on the release ref (host)
- [ ] Device matrix pass posted by Maya (all three slots)
- [ ] TTFA/RTF/RSS table posted, budget met vs v1.3.2-beta.2 baseline
- [ ] int8 spot-gate signed off for ja/ar/vi/hi (or fp16 exceptions recorded)
- [ ] `manifest.json` blessed from the merged engine head
- [ ] Rollout notes + store copy reviewed; Play Console text staged
- [ ] NEU-173 merged and live feed verified from a fresh install
