# Glass and effect research

What effects exist in keyboards and in Apple's "Liquid Glass", what is possible on Android, and what would look
good in SakuriKey. Sources are blogs and developer write-ups, so treat the technical claims as approximate.
Written for the 2.0 roadmap.

## What exists

### Apple's Liquid Glass (the target look)
- A translucent material that **refracts** what is behind it, not just blurs it: the background bends along the
  glass edges, which gives depth between layers. Older frosted effects only scattered light.
  ([CreateWithSwift](https://www.createwithswift.com/exploring-a-new-visual-language-liquid-glass/),
  [GCN](https://gcn.com/?p=21100))
- **Specular highlights** that glint along curved edges and respond to device motion. Sources disagree on how
  often the motion response happens. ([GCN](https://gcn.com/?p=21100))
- Built from GPU shaders, lens-distortion curves, a blur layer and a motion-tied highlight.
- With Reduce Motion on, parallax and specular shifts are reduced or removed.
- A web write-up notes plain blur filters can't displace pixels, so true refraction needs a displacement
  approach. ([The Plus Addons](https://theplusaddons.com/blog/liquid-glass-ui/))

### Android: how the real thing is built
- Two primitives: a blur `RenderEffect` (API 31+) and a `RuntimeShader` written in AGSL (API 33+).
- The usual recipe: capture the backdrop, blur it, then run a refraction shader that offsets texture
  coordinates near the glass edge using a rounded-rectangle distance, a bezel height curve and an index of
  refraction, plus a rim highlight. Compile the shader once and reuse it, because recompiling per frame stutters.
  ([DEV Community](https://dev.to/wrack/liquid-glass-on-android-in-react-native-i-refracted-the-backdrop-with-a-real-time-agsl-shader-1dp4),
  [Abduzeedo](https://abduzeedo.com/node/88877), [KMPLiquidGlass](https://klibs.io/project/Kashif-E/KMPLiquidGlass))
- SakuriKey already has the blur layer, the specular sweep (tilt shimmer) and per-key glints. Refraction is the
  missing piece for the full effect.

### Keyboard apps
- **Samsung Keys Cafe:** keyboard color effects (Paint Gun, Firecracker), key color effects (Footprint, image
  or sticker as the effect), key motion (Tilt, Bounce).
- **Kibo (iOS):** about 15 lighting effects, including Reactive Glow, Key Spotlight, Ripple, Confetti Burst, Typing
  Trail, Rainbow Wave, Starlight and Meteor. ([listing](https://appgoblin.info/apps/6791442323))
- **Fleksy SDK:** key press tiles that change color on each press (rainbow) and on each swipe.
  ([docs](https://docs.fleksy.com/keyboard-sdk/guides/introduction-to-theming))
- **RGB keyboard apps:** neon and backlight animations; a trail mode that emits particles along the touch path
  and a feedback mode that bursts and falls with gravity.

## What SakuriKey has now
Typing animations: sparkles, ripple, warp speed, glow bloom, ink splash, firecracker, paint splatter.
Key motion: sink, pop, bounce, tilt, float. Press colors: white, accent, gradient, photo, rainbow.
Glass animations: aurora, light flow, bubbles (can follow tilt). Tilt shimmer, key glints, depth effect (optional).

## Ideas, ranked by how good they would look and how hard they are
| Idea | Look | Effort | Notes |
|---|---|---|---|
| **Liquid refraction** at key and sheet edges (AGSL) | Highest: the signature Liquid Glass look | L | API 33+ only; fall back to today's glass elsewhere. Cost per frame needs the performance budget. |
| **Typing trail** (soft glowing ribbon following the finger) | High | S | Short fading polyline in the accent or gradient colors. |
| **Key spotlight** (soft light pool that follows the pressed key to neighbors) | High | S | Glow that spreads to adjacent keys and fades. |
| **Rainbow wave** (color sweeps across the keys on a press) | Medium-high | S | Reuses the gradient colors. |
| **Starlight / fireflies** (twinkling points drifting in the glass) | Medium-high | S | A new glass animation next to bubbles. |
| **Water ripple / waves** background | Medium | M | Slow sine bands with a bright crest. |
| **Meteor** streak on fast typing | Medium | S | Variant of warp speed. |
| **Edge specular rim** (bright thin rim that shifts with tilt) | High, cheap | S | A cheap piece of the refraction look without the shader. |
| **Reduce motion** switch | Needed | S | Turns off sensor-driven and looping effects together. Apple does this too. |

## Status (built so far)
Key spotlight, typing trail, starlight, the Reduce motion switch and a first version of liquid refraction (Android 13+,
beta, off by default) are in the app. The edge specular rim was not requested. Refraction bends the blurred background
around every key with one shader pass over the sheet; it still needs real-phone checks on look and speed.

## Suggested next steps
1. **Edge specular rim** and **key spotlight**: cheap, work on every Android version, big visible gain.
2. **Typing trail** and **starlight**.
3. A **Reduce motion** master switch.
4. Prototype **liquid refraction** behind a toggle for Android 13+, measured against the performance budget,
   before committing to it.
