#!/usr/bin/env python3
"""Score one golden-audio case against its corpus text.

Prints one JSON line: {"language", "metric", "score", "threshold", "pass"}.

Metric is word error rate for space-delimited orthographies and character
error rate for Japanese (whisper emits ja without spaces, so a word-level
metric would be meaningless). The intent is a smoke signal that the engine
still renders intelligible speech in each language, not a perceptual MOS.
"""

import argparse
import json
import unicodedata

CER_LANGUAGES = {"ja"}


def normalize(text):
    text = unicodedata.normalize("NFKC", text).casefold()
    chars = []
    for ch in text:
        if unicodedata.category(ch).startswith(("P", "S", "Z", "C")):
            chars.append(" ")
        else:
            chars.append(ch)
    return " ".join("".join(chars).split())


def edit_distance(a, b):
    if not a:
        return len(b)
    if not b:
        return len(a)
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--language", required=True)
    parser.add_argument("--expected", required=True)
    parser.add_argument("--transcript", required=True)
    parser.add_argument("--thresholds", required=True)
    args = parser.parse_args()

    with open(args.thresholds) as fh:
        thresholds = json.load(fh)
    entry = thresholds["languages"].get(args.language) or thresholds["defaults"]["cer" if args.language in CER_LANGUAGES else "wer"]

    expected = normalize(args.expected)
    with open(args.transcript) as fh:
        transcript = normalize(fh.read())

    if args.language in CER_LANGUAGES:
        ref = expected.replace(" ", "")
        hyp = transcript.replace(" ", "")
        score = edit_distance(ref, hyp) / max(len(ref), 1)
        metric = "cer"
    else:
        ref = expected.split()
        hyp = transcript.split()
        score = edit_distance(ref, hyp) / max(len(ref), 1)
        metric = "wer"

    threshold = float(entry["threshold"])
    print(json.dumps({
        "language": args.language,
        "metric": metric,
        "score": score,
        "threshold": threshold,
        "pass": score <= threshold,
    }))


if __name__ == "__main__":
    main()
