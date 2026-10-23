# Traces a small white-on-transparent icon into smooth vector outlines (Kotlin source):
#   python3 tools/make_emblem.py icon.png app/src/main/java/com/flexkeys/theme/EmblemShape.kt
# The alpha channel is upscaled and contoured with marching squares, then simplified.
import sys
import numpy as np
from PIL import Image

SCALE = 8
THRESHOLD = 0.5
TOLERANCE = 0.10  # in source pixels

def load(path):
    img = Image.open(path).convert("RGBA")
    a = img.getchannel("A").resize((img.width * SCALE, img.height * SCALE), Image.BICUBIC)
    f = np.asarray(a, dtype=np.float64) / 255.0
    return np.pad(f, 1), img.width, img.height  # zero border closes every contour

def contours(f):
    H, W = f.shape
    def edge_point(kind, i, j):
        # kind 'h': between (i,j) and (i,j+1); 'v': between (i,j) and (i+1,j)
        if kind == 'h':
            a, b = f[i, j], f[i, j + 1]
            t = (THRESHOLD - a) / (b - a)
            return (j + t, i)
        a, b = f[i, j], f[i + 1, j]
        t = (THRESHOLD - a) / (b - a)
        return (j, i + t)
    segs = {}
    def add(e1, e2):
        segs.setdefault(e1, []).append(e2)
        segs.setdefault(e2, []).append(e1)
    for i in range(H - 1):
        for j in range(W - 1):
            tl, tr, br, bl = f[i, j] >= THRESHOLD, f[i, j + 1] >= THRESHOLD, f[i + 1, j + 1] >= THRESHOLD, f[i + 1, j] >= THRESHOLD
            top, right, bottom, left = ('h', i, j), ('v', i, j + 1), ('h', i + 1, j), ('v', i, j)
            case = (tl << 3) | (tr << 2) | (br << 1) | bl
            if case in (0, 15): continue
            table = {1: [(left, bottom)], 2: [(bottom, right)], 3: [(left, right)], 4: [(top, right)],
                     6: [(top, bottom)], 7: [(left, top)], 8: [(left, top)], 9: [(top, bottom)],
                     11: [(top, right)], 12: [(left, right)], 13: [(bottom, right)], 14: [(left, bottom)]}
            if case in table:
                for a, b in table[case]: add(a, b)
            else:  # saddles 5 and 10
                center = (f[i, j] + f[i, j + 1] + f[i + 1, j + 1] + f[i + 1, j]) / 4 >= THRESHOLD
                if case == 5:
                    pairs = [(left, top), (bottom, right)] if center else [(left, bottom), (top, right)]
                else:
                    pairs = [(left, bottom), (top, right)] if center else [(left, top), (bottom, right)]
                for a, b in pairs: add(a, b)
    loops, seen = [], set()
    for start in segs:
        if start in seen: continue
        loop, prev, cur = [], None, start
        while cur not in seen:
            seen.add(cur); loop.append(edge_point(*cur))
            nxt = [n for n in segs[cur] if n != prev and n not in seen]
            if not nxt: break
            prev, cur = cur, nxt[0]
        if len(loop) > 8: loops.append(loop)
    return loops

def simplify(points, tol):
    pts = np.array(points)
    def dp(lo, hi):
        if hi <= lo + 1: return [lo]
        a, b = pts[lo], pts[hi]
        d = b - a; n = np.hypot(*d)
        seg = pts[lo + 1:hi] - a
        dist = np.abs(seg[:, 0] * d[1] - seg[:, 1] * d[0]) / n if n > 0 else np.hypot(seg[:, 0], seg[:, 1])
        k = int(np.argmax(dist))
        if dist[k] > tol:
            m = lo + 1 + k
            return dp(lo, m) + dp(m, hi)
        return [lo]
    idx = dp(0, len(pts) - 1) + [len(pts) - 1]
    return pts[idx]

src, dst = sys.argv[1], sys.argv[2]
f, w, h = load(src)
out = []
for loop in contours(f):
    # back to source pixel units (undo the pad and the upscale), then normalize to 0..1
    p = [((x - 1 + 0.5) / SCALE / w, (y - 1 + 0.5) / SCALE / h) for x, y in loop]
    s = simplify([(x * w, y * h) for x, y in p], TOLERANCE)
    out.append([(x / w, y / h) for x, y in s])
with open(dst, "w") as k:
    k.write("package com.flexkeys.theme\n\n")
    k.write("/** Vector outlines of the space bar emblem, traced from a 24px icon by tools/make_emblem.py. Even-odd fill, coordinates 0..1. */\n")
    k.write("object EmblemShape {\n    val loops: Array<FloatArray> = arrayOf(\n")
    for loop in out:
        flat = ", ".join(f"{x:.4f}f, {y:.4f}f" for x, y in loop)
        k.write(f"        floatArrayOf({flat}),\n")
    k.write("    )\n}\n")
print(len(out), "loops,", sum(len(l) for l in out), "points")
