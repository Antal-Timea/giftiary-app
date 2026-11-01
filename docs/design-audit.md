# Approved design vs current app: UI audit

Status: read-only audit, 2026-11-01. Last revised the same day, after the approved designs were
recovered and the owner made the R1–R4 decisions. The Android code audited is unchanged since
`d7f059c`. No product code, resources, backend, website or Play Console were changed.

Conclusion: **the current UI is not release-ready against the approved design.** The redesign is mostly
UI-only:
- the light colour tokens need restyling;
- the shapes and the screen structure differ everywhere;
- navigation needs a new shell (a phone bottom bar with the global +);
- there is no editorial imagery.

It needs no data or domain change, except search, which can filter in the ViewModel. **Phase 1 (the
light design-system foundation) can start now** (§8).

---

## 1. Source of truth

| Source | Where | Authority |
|---|---|---|
| The 39 `APPROVED - ` artboards on Claude's design canvas "Gifty — Visual Direction Exploration" (version `1793555139-e39c`) | **`docs/design/approved/`**: PNG renders, verbatim `.dc.html` sources, `canvas.json` | **Final visual source of truth.** The owner confirmed this explicitly (R1). The inline styles in the sources are the spec when an exact value matters |
| Design-system boards DS-01…DS-06 and the UI Kit | same folder (`ds-*.png`) | Part of the approved set. Where a phone board and DS-04 disagree on navigation, DS-04 wins (R2) |
| CLAUDE.md §29–§31 | repo | Product rules: four destinations, Quick Add as an action, typography direction. **Its dark tokens do not drive this redesign** (R3) |
| `docs/design/imagery-brief.md` | repo | What production imagery is needed and how it is made (R4) |
| Canvas boards without the `APPROVED - ` prefix: rows 1–5, the Teal Accent alternatives, row 15 (dark), row 16 (tablet, also the boards behind `giftiary-tablet-screenshots.zip`) | canvas only | **Not approved.** At most a hint, never a spec |
| Recovered images in `docs/design/approved/source/assets/` | repo | **Reference only** (R4). They must never be copied into Android resources |

The `docs/design/approved/README.md` file lists every board, the approval evidence, and the screens that
have no approved design.

### Owner decisions (2026-11-01, final)

| | Decision |
|---|---|
| **R1** | `APPROVED - ` artboards are the final approved visual source of truth |
| **R2** | **Person detail uses the standard DS-04 navigation unit**: bar, aqua wave and central coral global +. The bar-only Person-detail board is an early-version inconsistency, like its older `#5C7A76` muted colour and parchment bar |
| **R3** | **Dark mode is out of scope** for this redesign and release. Only light is implemented and validated. Dark mode is not modified, and the existing dark tokens must not drive light decisions |
| **R4** | **The recovered images are reference only** and must not ship. Later, new original AI-generated images are made with the same visual role and art direction. They contain no identifiable people, logos, text, exact object arrangements or distinctive backgrounds, and the owner approves them before they are committed. See `docs/design/imagery-brief.md` |

**Known metadata typo, harmless:** the Add-a-Gift quick-capture title is spelled `APPOVED - ` on the
canvas. It is approved: the owner's confirmation covers it, and canvas row 6 and the older Docs design
system list it. The canvas is not edited to fix it.

Below:
- "**Visible**" means the fact can be seen on an approved board, and the file is named.
- "**Inference**" marks this audit's proposal for something no approved board covers. It is not a
  design fact.

---

## 2. What the approved design specifies (Visible)

| Topic | What the approved boards show | Source file(s) |
|---|---|---|
| Bottom navigation | Four destinations: **Home, People, Gifts, Lists**, with stroked icons (house, people, gift, list). Selected: filled dark teal `#17423E`, label 700. Unselected: `#547370`, label 600 at 10.5. The ivory bar has a 28 dp top radius and tucks under a 52 dp aqua `#BFEAE3` **wave** | `phone-home`, `ds-04-navigation-content` |
| Central + / Quick Add | A 58 dp coral `#D6285A` circle with a 3 dp ivory ring, centred on the wave, half above the bar. "Never a fifth destination". It opens the **Quick Add bottom sheet**: Remember (Gift I gave, Gift I received), Plan (Gift idea, Wishlist item), Organize (Person, List). It has no Occasion entry | `phone-quick-add-sheet`, `ds-03`, `ds-04` |
| Where navigation shows | **Root** screens (Home, People, Gifts, Lists): bar, wave and +, no back control. **Pushed** screens: the same unit, plus a 48 dp aqua back circle. That is Person detail (R2), Gift detail, Gift idea detail, Wishlist owner and guest, and Mark as given. DS-04: "The wave, the global add and the bar are one unit and never appear apart." **Task flows** hide it: Add gift, Add gift idea, Add/Edit person, Add/Edit wish, Create list, Sign in. Also no bar on Profile, Settings, Share & Invite, onboarding and the error state | `ds-04`, each phone board |
| Profile / Settings | The avatar top right of the Home hero, labelled "Your profile", opens Profile: avatar, name, email, Edit profile, stats, **Settings**, Sign out. Settings is reached from there | `phone-home`, `phone-profile`, `phone-settings` |
| Home hero | An **editorial photograph** on warm yellow `#E3A73A` with a 32 dp bottom radius. Text on the image: the "GIFTY" wordmark, Lora "Who are we making happy next?", the date, and a **Caveat** handwriting line "Small gifts, big stories ♡" | `phone-home`, `ds-06` |
| Coming up | An aqua `#BFEAE3` band, radius 32, full bleed. Rows have a 3 dp coral (nothing planned) or teal (planned) marker, a monogram, the name, "Birthday · date", the idea count, the plan status with an icon, a Lora day count with "DAYS", and a chevron. It also appears on People, above "All people" | `phone-home`, `phone-people` |
| Recent gifts | "Recent gifts / Your memory collection" with "See all", then **photo cards** of the gifts | `phone-home` |
| People | Lora 28 title with a standfirst, a search field, the Coming-up band, then "All people" rows: a 48 dp photo avatar or aqua monogram, the name, "relationship · next birthday", a chevron, and indented hairline dividers | `phone-people`, the empty and no-results states |
| Person detail | A full-bleed **photo hero**, 266 dp, with the name and relationship in white, plus back and edit circles. An aqua occasion card with a coral "Plan gift" pill. "Gift ideas" rows with icon tiles and "+ Add idea". "History with Peter" on parchment, with year and occasion overlines and a two-column grid of **photo cards** with direction lines. Navigation is the standard unit (R2) | `phone-person-detail`, `ds-04` |
| Gift history | Lora title, standfirst, search, and chips **All** (dark filled), **→ Given** (coral outline) and **← Received** (teal outline). Lora year headings, caps month overlines, and rows with a 52 dp **photo thumbnail** or an aqua gift-glyph tile, plus "direction · person · occasion" in the direction colour. **There is no Ideas chip** | `phone-gift-history` |
| Gift detail | **The gift photo as a full-bleed hero**, with "→ Given to Peter" and a Lora title on it, plus back and overflow circles. Then a person row, an aqua info block (date, price), and the note in Caveat. The bar and + are present | `phone-gift-detail`, `phone-state-gift-pending-sync` |
| Gift idea detail | A "GIFT IDEA" overline, title, "For Peter", status (Idea / Purchased), occasion, estimated price or price paid, product link, note. Idea: "Plan this gift" and "Mark as purchased". Purchased: "Mark as given", with "Once given, this is saved to your gift history" | `phone-gift-idea-detail-*`, `phone-mark-as-given` |
| Add gift | Aqua back circle, "GIFTY" wordmark, Lora 36 "Add a gift", a standfirst. **Three numbered steps:** direction pills (selected teal/aqua, **not** coral); person search with monogram chips and a dashed coral "New"; the gift name. Then a coral "Save gift →" and an aqua "Add more details (optional)", stacked **vertically**. The expanded form adds Occasion, Date, Price with currency, an 84 dp dashed **Photo** tile, and Notes, with Save at the end | `phone-add-gift-quick`, `phone-add-gift-details` |
| Other editors | Add gift idea, Add/Edit person (88 dp aqua monogram photo picker with a coral + badge), Add/Edit wish, Create list. All use the flow header (aqua back circle, wordmark, Lora title, standfirst) and a supporting cut-out | the matching `phone-*` boards, `ds-03` |
| Lists and sharing | Lists: "Your lists", with privacy stated in words (e.g. "Only you"), and "Shared with you". The wishlist owner sees "Shared with family · Manage" and **no reservation state**. A guest sees "I'll get this", "You're getting this · Undo" or "Reserved". Share & Invite: people with access, "Share with someone", and the note that "gift reservations stay hidden from the list's owner" | `phone-lists`, `phone-wishlist-owner`, `phone-wishlist-guest`, `phone-share-invite` |
| Auth and onboarding | Sign in: "Continue with Google", email and password, "Forgot password?", Sign in, "Create account". Three onboarding chapters: Remember, Plan, Share | `phone-sign-in`, `phone-onboarding-*` |
| Settings | Language, Appearance, Notifications (occasion reminders, gift planning reminders), Privacy, "Your data", About, Sign out, **Delete account** | `phone-settings` |
| States | Offline: an "Offline · 2 changes waiting to sync" line above the content. Pending sync: "Waiting to sync · Saved on this device". Refresh failed: "Couldn't refresh · Showing saved data · Try again" above cached content. Also: a skeleton Home, People empty (with a mark) and no-results states, and a generic error with Try again and Go back | `phone-state-*`, `ds-05` |
| Typography | Lora 700 for display 36, screen title 28, sheet title 23, editorial section 22. Lora 600 at 17 for section titles. Public Sans for everything functional, from 15 down to 10.5. Caveat 500 for one handwriting line, on photography only. No italics | `ds-02` |
| Colours (light) | Background `#FBF7EE`, parchment `#F4EEDF`, field white. Text `#17423E` / `#47615D` / `#2C5852` / `#6E837D`, placeholder `#7C948F`. Aqua `#4FD6C2` (tints 0.14–0.35), aqua band `#BFEAE3`, aqua pale `#DDF5F0`. Coral `#D6285A`, yellow `#E3A73A`, received teal `#0F5F56`, nav muted `#547370` | `ds-01` |
| Spacing | 4 / 8 / 12 / 16 / 20 / 22 / 24 / 26 / 32. The gutter is 22 on browse screens and 24 on task screens | `ds-02` |
| Shape | Field 16, primary button 18, pills 999, Coming-up band 32 (24 when inset), hero bottoms 32, sheets and the bar 28 on the top corners, row thumbnails 14 (gift) and 16 (wish). Content stays **uncontained**: rows, whitespace and hairlines. A surface appears only where the content changes kind (the aqua band, the info block, parchment history, sheets) | `ds-02`, `ds-03`, `ds-04` |
| Imagery | **Photography, not illustration**, at three levels: editorial heroes (Level 1); supporting cut-outs at 0.26–0.50 opacity on creation, sign-in and profile screens (Level 2); none on utility screens. Fallbacks: a gift without a photo gets the branded gift glyph on an aqua tile; a person without one gets the aqua monogram | `ds-06`, `ds-04` |
| Accessibility and language | Decorative cut-outs, waves and monograms are hidden from screen readers; real photos carry descriptions. Font scaling without clipping. English and Hungarian | `ds-06` |

---

## 3. Current UI inventory (from routes and code)

`presentation/` holds about 15 000 lines across 90 files. The shell is hub-and-spoke:
- **Phone:** Home has stacked buttons (Gifts as primary; People and Wishlists as secondary; a Settings
  text button). Every other screen has a back-arrow top bar. There is no bottom bar.
- **Medium and expanded:** a `NavigationRail` (`GiftiaryNavigationRail`) with Home, People, Gifts and
  Wishlists, and Settings at the foot. The Home buttons are hidden.

| Area | Routes / screens | Notes |
|---|---|---|
| Auth | SignIn, CreateAccount, CheckEmail, ForgotPassword, Recovery, PasswordChanged, Startup | Separate `AuthNavHost`. Provider buttons (Google, and Facebook behind flags). No onboarding |
| Account deletion | FinishingAccountDeletion, AccountDeleted (auth gate), plus a delete action in Settings | |
| Home | Home | Lora tagline header, Coming up list, own-occasions link, reminder card, sync status line, section buttons |
| Sync | SyncAttention | A full screen opened from the sync line |
| People | People, PersonDetail, AddPerson, EditPerson | `AdaptivePeopleScreen`: list-detail at expanded width. `Person.photoPath` exists in the model but has no UI |
| Occasions | AddOccasion, EditOccasion, OwnOccasions, OccasionPlan | |
| Gifts / history | GiftHistory (Given / Received / Ideas chips), GiftDetail, AddGift, EditGift | `GiftHistoryListDetail` at expanded width. The photo shows in the editor and in the detail |
| Gift ideas | GiftIdeaDetail, AddGiftIdea, EditGiftIdea, MarkGiftIdeaGiven | |
| Wishlists | Wishlists, WishlistDetail, AddWishlist, EditWishlist, WishDetail, AddWish, EditWish | |
| Sharing | WishlistAccess (owner: invite link and members), SharedWishlists, SharedWishlist (guest reservations), JoinWishlist (`giftiary://` invite link, with the token held in memory) | |
| Settings | Settings | Profile, reminders, sign-out, account deletion, privacy link. There is no separate Profile screen |
| States | `LoadingContent` (a centred coral spinner), `MessageContent` (a centred title and message), snackbars, the sync line, and the "Saved on this device / waiting to upload" photo note | No skeletons and no list-level offline line |
| Adaptive | `WidthClass` 600/840, the rail, `ListDetailPanes` (360 dp list), readable 640 / wide 1040 caps | T1–T6 |

Shared components:
- `GiftiaryTopBar`: a Lora `headlineSmall` title on the page background.
- `GiftiaryPrimaryButton`: coral, 52 dp, radius 10.
- `GiftiarySecondaryButton`: outlined, in the border colour.
- `GiftiaryAddFab`: a coral extended FAB.
- `GiftiaryTextField` and `GiftiaryDropdownField`: outlined, radius 8.
- `GiftiaryDateField`, `GiftiaryConfirmDialog` (AlertDialog), `SectionHeading`, `ListRow` (a title
  plus one detail line, no leading visual), `ListDetailPanes`.

Light tokens in code that differ from the approved values:

| Token | Code | Approved |
|---|---|---|
| background | `#F7F2E8` | `#FBF7EE` |
| textPrimary | `#143A34` | `#17423E` |
| primaryAction | `#C8234F` | `#D6285A` |
| surfaceContext | `#E4EEEA` | `#BFEAE3` |
| received | `#1A7A6D` | `#0F5F56` |
| nav unselected | `#586E69` | `#547370` |

Radii in code are 8 / 10 / 12 / 16. A code comment says the UI "should not read as a stack of rounded
cards". There is no Caveat font.

Imagery:
- Drawables are icons only: add, back, calendar, delete, gift given / received, five nav icons, and two
  provider marks.
- There is **no editorial imagery, no cut-outs and no avatars**. The only image is the user's own gift
  photo.

---

## 4. Screen-by-screen comparison

Severity key:
- **MATCH**
- **MINOR**: polish
- **MAJOR**: visibly different structure or hierarchy
- **MISSING IMPL**: in the approved design, absent in the app
- **MISSING DESIGN**: no approved board; the design system applies by analogy
- **ADDED**: app functionality the approved boards do not show

### 4.1 Navigation shell

| Aspect | Approved | Current | Severity |
|---|---|---|---|
| Phone top level | Bottom bar (Home, People, Gifts, Lists), aqua wave, central coral + | Stacked buttons on Home; back arrows everywhere | **MAJOR** |
| Pushed screens | The same unit with an aqua back circle (Person detail too, R2) | Back-arrow top bar, no navigation | **MAJOR** |
| Global add | One + that opens the Quick Add sheet | Per-list FABs; no global add | **MISSING IMPL** |
| Label | "Lists" | "Wishlists" (`AppSection.WISHLISTS`) | MINOR (string) |
| Profile / Settings | Home avatar → Profile → Settings | Rail item / Home text button; no Profile screen | **MAJOR / MISSING IMPL** |
| Tablet / expanded | No approved design (row 16 is unapproved) | Rail | **MISSING DESIGN** (keep the rail; §5.8) |

### 4.2 Home

| Aspect | Approved | Current | Severity |
|---|---|---|---|
| Hero | Editorial photo on yellow with a 32 dp bottom radius; wordmark, Lora question, date, Caveat line and avatar on the image | Plain text: the app name in `labelLarge` and a `displaySmall` tagline | **MAJOR** |
| Coming up | Aqua band with markers, monograms, plan status, Lora day count, chevrons | Flat rows: name, a dot-joined detail line, "In N days" | **MAJOR** |
| Recent gifts | Photo cards with direction lines, "See all" | Absent | **MISSING IMPL** |
| Offline | "Offline · N changes waiting to sync" line above the content | Sync status line with a link to SyncAttention | MINOR (wording and placement) |
| Reminder card | Not drawn | `ReminderCard` Surface | **ADDED** |
| Own occasions link | Not drawn | TextButton | **ADDED** |

### 4.3 People and Person detail

| Aspect | Approved | Current | Severity |
|---|---|---|---|
| List header | Lora title, standfirst, search, Coming-up band | Top bar title only; no search anywhere in the app | **MAJOR / MISSING IMPL** (search) |
| Rows | 48 dp avatar or monogram, name, "relationship · next birthday", chevron, indented hairlines | `PersonRow` with a `Monogram`, name and detail; delete icon in the row | MAJOR (row anatomy, delete placement) |
| Detail hero | Full-bleed person photo hero with the name on it, back and edit circles | Top bar with the name | **MAJOR** (see I1 for no photo) |
| Next occasion | Aqua card with a coral "Plan gift" pill | `OccasionRow` list under a `SectionHeading` | **MAJOR** |
| Gift ideas | Icon-tile rows and "+ Add idea" | Heading, `ListRow`s, add text button | MAJOR (styling) |
| History | Parchment section, year and occasion overlines, two-column photo cards | "Gifts" heading with rows | **MAJOR** |
| Wishlists section | Not drawn | Heading, rows, add button | **ADDED** |
| Empty / no results | Mark (cut-out in a 128 dp circle), message, single next action | Centred text | **MAJOR** |

### 4.4 Gift history and gift detail

| Aspect | Approved | Current | Severity |
|---|---|---|---|
| Header | Title, standfirst, search | Top bar only, no search | **MAJOR / MISSING IMPL** |
| Filters | All (dark filled), → Given (coral outline), ← Received (teal outline) | `FilterChip` All / Given / Received / Ideas, uniform styling | MAJOR (styling) |
| Ideas chip | Not drawn; ideas live on Person detail and Gift idea detail | Present | **ADDED** (keep or drop: A4, product decision) |
| Grouping | Lora years, caps months, 52 dp photo thumbnail or gift-glyph tile | Flat dated rows with a direction icon | **MAJOR** |
| Detail | Photo hero with direction and title on it; person row; aqua info block; Caveat note; navigation unit | Top bar, label/value list, uncropped photo (at most 360 dp), delete | **MAJOR** |
| Refresh failed | Line above cached content with Try again | Owned data is local, so not applicable; sharing screens use `OnlineContent` | MATCH (behaviour) / MINOR (visual) |

### 4.5 Add / edit gift

| Aspect | Approved | Current | Severity |
|---|---|---|---|
| Header | Aqua back circle, wordmark, Lora 36 title, standfirst, cut-outs | Top bar "Add gift" | **MAJOR** |
| Structure | Three numbered steps; Save and "Add more details" stacked vertically | One long form, always expanded | **MAJOR** |
| Direction | Pills, selected teal/aqua | `SegmentedButton` | MAJOR |
| Person | Search, monogram chips, dashed coral "New" | Dropdowns; `NeedsPerson` when no people exist | **MAJOR** |
| Details | Occasion, date, price + currency, photo tile, notes | In the form | MAJOR (move under "more details") |
| Third-party giver/recipient | Not drawn | Supported (Grandma → Martin) | **ADDED**: must survive under "more details" |
| Edit gift | No board | Same form | **MISSING DESIGN** (reuse the add layout, expanded) |

### 4.6 Other approved screens

| Screen | Current | Severity |
|---|---|---|
| Gift idea detail (idea, purchased), Add gift idea, Mark as given | Top bar, flat fields | **MAJOR** |
| Lists, Wishlist owner, Wishlist guest | Separate Wishlists and SharedWishlists screens, flat rows; owner view already has no reservation data (backend-enforced) | **MAJOR** (layout); MATCH (privacy) |
| Share & Invite | WishlistAccess: invite link and members | **MAJOR** |
| Add/Edit person, Add/Edit wish, Create list | Top bar, outlined fields | **MAJOR** |
| Quick Add sheet | None | **MISSING IMPL** |
| Onboarding (3 chapters) | None | **MISSING IMPL** |
| Sign in | Top bar, outlined fields, provider buttons | **MAJOR** |
| Profile | None (folded into Settings) | **MISSING IMPL** |
| Settings | Flat sections | **MAJOR** |
| States: skeleton Home, generic error, pending-sync line | Spinner, centred text, photo note | **MAJOR** |

### 4.7 Screens with no approved design

All **MISSING DESIGN**. Apply the design system by analogy. The README lists them:
- occasions (editor, own occasions, occasion plan);
- Edit gift and Edit gift idea;
- Wish detail and Edit list;
- invite acceptance (Join);
- the full Create-account form, check-email, forgot/reset password and recovery;
- Facebook sign-in;
- the account-deletion screens;
- SyncAttention;
- the reminder card;
- the camera/gallery choice;
- tablet layouts;
- Hungarian.

---

## 5. Navigation target

1. **Destinations:** Home, People, Gifts, Lists. Settings is not a destination: it is reached through
   the Home avatar → Profile → Settings. `AppSection.SETTINGS` stays for the rail and route mapping.
   The phone bar shows exactly four items.
2. **Global +:** one coral + on the wave. It opens the Quick Add sheet with the six drawn entries, each
   going to an existing Add route. It is not a destination (CLAUDE.md §29).
   - Per-list FABs become redundant on phone. Remove them only after the + ships.
   - The Wishlist-detail "Add wish" FAB stays: there the list is the context. *Inference.*
   - The sheet has no Occasion entry. Own occasions keep their current entry point until occasions are
     designed. *Inference.*
3. **Visibility (DS-04, R2):**
   - **Shown** (bar, wave and +): the four roots, and the pushed detail screens: Person detail, Gift
     detail, Gift idea detail, Wishlist detail (owner and guest), Mark as given. Pushed screens add the
     aqua back circle.
   - **Hidden:** task flows (every Add/Edit editor, Create list), auth, onboarding, Profile, Settings,
     Share & Invite, the full-screen error.
   - Screens with no board follow their nearest kind: Wish detail and Shared wishlist as pushed;
     occasion editors, Join and SyncAttention as task screens. *Inference.*
   - Visibility is derived from the current back-stack entry, as a set of route classes, tested like
     the `routeSections` exhaustive test.
   - The bar, wave and + are never split: no screen shows the bar without the + or the wave.
4. **Nav graph:** keep the single `GiftiaryNavHost` and the type-safe routes. Add a phone
   `Scaffold(bottomBar)` in `AdaptiveNavigationFrame` for COMPACT, reusing `AppSection`,
   `routeSections` and `sectionNavOptions` (save/restore state, `launchSingleTop`, `popUpTo` start).
   The bar replaces Home's `SectionButtons`.
5. **Back stack:** a section tap uses the existing `sectionNavOptions`. Back from a root goes to Home,
   then exits.
6. **Deep links:** `giftiary://auth/*` is handled before the nav host and is unaffected. The wishlist
   invite opens over the current section with navigation hidden. No new deep links.
7. **State restoration:** `sectionNavOptions` already saves and restores. The sheet needs only
   `rememberSaveable` visibility.
8. **Tablet:** no approved design. Keep the current rail, restyled with the light tokens. The coral +
   at the top of the rail and the aqua indicator are proposals from the unapproved row 16.
   *Inference.*

---

## 6. Design-system findings

| Element | Current | Approved | Verdict |
|---|---|---|---|
| Light palette | See the token table in §3 | DS-01 | **Restyle**: replace the light values. Add aqua band, aqua pale, parchment and hero yellow as semantic tokens. Dark is not touched (R3) |
| Typography | Lora display/headline, Public Sans | DS-02 roles, plus **Caveat 500** for the handwriting line | **Restyle**: map the DS-02 sizes and weights; add Caveat as a font resource (not a dependency) |
| Spacing | 4…48 scale | DS-02 scale, gutters 22 / 24 | **Extend**: add 22 and 26 and the two gutters |
| Radii | 8 / 10 / 12 / 16 | Field 16, button 18, pill, band/hero 32, sheet 28 top, thumbnails 14 / 16 | **Restyle** |
| Containers | Flat lists; one Surface (`ReminderCard`) | Uncontained rows and hairlines; specific surfaces only (aqua band, info block, parchment history, sheets) | **Partly right already**: add those specific surfaces, not a generic card |
| Primary button | Coral, radius 10 | Coral, radius 18, full width; pill variant for in-card CTAs | **Restyle** |
| Secondary actions | Outlined grey | Aqua filled ("Add more details"), text links ("+ Add idea") | **Restyle** |
| Rows | `ListRow`: text only | Leading avatar or thumbnail, trailing meta or chevron, indented hairline | **Restyle**: add slots |
| Avatars | `Monogram` in People only | Photo avatar or aqua monogram everywhere | **Promote** `Monogram` to components, with a photo slot |
| Gift glyph tile | None | Aqua tile with the branded gift glyph as the no-photo fallback | **Missing** |
| Chips | `FilterChip` | All dark filled; Given/Received outlined in direction colours with arrows | **Restyle** |
| Direction toggle | `SegmentedButton` | Two pills, selected teal/aqua | **Replace** |
| Person picker | Dropdown | Search, monogram chip row, dashed "New" | **New component** |
| Inputs | Outlined, radius 8 | White field, radius 16, labels above, search with a leading icon | **Restyle**, plus a search field |
| Photo picker | Editor-specific | 84 dp dashed coral square (attachment); 88 dp aqua monogram circle (identity) | **New component** |
| Sheets and dialogs | AlertDialog | Quick Add as a bottom sheet with a 28 dp top radius | **New** sheet; dialogs stay for confirmations |
| Top bar | Lora title bar on every screen | Roots: large Lora title and standfirst in content; flows: aqua back circle and wordmark; pushed: circles over the hero | **Restyle** |
| Status lines | Sync line, photo note | Offline, pending-sync and refresh-failed lines (DS-05) | **Restyle** |
| Loading | Spinner | Skeleton when there is no content | **Replace** |
| Imagery | None | Level 1 heroes and Level 2 cut-outs | **Missing**: new original images (`docs/design/imagery-brief.md`). Phase 1 uses placeholders |
| Photo display | Uncropped, at most 360 dp | Cropped into tiles and heroes; uncropped full view not drawn | Add a cropped tile/hero display next to the existing uncropped view. Do not change the approved camera follow-up behaviour until the Gift-detail phase decides how the two coexist |

---

## 7. Newer functionality: where it fits

| Feature | Approved reference | Placement |
|---|---|---|
| Gift photos | **Yes:** the photo tile in Add gift details, the Gift detail hero, history thumbnails, Recent-gift cards. **No:** the camera/gallery choice and the photo-chosen state | The source choice becomes a bottom sheet (*inference*). The `GiftPhotoField` contract is unchanged |
| Offline / pending sync | **Yes:** the Home offline line, the Gift-detail pending-sync line, the refresh-failed line. **No:** SyncAttention | Lines as drawn, tappable to SyncAttention. Never wording that implies the data was lost (CLAUDE.md §16, §28) |
| Wishlist sharing / reservations | **Yes:** Lists, owner and guest wishlist, Share & Invite. **No:** Join and Wish detail | Lists merges own and shared lists in two sections. Reservation privacy stays backend-enforced (§24) |
| Provider auth | **Google: yes. Facebook: no** | Facebook below Google in the same style, behind its flag (*inference*) |
| Account deletion / privacy | **Partly:** the Settings rows | Deletion screens use the message-state style (*inference*) |
| Reminders | Settings toggles **yes**; the Home reminder card **no** | A quiet card under the hero (*inference*) |
| Own occasions / occasion plan | **No** | Keep the current entry points; design by analogy |
| Ideas in Gift history | **No** (A4) | Product decision: keep the fourth chip, or keep ideas on Person detail only |
| Third-party gifts | **No** | Under "Add more details" as a giver/recipient override |

---

## 8. Phased roadmap

Ship only after phases 1–5 at least. **Do not ship the current UI first.** All phases are **light theme
only** (R3).

| # | Phase | Scope | Data / domain / nav | Risk | Tests |
|---|---|---|---|---|---|
| 0 | **Design sources** | **Done:** the approved snapshot is in `docs/design/approved/`, R1–R4 decided, imagery briefed | — | — | — |
| 1 | **Light foundation** | DS-01 light tokens; DS-02 type roles including Caveat; spacing and gutters; radii; the components from DS-03/04/05: buttons, fields, search, direction pills, chips, person picker, photo picker, rows with slots, monogram/avatar, gift-glyph tile, aqua band, status lines, skeleton, sheet, the bar/wave/+ component itself; an image slot that takes a placeholder | none | Low to medium: shared components used by every screen | Compose tests for new components; existing layout tests stay green; light contrast checks |
| 2 | **Navigation shell** | Phone bar, wave and + wired in; Quick Add sheet; visibility per §5 including Person detail (R2); remove `SectionButtons`; "Lists" label; avatar → Profile → Settings | nav shell only, no route changes | Medium: back stack, deep-link entry, state restore | Exhaustive visibility test per route; section re-select restores state; invite opens with navigation hidden; back from a root |
| 3 | **High-impact screens** | Home, People + Person detail, Gift history + Gift detail, Add a gift | Recent gifts: read-only use case. Search: ViewModel filter. Add-gift steps: UI over the current editor state | Medium to high: editor layout tests, T3–T6 adaptive tests | Rewrite the affected layout tests; VM tests for search and recent; step-flow tests including third-party and photo |
| 4 | **Other approved screens** | Gift idea detail, Add gift idea, Mark as given, Lists, wishlists, Share & Invite, person/wish/list editors, Sign in, Profile, Settings, onboarding, states | Profile is a new route; onboarding needs a first-run flag | Medium (breadth) | Updated screen tests; owner view has no reservation node |
| 5 | **Imagery** | Generate and approve the 4 originals (brief); replace the placeholders | none | Low | Visual review against the references |
| 6 | **Unapproved areas** | Screens in §4.7 by analogy; tablet restyle of the existing adaptive layouts | none | Low to medium | Existing T1–T6 tests updated |
| 7 | **Accessibility / localization** | Descriptions, 48 dp targets, font scale 200 %, Hungarian lengths ("DAYS"/"NAP", chips, wordmark lines) | none | Low | Semantics assertions, HU and font-scale previews |
| 8 | **Screenshot regression** | Key screens, light, EN/HU, compact/expanded | none | Low; a tooling decision (A9) | Baselines reviewed against `docs/design/approved/` |

Dark mode is not a phase of this redesign (R3). The existing dark tokens stay as they are.

### Phase 1 readiness

**Phase 1 can start.** Everything it needs is approved and unambiguous in DS-01…DS-05 and the UI Kit:
- palette;
- type, including the Caveat handwriting role;
- spacing;
- radii;
- every component listed above, including the navigation unit's own drawing.

Conditions:
- **Light only (R3).** Change only the light colour scheme. Leave the dark values and dark-theme code
  untouched. Do not derive any light value from the dark tokens.
- **Independent of the final photography (R4).** Image-bearing components (hero, cut-out, thumbnail,
  avatar) take an image slot. Previews and tests use the designed fallbacks:
  - the `#E3A73A` hero fill;
  - the gift-glyph tile;
  - the monogram;
  - no cut-out.
  
  No recovered image is copied into resources. The real images arrive in phase 5.
- **No navigation wiring.** Phase 1 builds the bar/wave/+ component. Phase 2 wires it, including
  Person detail per R2.

---

## 9. Open ambiguities

| ID | Status |
|---|---|
| A1 | **Resolved:** approved designs are in `docs/design/approved/` |
| A2 | **Resolved:** navigation shows on roots and pushed screens (Person detail included, R2), and is hidden in task flows, auth, onboarding, Profile, Settings and Share & Invite |
| A3 | **Resolved (phone):** Home avatar → Profile → Settings. Tablet has no approved design |
| A4 | **Open, product decision:** keep the Ideas chip in Gift history, or not? It is not in the approved board |
| A5 | **Resolved:** Caveat 500, one line, on photography only |
| A6 | **Partly resolved:** Quick Add is a bottom sheet. The photo-source choice has no board |
| A7 | **Resolved by R4:** new original images per `docs/design/imagery-brief.md` |
| A8 | **Resolved:** a user photo is used wherever the boards show content photography; otherwise the DS-04 fallbacks |
| A9 | **Open, tooling decision:** screenshot tooling (phase 8) |
| A10 | **Resolved by R3:** light only |
| I1 | **Open, before the Person-detail phase:** the Person-detail hero when the person has no photo (see the imagery brief) |
| I2 | **Open, before Lists:** list covers have no data source (see the imagery brief) |
