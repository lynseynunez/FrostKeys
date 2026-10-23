# Builds app/src/main/assets/typos.tsv (misspelling TAB correction) from codespell's dictionary
# (CC BY-SA 3.0, derived from English Wikipedia's lists of common misspellings):
#   curl -sSLO https://raw.githubusercontent.com/codespell-project/codespell/master/codespell_lib/data/dictionary.txt
#   python3 tools/make_typos.py dictionary.txt app/src/main/assets/words.txt app/src/main/assets/typos.tsv
import re, sys
dic, words, out = sys.argv[1:4]
freq = {}
for line in open(words, encoding="utf-8"):
    if line.startswith("#"): continue
    w, f = line.rstrip("\n").split("\t")
    freq[w.lower()] = int(f)
rows = {}
for line in open(dic, encoding="utf-8"):
    if "->" not in line: continue
    bad, good = line.rstrip("\n").split("->", 1)
    bad = bad.strip().lower()
    good = [g.strip() for g in good.split(",") if g.strip()]
    if len(good) != 1: continue          # ambiguous: more than one possible fix
    good = good[0].lower()
    if not re.fullmatch(r"[a-z]{3,16}", bad): continue
    if not re.fullmatch(r"[a-z' ]{2,24}", good): continue
    # The fix must be an ordinary word people use (or a short phrase of them).
    if any(freq.get(part, 0) < 300 for part in good.split(" ")): continue
    # Never "fix" something that is itself a common word.
    if freq.get(bad, 0) >= 1500: continue
    rows[bad] = good
with open(out, "w", encoding="utf-8") as f:
    f.write("# Misspellings from codespell's dictionary (CC BY-SA 3.0, derived from English Wikipedia)\n")
    for bad in sorted(rows): f.write(f"{bad}\t{rows[bad]}\n")
print(len(rows), "entries")
