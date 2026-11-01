# Production imagery brief

Status: brief only, 2026-11-01. No image has been generated yet, and nothing in this document goes into
the app until the owner approves it.

## Why this exists

The approved designs in `docs/design/approved/` use 20 recovered images (`source/assets/`). The owner
decided (**R4**) that they are **reference only**. They define each image's role and art direction, but
they must not ship. This applies to the images and to anything closely derived from them.

The app needs new, original images that keep the approved art direction:
- editorial gift photography;
- a warm, tactile mood;
- the same crop logic, palette relationship and lighting;
- the same negative space;
- the same cut-out treatment.

Every new image must:
- not be a pixel copy, trace, retouch or near-duplicate of a recovered image;
- contain no identifiable people, logos, brand names, readable text or watermarks;
- not reuse a recovered image's exact object arrangement or distinctive background;
- be approved by the owner before it is committed.

The 20 recovered images fall into three groups:

| Group | What | Count | Action |
|---|---|---|---|
| **1** | Bundled editorial and decorative images, part of the app's look | 4 roles, from 4 recovered files | **Generate new originals** (see below) |
| **2** | Example people and gifts that stand in for user content on the boards | 16 recovered files | **Do not bundle.** At runtime the user's photo or a designed fallback takes their place |
| **3** | User-taken or user-selected photos | — | No asset needed. The user supplies them at runtime |

## Shared art direction (all group 1 images)

These come from DS-06 ("photography, not illustration") and the recovered references.

- **Subject:** wrapped gifts and gift-making materials, photographed as real objects:
  - papers (kraft, matte white, soft pastel, a little pattern);
  - fabric or grosgrain ribbons and bows;
  - tissue, tags without writing, raffia.
  
  Gifts are the subject. A person may appear as a hand at most.
- **Mood:** warm, joyful and calm, never busy. It should feel like a keepsake, not a sale.
- **Light:** soft and even, like a studio or a large window. Low contrast, gentle shadows, no hard
  flash and no dramatic rim light. Colour temperature neutral to slightly warm.
- **Palette relationship:** the photographs sit next to these tokens:
  - ivory `#FBF7EE`;
  - parchment `#F4EEDF`;
  - dark teal `#17423E`;
  - aqua `#4FD6C2` / `#BFEAE3`;
  - coral `#D6285A`;
  - the hero yellow `#E3A73A`.
  
  Ribbons and papers should pick up aqua, coral and warm yellow, with white and kraft as neutrals.
  Navy, teal or green may appear as small accents. Avoid neon, pure black and cold blue-grey casts.
- **Materials:** paper grain and ribbon weave should be visible. They should read as tactile, not
  rendered or plastic. No 3D-render or CGI look, no illustration style, no AI artefacts:
  - no merged ribbons or impossible bows;
  - no melted edges;
  - no extra fingers.
- **Never:**
  - faces or identifiable people;
  - jewellery, tattoos, nail art or watches on hands;
  - logos, brand marks or product names;
  - readable or pseudo-text on tags, tissue, boxes or backgrounds;
  - seasonal clichés that date the image (no Christmas trees, no birthday numbers).
- **Process:**
  - For each role, generate several candidates.
  - The owner picks one, and it is checked side by side against the reference for "same role, not
    the same picture".
  - Only then is it exported and committed.
  - Keep the prompt and the generator's terms with the approval record, so origin is documented.

## Group 1: bundled images to generate (4)

All four go in `giftiary-android/app/src/main/res/drawable-nodpi/` as WebP, with names to be decided by
the implementing phase. A single high-resolution file per image is enough. The size is set by the
largest phone use at xxxhdpi (4×), and Coil/Compose scale it down.

### G1-1. Home editorial hero

| | |
|---|---|
| Used by | Home (`phone-home`). Also under the Quick Add sheet (`phone-quick-add-sheet`), on Home offline (`phone-state-home-offline`), and as the Level 1 example in DS-06 |
| Reference | `fefbf3ac….jpg` (780×672) |
| Type | **Hero / editorial** (DS-06 Level 1) |
| UI purpose | The brand moment at the top of Home. The "GIFTY" wordmark, the Lora headline "Who are we making happy next?", the date, the Caveat line and the profile avatar are set **directly on the image** |
| Aspect and crop | Full-bleed width × 336 dp on a 390 dp phone (about 1.16 : 1), `cover`, centred, with a 32 dp bottom radius. DS-06 shows the same image at 230 dp, so the subject must survive a much shorter crop |
| Composition and negative space | A flat, warm yellow seamless ground close to `#E3A73A` (the `#E3A73A` fill is also the loading fallback, so the photo must blend into it). The subject sits in the **right third and lower two thirds**. The **left half and top band stay calm, empty ground** for white text, which must pass contrast on it. Nothing important within 32 dp of the bottom corners |
| Subject, mood, light | A small stack or cluster of wrapped gifts, optionally held or offered by a hand entering from the side. The gesture is giving. Soft, even studio light |
| May vary | The number, sizes and wrapping of the gifts; whether a hand appears; the side it enters from (only if the subject stays on the right); ribbon colours within the palette; slight angle |
| Must not copy | The exact stack: four boxes of kraft with an aqua bow, white striped, light blue with an orange-red ribbon, and royal blue with multicolour polka dots, plus the pink card tucked between them. Also: the outstretched forearm from the bottom right with ring and nail polish, and the denim sleeve |
| User photo supersedes? | No. It is decorative brand imagery and the same for every user |
| Output | One master at **1600 × 1380 px**, WebP lossy (quality about 85), no alpha. The master must also allow the G1-2 crop if the owner wants one shoot for both (see G1-2) |

### G1-2. Onboarding "Share" image

| | |
|---|---|
| Used by | Onboarding chapter 3, Share (`phone-onboarding-3-share`) |
| Reference | `950fee72….jpg` (554×554). It is a squarer crop of the same photograph as G1-1 |
| Type | **Hero / editorial** (Level 1, onboarding) |
| UI purpose | The illustration for "Share": giving and receiving between people |
| Aspect and crop | Full width × at least 440 dp (flexes taller), `cover`, positioned `center 86%` so the bottom of the subject stays in frame, with a 32 dp bottom radius. Portrait-leaning, about 4 : 5 or taller |
| Composition and negative space | The subject **centred**, with clear ground above for the crop to fall into. The background fallback is parchment `#F4EEDF` |
| Subject, mood, light | As G1-1: gifts being handed over, warm and generous |
| May vary | It can be a second crop of the G1-1 master, as in the reference, or its own image from the same shoot. The owner chooses; a second crop is the default because the reference does that |
| Must not copy | As G1-1 |
| User photo supersedes? | No |
| Output | **1440 × 1800 px**, WebP lossy (quality about 85), no alpha |

### G1-3. Large supporting gift cut-out

| | |
|---|---|
| Used by | Corner decoration, top right, at 0.40–0.50 opacity and 200–250 dp wide, bleeding off the right edge, on: Add gift quick and details, Add gift idea, Add/Edit person, Add/Edit wish, Sign in, and DS-06 Level 2. At **full opacity and 566 dp wide** it is also the Onboarding "Remember" image ("Gifts that were given, kept together") |
| Reference | `e4a6b9dd….png` (808×810, RGBA) |
| Type | **Supporting cut-out** (DS-06 Level 2). It is aria-hidden everywhere except onboarding |
| UI purpose | Warmth on creation and account screens without competing with the form. On onboarding, the chapter image for "Remember" |
| Aspect and crop | About 1 : 1. Placed `width` × `auto` with no crop; up to 40% of it falls outside the screen |
| Composition and negative space | One taller gift as the hero object, with a few smaller wrapped gifts behind or beside it, filling the frame. **Cut-out treatment:** the object edge on the right and top is clean, while the left and bottom dissolve into transparency through a soft, feathered fade. No hard outline, no drop shadow, no background plate. It must read well at 0.40 opacity on ivory, so it needs mid-tone mass and not just pale highlights |
| Subject, mood, light | A collection of kept gifts. Light matte white or pale papers with a few saturated ribbons in palette colours. Soft top-left light |
| May vary | Which objects, how many, the paper pattern, the ribbon colours, the exact arrangement |
| Must not copy | The tall white box with gold confetti dots and a navy grosgrain bow, set against the smaller dotted boxes with green, pink and orange ribbons. Also: the faint printed lettering on the tissue at lower left. The new image must have no text at all |
| User photo supersedes? | No |
| Output | **1600 × 1600 px**, WebP **with alpha** (lossy with alpha is acceptable at quality about 90; check the feathered edge for banding) |

### G1-4. Small supporting gift cut-out

| | |
|---|---|
| Used by | Second corner decoration, at 0.26–0.42 opacity and 180–300 dp wide: bottom left on Add gift quick and details and Sign in; top right on Create list and Profile. **At full opacity, cropped:** the 128 dp circular People-empty mark (`phone-state-people-empty`, DS-05, `object-position 54% 46%`), and the Onboarding "Plan" image, a 344 dp-wide pill clipped on the left (`object-position 58% 42%`, "A gift set aside for an occasion still to come") |
| Reference | `9c1d16f8….png` (345×345, RGBA) |
| Type | **Supporting cut-out** (Level 2), also used as an **empty-state mark** and an onboarding image |
| UI purpose | A quieter second accent. As the empty-state mark, a soft object that says "nothing here yet, and that's fine" |
| Aspect and crop | 1 : 1. It must survive three uses: uncropped at low opacity, a **tight circular centre crop** (the main object must sit in the centre 60%), and a tall rounded pill crop |
| Composition and negative space | One or two small wrapped gifts, the main one near the centre and slightly low. Same feathered cut-out treatment as G1-3, fading on the outer edges |
| Subject, mood, light | A single gift set aside and waiting. Calmer than G1-3, with one accent ribbon (coral/magenta or aqua family) on light paper |
| May vary | Object, ribbon colour, secondary object |
| Must not copy | The white gold-dotted box with the magenta satin bow over a second box tied with yellow raffia |
| User photo supersedes? | No |
| Output | **1200 × 1200 px**, WebP **with alpha** |

**About the source resolution:** the recovered G1-4 is only 345 px, so it is soft even on the canvas at
its 300 dp and pill uses. The new one must be generated at full size and not upscaled.

## Group 2: example content (not to be bundled)

These recovered images play the part of user data on the boards: Peter, Mom and Anna, and their
gifts and wishes. Shipping any of them, or a generated stand-in, as a bundled image would make demo
content look like the user's own. So none of them gets a production replacement.

| Recovered | Plays | Seen on | At runtime |
|---|---|---|---|
| `e739a7f1….jpg` (780×644) | Peter's portrait, a dark silhouette. The board comment calls it a "relationship-neutral personal-photo placeholder" | Person detail hero; avatars on People, Lists, Share & Invite, Wishlist owner/guest, Gift detail, Gift idea details, Add gift idea person picker | The person's own photo (`Person.photoPath` exists in the model) or the **aqua monogram** (DS-04 "Person without photo — monogram"). For the Person-detail hero with no photo, see the remaining ambiguities |
| `d7e715cd….jpg`, `e6a0fe8c….jpg` (560×560) | Mom and Anna avatars | People, Lists, Share & Invite, Home Coming up | Person photo or monogram |
| `80f0ad15….jpg` (960×960) | The headphones gift photo | Gift detail hero; inset on Gift idea details and Mark as given; DS-04/05 examples | The gift's or idea's own photo (`Gift.photoPath`, `GiftIdea.photoPath`). Without one, the **branded gift glyph on an aqua tile** (DS-04, "never a broken-image icon") |
| `420c5014`, `7429cd5f`, `3ed40e70`, `9e1ece56`, `8fe647e0`, `907c567a` (340×400) | History cards: headphones, concert tickets, perfume, running shoes, a smartwatch (named "Garmin" in the board's alt text and copy, so a real brand), cookbook | Home Recent gifts, Person detail history grid, Gift history rows | Gift photo, cropped into the tile, or the gift glyph |
| `8fc37a97`, `e827698b`, `e9fd1bc7`, `fb86ca6a` (440×440) | Wish thumbnails | Wishlist owner and guest | The wish's own photo (`Wish.photoPath`) or the gift glyph |
| `3052cc23`, `6bc91cab` (480×480) | List cover images | Lists | **No runtime source exists.** Wishlists have no cover photo in the model. Use the gift glyph tile, or derive a cover from a wish photo; to decide when Lists is built |

Rules for group 2:
- Not in `src/main/res` and not in the release APK.
- Compose previews and tests render the designed fallbacks (monogram, gift glyph tile) or a neutral
  test-only placeholder. They do not use these files.
- If demo photography is ever wanted, for example for Play Store screenshots or marketing, it is a
  separate task with its own original images. It never goes in the app bundle.
- Board copy that names brands ("Garmin Watch") is example text. It must not be copied into strings or
  sample data.

## Group 3: user photos

Gift, gift idea, wish and person photos taken with the camera or picked from the gallery are stored in
private Supabase Storage and cached locally. They need no replacement asset. Where a group 2 image
appears on a board, the user's photo takes that place at runtime, cropped as the board's container
crops it. The existing uncropped Gift-detail behaviour from the camera follow-up stays until the
Gift-detail redesign decides how hero crop and full view coexist.

## Summary

- **New images to generate: 4.**
  - 2 hero/editorial: G1-1 Home hero and G1-2 Onboarding Share. G1-2 can be a second crop of the
    G1-1 master, so the minimum is **3 generated originals and 4 exported files**.
  - 2 supporting cut-outs: G1-3 large and G1-4 small. These also cover Onboarding Remember, Onboarding
    Plan and the People-empty mark.
- **Content thumbnails to generate: 0.** Every content thumbnail is user content (group 2 → group 3,
  with designed fallbacks).
- **Recovered images that must never ship: all 20.**

## Remaining ambiguities

- **I1. Person-detail hero without a photo.** DS-06 makes Person detail a Level 1 editorial screen, and
  the board fills the hero with a silhouette photo labelled as a placeholder. The DS-04 person fallback
  is the monogram, but no board draws a full-width hero with no photo. Options:
  - a teal or aqua hero surface with a large monogram (reuses DS-04, no asset);
  - a fifth bundled, person-neutral editorial image (a still life, never a person).
  
  The default proposal is the first option. Decide before the Person-detail phase.
- **I2. List covers.** There is no data source (see group 2). Decide when Lists is built.
- **I3. One shoot or two for G1-1 / G1-2.** The default is one master with two crops, as in the
  reference. It does not block anything.
