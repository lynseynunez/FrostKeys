# SakuriKey 2.0 roadmap

SakuriKey is a personal keyboard for the owner's own devices, including a tablet. Nothing here assumes
publishing it. Effort: **S** a session or less, **M** a few sessions, **L** a big project.

Status: `[ ]` not started, `[~]` in progress, `[x]` done.

## Milestone 1: Foundation (done)

Make what exists solid and measurable before adding more.

Result: 25 unit tests run in CI before every build; a code review pass fixed nine issues; suggestions are about
three times faster; Settings > Diagnostics records key timing against a budget (key median under 4 ms, 95th under
12 ms, touch wait 95th under 20 ms, under 10% slow frames); backups include stickers and are checked before anything
is changed; releases get automatic notes and a signing-key script.

- [x] **Test pass and polish (M).** Run a full code review, fix what turns up, and add unit tests for
  autocorrect, layouts, backups and the clipboard. The autocorrect desktop harness used during tuning becomes a
  real test with a fixed list of typos and a list of words that must never change.
- [x] **Local diagnostics (S).** An optional log, kept only on the phone, of key-to-screen timing and crashes,
  with a Settings screen to view and clear it. Turns "it lags" into numbers.
- [x] **Performance budget (M).** Measure the cost of a key press with blur, glass animation, tilt and typing
  effects on, set targets (for example, under one frame of extra work per key), and fix what exceeds them.
- [x] **Backup completeness (S).** Include stickers, the autocorrect list and the top-row order. Check that a
  restore on a fresh install brings back everything except API keys.
- [x] **Real release pipeline (S).** A private signing key kept in the repository secrets, version notes written
  for each release, and the in-app update check showing them.

## Milestone 2: Typing intelligence

The biggest everyday improvement.

- [x] **Context-aware autocorrect (L).** *Done: about 80,000 common word pairs rank suggestions by the previous word, fix mix-ups like "away form", and improve next-word prediction. Checked by tests on ordinary messages so correct text is left alone.*
  ("form" for "from", "wnat" for "want") is resolved by the previous word. Needs a bundled, permissively licensed
  pair table and a size budget for the APK. Measured against the typo test list from Milestone 1.
- [x] **Bigger vocabulary (M).** A tech, app and texting-slang list, plus names the user types often, so real
  words are not treated as mistakes.
- [x] **Smarter capitalization and punctuation (S).** "i" to "I", sentence-start rules, and edge cases around
  quotes, parentheses and abbreviations.

## Milestone 3: Look and feel

- [-] **Per-key glints on tilt (S).** *Built, then removed at the owner's request.*
- [ ] **More glass animations and effects (S each).** New typing effects and glass animations in the spirit of
  the Keys Cafe ones, chosen after trying the current set. A place to try ideas quickly.
- [-] **Depth effect (L).** *Built as an optional toggle with ML Kit, then removed at the owner's request (it also removed the only library).*

## Milestone 4: AI features

All use the user's own free API key (Groq or Gemini), are off by default, and are disabled in password fields.

- [x] **AI reply helper (S).** Write a reply to text the user copied, in a chosen tone.
- [x] **Voice cleanup (S).** After dictating, tidy punctuation and remove filler words.
- [x] **Editable rewrite presets (S).** The user's own styles, such as "make it sound like me".
- [x] **Smarter emoji suggestions (M).** Suggest emoji for a whole sentence, not a single word.

## Milestone 5: Productivity

- [x] **Text tools (S).** Change case, remove extra spaces, turn lines into a list, from the selection.
- [x] **Snippet variables (S).** Phrases that can include today's date, the time or the clipboard.

## Milestone 6: Platform

- [~] **Tablet and foldable layouts (L).** *First version done: keyboard width (auto narrower on tablets, wide, medium, compact), position (center, left, right) and split keys with an adjustable gap. Floating mode and foldable posture handling are still to do.* Split keyboard and floating mode, with sizing that suits a tablet.
  Needs testing on the owner's tablet.
- [x] **Android version coverage (M).** *Lint now runs in CI: 0 errors and no calls that need a newer Android than the minimum (26). Not yet tried on a real older phone.* Check the older-Android fallbacks (blur, the glass sheet, sensors,
  window setup) so nothing breaks off the newest version.

## Suggested order

1. Milestone 1, so everything after it can be measured.
2. Milestone 2, starting with the bigger vocabulary and then the context model.
3. Milestone 6 (tablet) in parallel with Milestone 3 once the owner has the tablet to test on.
4. Milestones 4 and 5 are small and can be added between larger items.

## Considered and not planned

Multiple languages, per-app profiles, an incognito toggle, theme packs with the photo, adaptive theming by time
of day, accessibility modes, Play Store publishing. Any of these can be added back later.
