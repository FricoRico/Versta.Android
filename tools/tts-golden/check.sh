#!/usr/bin/env bash
# Golden-audio gate for Supertonic 3 TTS (NEU-71).
#
# Synthesizes the corpus in app/src/test/resources/tts/golden/sentences.json
# through the native engine host build, then:
#   1. hash gate  — sha256 of each wav against tools/tts-golden/manifest.json
#                   (only when platform matches a blessed entry)
#   2. WER gate   — whisper.cpp round-trip transcription scored against the
#                   corpus text with the per-language thresholds in
#                   tools/tts-golden/thresholds.json
#
# Modes:
#   verify (default)  run both gates, exit non-zero on any failure
#   bless             synthesize and (re)write manifest.json; WER is reported
#                     but never fails the run — a human reviews the numbers and
#                     commits the manifest via PR
#
# Required tools on PATH (or via flags): the engine host CLI
# (SUPERTONIC_SYNTH), whisper-cli (whisper.cpp), jq, sha256sum, python3.
#
# Engine CLI contract (implemented by the native engine host build):
#   supertonic-synth --models <bundle dir> --config <tts.json> \
#       --style <voice_styles/F*.json> --lang <iso> --text <text> \
#       --seed <n> --speed 1.0 --threads <n> --out <out.wav>
# Output wav is PCM mono at 44.1 kHz (32-bit float or 16-bit; whisper-cli
# reads both).
set -euo pipefail

MODE="verify"
SYNTH="${SUPERTONIC_SYNTH:-supertonic-synth}"
WHISPER_CLI="${WHISPER_CLI:-whisper-cli}"
WHISPER_MODEL="${WHISPER_MODEL:-}"
BUNDLE=""
THREADS=4

usage() { sed -n '2,31p' "$0"; exit "${1:-0}"; }

while [ $# -gt 0 ]; do
    case "$1" in
        --bless) MODE="bless" ;;
        --synth) SYNTH="$2"; shift ;;
        --whisper-cli) WHISPER_CLI="$2"; shift ;;
        --whisper-model) WHISPER_MODEL="$2"; shift ;;
        --bundle) BUNDLE="$2"; shift ;;
        --threads) THREADS="$2"; shift ;;
        -h|--help) usage 0 ;;
        *) echo "unknown flag: $1" >&2; usage 1 ;;
    esac
    shift
done

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CORPUS="$ROOT/app/src/test/resources/tts/golden/sentences.json"
MANIFEST="$HERE/manifest.json"
THRESHOLDS="$HERE/thresholds.json"

[ -n "$BUNDLE" ] || { echo "error: --bundle <extracted supertonic3 bundle dir> is required" >&2; exit 2; }
[ -d "$BUNDLE" ] || { echo "error: bundle dir not found: $BUNDLE" >&2; exit 2; }
[ -n "$WHISPER_MODEL" ] || { echo "error: --whisper-model <ggml bin> is required" >&2; exit 2; }
if [ "$MODE" = "verify" ] && [ ! -f "$MANIFEST" ]; then
    echo "error: $MANIFEST missing — nothing blessed yet; see README.md" >&2; exit 2
fi
command -v "$SYNTH" >/dev/null || { echo "error: engine CLI '$SYNTH' not found (see README.md, Engine CLI)" >&2; exit 2; }
command -v "$WHISPER_CLI" >/dev/null || { echo "error: whisper-cli not found (see README.md, Whisper)" >&2; exit 2; }
command -v jq >/dev/null || { echo "error: jq is required" >&2; exit 2; }

for stage in duration_predictor text_encoder vector_estimator vocoder; do
    [ -f "$BUNDLE/$stage.mnn" ] || { echo "error: bundle is missing $stage.mnn" >&2; exit 2; }
done
[ -f "$BUNDLE/tts.json" ] || { echo "error: bundle is missing tts.json" >&2; exit 2; }
if [ "$MODE" = "bless" ] && [ ! -f "$MANIFEST" ]; then
    echo '{"version":1,"platforms":{}}' | jq . > "$MANIFEST"
fi

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

SEED="$(jq -r '.seed' "$CORPUS")"
PLATFORM="$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m)"

# Mirrors Supertonic3.defaultStyle(..., Female): the sorted language list spread
# over styles F1..F5. Keep in sync with core/entity/Supertonic3.kt.
style_for_language() {
    python3 - "$1" <<'PY'
import sys
languages = ["ar","bg","cs","da","de","el","en","es","et","fi","fr","hi","hr","hu",
             "id","it","ja","ko","lt","lv","nl","pl","pt","ro","ru","sk","sl","sv","tr","uk","vi"]
print(f"F{languages.index(sys.argv[1]) % 5 + 1}")
PY
}

RESULTS="$WORK_DIR/wer.jsonl"
HASHES="$WORK_DIR/hash.tsv"
: > "$RESULTS"
: > "$HASHES"

COUNT="$(jq '.cases | length' "$CORPUS")"
echo "tts-golden[$MODE]: $COUNT cases, seed=$SEED, threads=$THREADS, platform=$PLATFORM"

for i in $(seq 0 $((COUNT - 1))); do
    LANG="$(jq -r ".cases[$i].language" "$CORPUS")"
    TEXT="$(jq -r ".cases[$i].text" "$CORPUS")"
    STYLE="$(style_for_language "$LANG")"
    STYLE_FILE="$BUNDLE/voice_styles/$STYLE.json"
    [ -f "$STYLE_FILE" ] || { echo "error: $STYLE_FILE missing from bundle" >&2; exit 2; }

    WAV="$WORK_DIR/$LANG.wav"
    "$SYNTH" --models "$BUNDLE" --config "$BUNDLE/tts.json" --style "$STYLE_FILE" \
        --lang "$LANG" --text "$TEXT" --seed "$SEED" --speed 1.0 \
        --threads "$THREADS" --out "$WAV"
    SHA="$(sha256sum "$WAV" | cut -d' ' -f1)"

    HASH_STATUS="skipped"
    if [ "$MODE" = "verify" ]; then
        EXPECTED="$(jq -r --arg k "$PLATFORM" --arg l "$LANG" \
            '.platforms[$k].languages[$l].sha256 // empty' "$MANIFEST")"
        if [ -z "$EXPECTED" ]; then
            HASH_STATUS="skipped (no blessed entry for $PLATFORM)"
        elif [ "$EXPECTED" = "$SHA" ]; then
            HASH_STATUS="pass"
        else
            HASH_STATUS="FAIL"
        fi
    fi
    printf '%s\t%s\n' "$LANG" "$HASH_STATUS" >> "$HASHES"

    "$WHISPER_CLI" -m "$WHISPER_MODEL" -l "$LANG" -t "$THREADS" -np -nt \
        -f "$WAV" -otxt -of "$WORK_DIR/$LANG" >/dev/null

    python3 "$HERE/wer.py" --language "$LANG" --expected "$TEXT" \
        --transcript "$WORK_DIR/$LANG.txt" --thresholds "$THRESHOLDS" >> "$RESULTS"

    printf '%-3s hash=%-38s sha=%s\n' "$LANG" "$HASH_STATUS" "${SHA:0:16}…"

    if [ "$MODE" = "bless" ]; then
        TMP="$(mktemp)"
        jq --arg k "$PLATFORM" --arg l "$LANG" --arg sha "$SHA" \
           --argjson sr 44100 --argjson seed "$SEED" \
           --argjson threads "$THREADS" --arg style "$STYLE" '
           .platforms[$k].threads = $threads
           | .platforms[$k].seed = $seed
           | .platforms[$k].sampleRate = $sr
           | .platforms[$k].languages[$l] = {sha256: $sha, style: $style}
           ' "$MANIFEST" > "$TMP" && mv "$TMP" "$MANIFEST"
    fi
done

echo
python3 - "$RESULTS" "$HASHES" "$MODE" <<'PY'
import json, sys
rows = [json.loads(line) for line in open(sys.argv[1])]
hash_fail = False
for line in open(sys.argv[2]):
    lang, status = line.rstrip("\n").split("\t")
    if status == "FAIL":
        hash_fail = True
verify = sys.argv[3] == "verify"
fails = 0
for row in rows:
    status = "pass" if row["pass"] else "FAIL"
    if not row["pass"]:
        fails += 1
    print(f"{row['language']:>3}  {row['metric']}: {row['score']:.3f}  "
          f"threshold: {row['threshold']:.2f}  {status}")
print()
if verify:
    if fails or hash_fail:
        print(f"tts-golden: red — {fails} language(s) over WER threshold, "
              f"hash drift: {'yes' if hash_fail else 'no'}", file=sys.stderr)
        sys.exit(1)
    print("tts-golden: hash + WER gates green")
else:
    print(f"tts-golden bless: manifest written; {fails} language(s) over threshold "
          "(human review before committing the manifest)")
PY
