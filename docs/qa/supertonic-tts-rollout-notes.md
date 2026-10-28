# Supertonic 3 TTS — rollout notes

NEU-71. Release copy and rollout guardrails for the Supertonic 3 TTS
migration. Stage the Play Console text only when the release PR is up —
never publish copy about unshipped features; the in-repo
`app/src/main/play/release-notes/en-US/default.txt` is updated on the
release PR, not before.

## Phased rollout

1. **internal** — team devices; run the full device matrix
   (`supertonic-tts-release-gates.md` §2) on this build.
2. **alpha** — 100% of the alpha track; watch guardrails one week.
3. **beta** — staged 10% → 50% → 100%, one week at 10% and 50% each.

Each promotion requires the guardrails below to stay flat or improve versus
the previous release.

## Guardrails

- Crash-free sessions ≥ previous release; ANR rate ≤ previous release.
- Zero native crash signatures in `app_versta_translate_bridge` (watch the
  Play vitals native crash tab for `supertonic`).
- TTS failure telemetry does not exist on purpose (offline, no telemetry) —
  proxy signals are vitals + beta feedback channel volume/tags.
- Download failure rate for `supertonic3-bundle.tar.gz` (CDN logs) stays
  under 1%.

## Rollback

- **App-side:** staged rollout halt in Play Console; if a native crash
  signature appears, pull the release back to the previous build.
- **Feed-side:** revert the NEU-173 feed change (kokoro entry is gone from
  the app, so feed rollback only matters if the app release is pulled too —
  keep them coupled).
- **Quality exception:** a language that fails int8 on a device class moves
  to the fp16 engine set for that class (Decision 4 escape hatch) in a patch
  release; it is not a rollback trigger by itself.

## Store listing copy (Play Console → Main store listing)

Short description (80 chars):

> Offline translation with natural on-device voices in 31 languages

Full description — replace the voice paragraph with:

> Versta speaks your translations out loud with natural, neural voices that
> run entirely on your device — no network, no account, no data leaving your
> phone. Voice output is available in 31 languages: Arabic, Bulgarian, Czech,
> Danish, German, Greek, English, Spanish, Estonian, Finnish, French, Hindi,
> Croatian, Hungarian, Indonesian, Italian, Japanese, Korean, Lithuanian,
> Latvian, Dutch, Polish, Portuguese, Romanian, Russian, Slovak, Slovenian,
> Swedish, Turkish, Ukrainian, and Vietnamese.

> Text-to-speech is not yet available for Chinese, Catalan, Persian, or
> Hebrew — those languages keep full text translation. Chinese voices return
> when the upstream model ships them.

## Release notes (Play Console → release, en-US default)

> - All-new on-device voices: natural neural speech in 31 languages, powered
>   by Supertonic 3 — faster start-up and lower memory than the previous
>   voices.
> - Voice output is paused for Chinese, Catalan, Persian, and Hebrew (full
>   text translation unchanged). Chinese returns when the upstream model
>   ships it.
> - Translation, camera, and speech recognition still work fully offline
>   after model download.

## Support copy (FAQ / beta feedback auto-reply snippet)

> Versta 1.4 replaces its voices with Supertonic 3 running fully on-device.
> If a voice sounds wrong in your language, send us the language, the
> sentence, and whether it mispronounces or stutters. Voice output for
> Chinese, Catalan, Persian, and Hebrew is temporarily unavailable.

## Versioning

Ship as **1.4.0** (feature-level change: TTS stack replacement). The
`versionCode`/`versionName` bump rides the release PR, and
`app/src/main/play/release-notes/en-US/default.txt` is updated in the same
PR from the text above.
