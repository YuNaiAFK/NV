"""Convert EhPanda's zh-Hans/zh-Hant release JSONs to NVbook's display-only TSV."""

import argparse
import html
import json
import re
from collections import defaultdict
from pathlib import Path


WIKI_TYPES = {
    "artist": "artist", "character": "character", "group": "group",
    "parody": "parody", "language": "language", "reclass": "category",
    "female": "tag", "male": "tag", "mixed": "tag", "other": "tag",
}
IMAGE = re.compile(r"!\[[^\]]*\]\([^)]*\)")
LINK = re.compile(r"\[([^\]]+)\]\([^)]*\)")


def plain_name(value):
    value = IMAGE.sub("", value)
    value = LINK.sub(r"\1", value)
    value = " ".join(html.unescape(value).split())
    # Skip unsupported markup, rather than displaying fragmentary markdown.
    return "" if any(mark in value for mark in ("![", "](", "://", "<", ">")) else value


def read_names(path):
    candidates = defaultdict(set)
    for group in json.loads(path.read_text(encoding="utf-8"))["data"]:
        kind = WIKI_TYPES.get(group["namespace"])
        if not kind:
            continue
        for key, entry in group["data"].items():
            name = plain_name(entry.get("name") or "")
            if name and name.casefold() != key.casefold():
                # nhentai does not distinguish female/male/mixed/other.
                candidates[kind, key.lower()].add(name)
    return {key: next(iter(names)) for key, names in candidates.items() if len(names) == 1}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("hans", type=Path)
    parser.add_argument("hant", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    hans, hant = read_names(args.hans), read_names(args.hant)
    keys = sorted(hans.keys() | hant.keys())
    with args.output.open("w", encoding="utf-8", newline="\n") as out:
        for kind, key in keys:
            if any(char in key for char in "\t\r\n"):
                raise ValueError(f"invalid key: {key!r}")
            out.write("\t".join((kind, key, hans.get((kind, key), ""), hant.get((kind, key), ""))) + "\n")
    print(f"Wrote {len(keys)} names")


if __name__ == "__main__":
    main()
