# Regenerates app/src/main/assets/words.txt from FrequencyWords en_50k.txt:
#   python3 tools/make_words.py en_50k.txt app/src/main/assets/words.txt
import re, sys
src, dst = sys.argv[1], sys.argv[2]
freq = {}
for line in open(src, encoding='utf-8'):
    parts = line.split()
    if len(parts) != 2: continue
    w, f = parts[0], int(parts[1])
    if not re.fullmatch(r"[a-z]+", w): continue
    freq[w] = freq.get(w, 0) + f
stems = "don didn isn doesn wasn aren couldn wouldn shouldn haven hasn weren hadn ain mustn needn".split()
for s in stems: freq.pop(s, None)
# The corpus also has contractions with the apostrophe dropped; keep only the real forms below.
no_apostrophe = """dont im youre didnt doesnt isnt wasnt arent couldnt wouldnt shouldnt havent hasnt werent hadnt
thats whats theyre youll youve weve theyve hes shes ive itll thatll wheres whos hows heres aint yall
wouldve couldve shouldve mustnt neednt wont cant""".split()
for s in no_apostrophe: freq.pop(s, None)
for w in list(freq):
    if len(w) == 1 and w not in ("a", "i"): del freq[w]
blocked = set("""fuck fucking fucked fucker fuckin motherfucker shit shitty bitch bitches cunt dick dicks cock cocks pussy
asshole assholes bastard slut sluts whore whores nigger nigga niggers faggot faggots fag retard retarded twat wanker
dildo tits titties boobs cum jizz porn""".split())
for w in blocked: freq.pop(w, None)
contractions = {
 "don't": 4100000, "I'm": 4300000, "it's": 3000000, "that's": 1500000, "you're": 1200000, "can't": 1100000,
 "didn't": 1100000, "I'll": 900000, "we're": 800000, "I've": 700000, "he's": 700000, "there's": 600000,
 "won't": 500000, "isn't": 430000, "what's": 700000, "let's": 600000, "I'd": 500000, "doesn't": 400000,
 "you'll": 300000, "they're": 400000, "she's": 400000, "we'll": 250000, "wasn't": 300000, "you've": 250000,
 "aren't": 200000, "couldn't": 200000, "wouldn't": 200000, "haven't": 200000, "we've": 150000,
 "shouldn't": 80000, "they'll": 60000, "he'll": 60000, "she'll": 40000, "it'll": 60000, "you'd": 120000,
 "they've": 60000, "hasn't": 60000, "weren't": 50000, "who's": 120000, "where's": 120000, "how's": 40000,
 "here's": 100000, "hadn't": 40000, "ain't": 100000, "y'all": 20000, "we'd": 60000, "he'd": 70000,
 "she'd": 40000, "they'd": 40000, "that'll": 30000, "mustn't": 5000, "needn't": 3000,
}
for w, f in contractions.items():
    freq[w] = f
freq.pop("i", None)
freq["I"] = 27086011
top = sorted(freq.items(), key=lambda kv: -kv[1])[:40000]
with open(dst, "w", encoding="utf-8") as out:
    out.write("# English word frequencies derived from hermitdave/FrequencyWords (OpenSubtitles 2018, CC-BY-SA-4.0)\n")
    for w, f in top:
        out.write(f"{w}\t{f}\n")
print(len(top))
