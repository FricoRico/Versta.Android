#!/usr/bin/env python3
"""Export LFM2.5 tokenizer.json to a single unified binary format for Android."""

import json
import struct
import io
from pathlib import Path
import sys

def bytes_to_unicode():
    """GPT-2 byte-to-unicode mapping used by ByteLevel pre-tokenizers."""
    bs = (
        list(range(ord("!"), ord("~") + 1))
        + list(range(ord("¡"), ord("¬") + 1))
        + list(range(ord("®"), ord("ÿ") + 1))
    )
    cs = bs[:]
    n = 0
    for b in range(2**8):
        if b not in bs:
            bs.append(b)
            cs.append(2**8 + n)
            n += 1
    cs = [chr(c) for c in cs]
    return dict(zip(bs, cs))


def export_vocab(vocab: dict, f):
    """Write vocab: null-terminated UTF-8 string followed by 4-byte LE length."""
    count = 0
    for token_str, token_id in sorted(vocab.items(), key=lambda x: x[1]):
        encoded = token_str.encode("utf-8")
        f.write(encoded)
        f.write(b"\x00")
        f.write(struct.pack("<i", len(encoded)))
        count += 1
    return count


def export_merges(vocab: dict, merges: list, f):
    """Write merges: each rule is 3 × int32 LE (left_id, right_id, merged_id)."""
    count = 0
    for left_str, right_str in merges:
        left_id = vocab[left_str]
        right_id = vocab[right_str]
        merged_str = left_str + right_str
        merged_id = vocab[merged_str]
        f.write(struct.pack("<iii", left_id, right_id, merged_id))
        count += 1
    return count


def export_special_tokens(added_tokens: list, f):
    """Write special tokens: int32 ID + null-terminated string + int32 length + int32 is_special."""
    count = 0
    for token in added_tokens:
        token_id = token["id"]
        token_str = token["content"]
        encoded = token_str.encode("utf-8")
        f.write(struct.pack("<i", token_id))
        f.write(encoded)
        f.write(b"\x00")
        f.write(struct.pack("<i", len(encoded)))
        f.write(struct.pack("<i", 1 if token.get("special", False) else 0))
        count += 1
    return count


def export_byte_to_id(vocab: dict, f):
    """Write 256 int32 mappings for bytes 0-255."""
    b2u = bytes_to_unicode()
    count = 0
    for b in range(256):
        unicode_char = b2u[b]
        token_id = vocab.get(unicode_char, -1)
        f.write(struct.pack("<i", token_id))
        count += 1
    return count


def main():
    if len(sys.argv) < 2:
        print("Usage: python export_tokenizer.py <tokenizer.json> [output_dir]")
        sys.exit(1)

    tokenizer_path = Path(sys.argv[1])
    output_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else tokenizer_path.parent
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Reading {tokenizer_path}...")
    with open(tokenizer_path) as f:
        data = json.load(f)

    vocab = data["model"]["vocab"]
    merges = data["model"]["merges"]
    added_tokens = data["added_tokens"]

    output_file = output_dir / "tokenizer.bin"
    
    print(f"Exporting unified tokenizer to {output_file}...")
    
    # Use BytesIO to build the segments in memory to calculate exact offsets/sizes
    vocab_buf = io.BytesIO()
    v_count = export_vocab(vocab, vocab_buf)
    
    merges_buf = io.BytesIO()
    m_count = export_merges(vocab, merges, merges_buf)
    
    special_buf = io.BytesIO()
    s_count = export_special_tokens(added_tokens, special_buf)
    
    byte_buf = io.BytesIO()
    b_count = export_byte_to_id(vocab, byte_buf)

    # Header: Magic (4) + 4 x Int32 (counts)
    # Total Header Size = 4 + (4*4) = 20 bytes
    
    with open(output_file, "wb") as f:
        f.write(b"LFM2")
        f.write(struct.pack("<iiii", v_count, m_count, s_count, b_count))
        f.write(vocab_buf.getvalue())
        f.write(merges_buf.getvalue())
        f.write(special_buf.getvalue())
        f.write(byte_buf.getvalue())

    print(f"Done. Exported {v_count} vocab items, {m_count} merges, {s_count} special tokens.")
    print(f"Total size: {output_file.stat().st_size} bytes.")

if __name__ == "__main__":
    main()
