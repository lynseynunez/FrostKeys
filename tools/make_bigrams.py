# Builds app/src/main/assets/bigrams.txt (word pairs with counts) from SymSpell's bigram list:
#   pip download symspellpy --no-deps -d <folder>, unzip the wheel, then
#   python3 tools/make_bigrams.py <path to frequency_bigramdictionary_en_243_342.txt> \
#       app/src/main/assets/words.txt app/src/main/assets/extra_words.txt app/src/main/assets/bigrams.txt
# Only pairs whose two words are both in the keyboard's own word list are kept, and only the most common ones.
# Source data: SymSpell (MIT), derived from Google Books Ngram data (CC BY 3.0).
import sys

src, words_path, extra_path, dst = sys.argv[1:5]
LIMIT = 80000

known = set()
for path in (words_path, extra_path):
    for line in open(path, encoding='utf-8'):
        if line.startswith('#') or '\t' not in line:
            continue
        known.add(line.split('\t', 1)[0].lower())

pairs = []
for line in open(src, encoding='utf-8'):
    parts = line.split()
    if len(parts) != 3:
        continue
    a, b, c = parts
    if a in known and b in known and a.isalpha() and b.isalpha():
        pairs.append((int(c), a, b))
pairs.sort(reverse=True)
pairs = pairs[:LIMIT]
with open(dst, 'w', encoding='utf-8') as out:
    out.write('# Word pairs: first word, second word, count. Derived from SymSpell (MIT) / Google Books Ngrams (CC BY 3.0).\n')
    for c, a, b in pairs:
        out.write(f'{a} {b} {c}\n')
print(len(pairs), 'pairs; lowest count', pairs[-1][0])
