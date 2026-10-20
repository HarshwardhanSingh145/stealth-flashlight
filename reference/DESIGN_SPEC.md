# Reference UI Spec — measured from `flashlight-ui-reference.mp4`

Source: 720×1280, 25 fps, 11.05 s. A 3D camera orbits a pill toggle, then settles head-on
(last ~3 s). The head-on composition is the UI to reproduce. The clip alternates the two
switch states, so both are captured.

## Composition (head-on, 720×1280 → fractions of screen)

| Element | Measured | Fraction |
| --- | --- | --- |
| Pill horizontal extent | x 148…561 | width 57.4 % of screen |
| Pill vertical extent | y 138…1087 | height 74.1 % of screen |
| Pill centre x | 354 | 49.2 % (dot column occupies the right gutter) |
| Corner radius | = pill width / 2 | full capsule |
| Knob height | 474 | exactly half the pill |
| Dot column centre x | 553 | 76.8 % |
| Dot column extent | y 305…975 | 18 dots, ⌀≈18 px, pitch ≈39 px |

## Switch semantics

The pill always shows two fixed labels: **OFF** in the top half, **ON** in the bottom half.
A single capsule knob (half the pill height) slides between them.

- **OFF state** — knob rests at the **bottom**, background light, no neon.
- **ON state** — knob slides to the **top**, background near-black, purple neon rim.

So sliding the knob **up turns the torch on**. The knob carries the label of the half it
occupies, which reads as the *next action*; the exposed half shows the *current state* in
the bright colour. Verified: in the ON frame the bottom "ON" is bright white and the knob's
"OFF" is dim grey; in the OFF frame the top "OFF" is bright white and the knob's "ON" is
dark grey.

## Palette (sampled)

OFF / light state — neumorphic clay:

```
background      #C0C0C0   (flat, soft ambient occlusion under the pill)
track (top)     #C7C7C9
knob (bottom)   #A8A8AA
knob rim        #B49BD8   (thin lavender light under the knob only)
label active    #FFFFFF
label inactive  #4B4B4D
dot idle        #A9AEC4   (blue-grey, ~55 % alpha)
dot lit         #FFFFFF
caption         #6E6E70
```

ON / dark state — neon:

```
background      #0B0B0D   with purple bloom #1A1024 toward the top corners
knob            #141416
track           #0A0A0C
neon core       #C77DFF
neon mid        #A855F7
neon outer      #7C2FD1
neon bloom      #6A1FB8
label active    #FFFFFF
label inactive  #6A6A6E
dot lit         #E9D5FF / #FFFFFF
```

Neon is a tight ~4 px core stroke plus widening, fading bloom passes. Reproduced with
concentric strokes rather than a blur render effect so it works below API 31.

## Dot column = hold progress

Across the settled frames the column fills **bottom → top**: 5 dots lit at one moment, 6
the next, 15 later. It is the only progress affordance in the design, so it is reused for
the 5-second unlock gesture rather than inventing a new ring or bar.

## Side captions

The column is bracketed by two short captions, top and bottom. In the source they are
cropped by the video framing and only ever read `ER` and `SÜ`, so the intended words are
not recoverable. Implemented as `HOLD` (top) and `5S` (bottom) in the same size, weight and
colour slots — the only place where the reference could not be reproduced verbatim.

## Typography

Geometric sans, medium weight, generous tracking. OFF/ON render at roughly 1/8 of the pill
width. Captions are smaller and lighter. No font file ships with the clip, so the system
sans-serif is used with matching weight and letter spacing.
