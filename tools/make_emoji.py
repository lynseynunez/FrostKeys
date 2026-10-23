# Regenerates app/src/main/assets/emoji.tsv from GitHub's gemoji database (MIT license):
#   curl -sSLO https://raw.githubusercontent.com/github/gemoji/master/db/emoji.json
#   python3 tools/make_emoji.py emoji.json app/src/main/assets/emoji.tsv
# Each line: emoji <TAB> 1 if it supports skin tones else 0 <TAB> space-separated keywords.
import json, re, sys
src, dst = sys.argv[1], sys.argv[2]
with open(dst, "w", encoding="utf-8") as out:
    out.write("# Emoji keywords from github/gemoji (MIT, Copyright (c) 2019 GitHub, Inc.)\n")
    for e in json.load(open(src, encoding="utf-8")):
        words = []
        for text in [e.get("description", "")] + e.get("aliases", []) + e.get("tags", []):
            for w in re.split(r"[\s_:,.()!&'\"-]+", text.lower()):
                if len(w) > 1 and w not in words:
                    words.append(w)
        out.write(f"{e['emoji']}\t{1 if e.get('skin_tones') else 0}\t{' '.join(words)}\n")
