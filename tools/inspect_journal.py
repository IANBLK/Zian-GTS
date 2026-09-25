#!/usr/bin/env python3
"""Read-only Zian GTS V2 WAL inspection. Never repairs, truncates or replays a journal."""
import argparse
import hashlib
import json
import pathlib
import struct
import sys

JOURNAL_VERSION = 2
MAX_FRAME = 8 * 1024 * 1024


def records(path):
    previous = bytes(32)
    sequence = 0
    with path.open("rb") as source:
        while True:
            header = source.read(4)
            if not header:
                return
            if len(header) != 4:
                raise ValueError("Truncated frame header")
            length, = struct.unpack(">i", header)
            if not 1 <= length <= MAX_FRAME:
                raise ValueError("Invalid V2 frame length")
            payload = source.read(length)
            checksum = source.read(32)
            if len(payload) != length or len(checksum) != 32:
                raise ValueError("Truncated frame")
            expected = hashlib.sha256(previous + payload).digest()
            if checksum != expected:
                raise ValueError("Checksum mismatch")
            event = json.loads(payload)
            sequence += 1
            if event["version"] != JOURNAL_VERSION or event["sequence"] != sequence:
                raise ValueError("Unsupported V2 journal version or sequence")
            previous = checksum
            yield event


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("journal", type=pathlib.Path)
    parser.add_argument("--export", type=pathlib.Path, help="New directory for verified V2 JSON records")
    args = parser.parse_args()
    if args.export:
        args.export.mkdir(parents=True, exist_ok=False)
    try:
        for event in records(args.journal):
            data = event["data"]
            print(event["sequence"], event["kind"], data.get("id", "-"),
                  data.get("operation", data.get("stage", "-")))
            if args.export:
                destination = args.export / f'{event["sequence"]:012d}.json'
                with destination.open("x", encoding="utf-8") as output:
                    json.dump(event, output, ensure_ascii=False, indent=2)
        return 0
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"STOP: {error}. Original journal was not modified; any export is only the verified prefix.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
