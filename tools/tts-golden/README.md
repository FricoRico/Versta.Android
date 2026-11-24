# tts-golden — Supertonic 3 golden-audio gate

Release gate for NEU-71 (plan workstream E, NEU-55 rev 1). It answers two
questions on every gate run:

1. **Drift:** does the engine still render the corpus bit-identically to the
   blessed references? (sha256 per wav, per platform)
2. **Intelligibility:** can whisper.cpp transcribe what the engine speaks back
   into the corpus text within a per-language error threshold? (WER; CER for
   `ja`)

The corpus is `app/src/test/resources/tts/golden/sentences.json` — one fixed
sentence for each of the 31 Supertonic 3 languages. Its consistency with
`Supertonic3.LANGUAGES` is enforced by the JVM test
`Supertonic3GoldenCorpusTest` in the regular `test` CI job, so corpus edits
are gated independently of this harness.

## Layout

- `check.sh` — orchestrator. `verify` mode (default) gates; `--bless`
  (re)generates `manifest.json` locally.
- `wer.py` — normalization + WER/CER scoring for one case (stdlib only).
- `thresholds.json` — per-language thresholds. **Provisional** until the first
  blessed run; tighten then.
- `manifest.json` — blessed reference sha256 per language, per platform.
  Human-reviewed: it only changes via PR from a `--bless` run.

## Engine CLI

`check.sh` drives the native engine's host build through a small
non-production CLI, contract:

```
supertonic-synth --models <bundle dir> --config <tts.json> \
    --style <voice_styles/F*.json> --lang <iso 639-1> --text <text> \
    --seed <n> --speed 1.0 --threads <n> --out <out.wav>
```

- One sentence in, one wav out: 44.1 kHz mono PCM (f32 or s16).
- `--seed` must drive the latent RNG (seeded `std::mt19937`, per the NEU-64
  spike report) so repeated runs with the same binary + platform + seed are
  bit-identical.
- The CLI is the test seam over `supertonic.cc`; it ships with the engine's
  host build (`app/native/tests`). Until the native engine PR lands, only the
  corpus validation test is green — this harness fails fast with a pointer
  here, by design.

## Model bundle

Pin: `text-to-speech/v2.0.0/supertonic3-bundle.tar.gz`, 99,840,842 bytes,
sha256 `d975a46a57525fc7bb8c439d47ac0fca5b2f1ae845d7cb3e626bc5083359daec`
(models.versta.app). The workflow verifies the sha before extracting. The
bundle ships int8 `.mnn`s by default; pass an fp16 bundle dir for the
Decision-4 escape-hatch comparison runs.

## Whisper round-trip

Any recent whisper.cpp build works; the CI workflow uses `ggml-base.bin`
(multilingual) from the ggml-org release bucket and caches it. Install
locally with your package manager or build `examples/cli` from the
whisper.cpp sources. Override with `--whisper-cli` / `--whisper-model` (or
`WHISPER_CLI` / `WHISPER_MODEL`). Note whisper quality bounds how tight the
WER thresholds can go — the gate measures *speech staying intelligible*, not
engine parity with upstream.

## Running

```
# verify against the blessed manifest (CI)
tools/tts-golden/check.sh --bundle /path/to/supertonic3-bundle \
    --whisper-model /path/to/ggml-base.bin

# bless new references after an intended engine/model change
tools/tts-golden/check.sh --bless --bundle ... --whisper-model ...
# → review the printed WER table, then commit manifest.json via PR
```

Determinism contract: blessed hashes are pinned to
`<platform>` + thread count + seed. A verify run on a platform with no
blessed entry skips the hash gate and reports WER only. Device passes use
WER-only (never hash) — the manifest is for CI hosts.

## Exit codes

- `0` — gates green (or bless completed; bless never fails on WER).
- `1` — hash drift on a blessed platform, or WER over threshold.
- `2` — setup error (missing CLI/bundle/manifest/args).
