# Approved design vs current app: UI audit

Status: read-only audit, 2026-11-01, against `develop` @ `5509652` (code identical to `d7f059c`).
No product code, resources, backend, website or Play Console were changed.

Conclusion: **the current UI is not release-ready against the approved design.** The colors, fonts and
spacing tokens are mostly right. The screen structure is not: navigation, Home, the cards and imagery,
the add-gift flow, search, and list styling all differ. The redesign is mostly UI-only. It needs one
navigation-shell change and no data or domain change, except search, which can filter in the ViewModel.

---

## 1. Design sources

| Source | Where | Authority | Notes |
|---|---|---|---|
| CLAUDE.md §29–§31 | repo | **Authoritative (text)** | Four destinations Home / People / Gifts / Lists; "Quick Add is an action, not a navigation destination"; warm ivory, dark teal, aqua support, coral actions, editorial imagery, restrained rounded geometry, generous spacing; Lora + Public Sans; dark token values |
| Design-system commit `e1c0d13` | repo | Derived | Implements the tokens and fonts; no screen layouts |
| `giftiary-tablet-screenshots.zip` (16 PNG + README) | owner's Downloads, **not in repo** | **Authoritative for expanded width only, and derived** | The README says these "are designs, not captures": a *proposed* expanded layout "drawn from the final design system". 7″ 1920×1200 and 10″ 2560×1800, EN and HU: Home, Person, Add a gift, Gift history |
| Approved **phone** screens ("the screens you supplied", "design canvas row 16") | referenced by the README | **Authoritative but MISSING** | Not in the repo, Downloads or any doc. Their content is known only from what the README says carries over: a bottom bar with a wave, a central "+" action, Home hero, three-step Add a gift, and the four destinations |

**What this audit can rely on:**
- Phone-level findings rely on CLAUDE.md, the README's description of the phone design, and the tablet
  images, which reuse the same components (hero, cards, chips, steps).
- Phone pixel details cannot be checked:
  - the exact shape of the bottom-bar wave;
  - phone paddings;
  - the phone hero height;
  - screens with no tablet image (Wishlists, Settings, auth, occasions, editors other than Add a gift).

**Ambiguity A1 (blocking for implementation, not for this audit):** commit the approved phone screens
and their exports to `docs/design/`, together with the tablet zip, so that every later phase has a
reviewable reference.

---

## 2. Current UI inventory (from routes and code)

`presentation/` holds about 15 000 lines across 90 files. The shell is hub-and-spoke:
- **Phone:** Home has stacked buttons (Gifts as primary; People and Wishlists as secondary; a Settings
  text button). Every other screen has a back-arrow top bar. There is no bottom bar.
- **Medium and expanded:** a `NavigationRail` (`GiftiaryNavigationRail`) with Home, People, Gifts and
  Wishlists, and Settings at the foot. The Home buttons are hidden.

| Area | Routes / screens | Notes |
|---|---|---|
| Auth & onboarding | SignIn, CreateAccount, CheckEmail, ForgotPassword, Recovery, PasswordChanged, Startup | Separate `AuthNavHost`; provider buttons (Google, Facebook behind flags); no onboarding |
| Account deletion | FinishingAccountDeletion, AccountDeleted (auth gate), plus a delete action in Settings | |
| Home | Home | Lora tagline header, Coming up list, own-occasions link, reminder card, sync status line, section buttons |
| Sync | SyncAttention | Full screen opened from the sync line |
| People | People, PersonDetail, AddPerson, EditPerson | `AdaptivePeopleScreen`: list-detail at expanded width |
| Occasions | AddOccasion, EditOccasion, OwnOccasions, OccasionPlan | |
| Gifts / history | GiftHistory (Given / Received / Ideas chips), GiftDetail, AddGift, EditGift | `GiftHistoryListDetail` at expanded width; the photo shows in the editor and the detail |
| Gift ideas | GiftIdeaDetail, AddGiftIdea, EditGiftIdea, MarkGiftIdeaGiven | |
| Wishlists | Wishlists, WishlistDetail, AddWishlist, EditWishlist, WishDetail, AddWish, EditWish | |
| Sharing | WishlistAccess (owner: invite link and members), SharedWishlists, SharedWishlist (guest reservations), JoinWishlist (`giftiary://` invite link; token held in memory) | |
| Settings | Settings | Profile, reminders, sign-out, account deletion, privacy link |
| States | `LoadingContent` (centred coral spinner), `MessageContent` (centred title and message), snackbars, sync line, "Saved on this device / waiting to upload" photo note | No skeletons; no list-level offline banner |
| Adaptive | `WidthClass` 600/840, rail, `ListDetailPanes` (360 dp list), readable 640 / wide 1040 caps | T1–T6 |

Shared components:
- `GiftiaryTopBar`: Lora `headlineSmall` title on the page background.
- `GiftiaryPrimaryButton`: coral, 52 dp, radius 10.
- `GiftiarySecondaryButton`: outlined, border colour.
- `GiftiaryAddFab`: coral extended FAB.
- `GiftiaryTextField` and `GiftiaryDropdownField`: outlined, radius 8.
- `GiftiaryDateField`, `GiftiaryConfirmDialog` (AlertDialog), `SectionHeading`, `ListRow`
  (title plus one detail line, no leading visual), `ListDetailPanes`.

Imagery and icons:
- Drawables are icons only: add, back, calendar, delete, gift given / received, five nav icons, two
  provider marks.
- There are **no illustrations, hero art or avatars** anywhere. The only image is the user's own gift
  photo.

---

## 3. Screen-by-screen comparison

Severity key:
- **MATCH**
- **MINOR**: polish
- **MAJOR**: visibly different structure or hierarchy
- **MISSING IMPL**: in the design, absent in the app
- **MISSING DESIGN**: no approved design exists
- **ADDED**: functionality added after the design

### 3.1 Navigation shell

| Aspect | Design | Current | Severity |
|---|---|---|---|
| Phone top level | Bottom bar: Home, People, Gifts, Lists, a central "+" action, and a wave shape | Stacked buttons on Home; back arrows everywhere | **MAJOR** (structurally wrong) |
| Tablet | Rail with **coral global add at top**, four destinations, aqua pill indicator, aqua wave at the foot | Rail with four destinations, Settings at the foot, no add, default indicator | **MAJOR** |
| Label "Lists" | "Lists" | "Wishlists" (`AppSection.WISHLISTS`) | MINOR (string) |
| Settings / profile | Avatar initial top right of the Home hero (implied profile entry) | Rail item / Home text button | MAJOR |
| Global add | One "+" action (Quick Add) | Per-list FABs (People, Wishlists, Gifts…); no global add | **MISSING IMPL** |

### 3.2 Home

| Aspect | Design | Current | Severity |
|---|---|---|---|
| Hero | Yellow-gradient rounded hero (large radius): "GIFTIARY" eyebrow, Lora question headline, date, script tagline, gift illustration, avatar | Plain text: app name in `labelLarge` and a `displaySmall` tagline | **MAJOR** |
| Coming up | Aqua rounded card. Rows have a coloured left accent (coral = nothing planned, teal = planned), initial avatar, "Birthday · date", idea count, plan status with icon, large Lora day count with "DAYS", and a chevron | Flat rows: name, a dot-joined detail line, and "In N days" in `labelLarge` | **MAJOR** |
| Recent gifts | "Recent gifts / Your memory collection" with "See all", and image cards with "→ Given to" / "← Received from" | **Absent** | **MISSING IMPL** |
| Wide layout | Hero across; Coming up beside Recent gifts | Header; Coming up 3 : reminder + sync 2 | MAJOR |
| Reminder card | Not in the design | `ReminderCard` Surface | **ADDED** (keep; restyle as a quiet card under the hero) |
| Sync status line | Not in the design | Text line plus a link to SyncAttention | **ADDED** |
| Own occasions link | Not in the design | TextButton | **ADDED** (move into the Coming up card footer or People) |

### 3.3 People and Person detail

| Aspect | Design | Current | Severity |
|---|---|---|---|
| List header | Lora "People" with a subtitle and a **search field** | Top bar title only; **no search** anywhere in the app | MAJOR / **MISSING IMPL** (search) |
| Rows | Initial avatar, name, next occasion; selected row has an aqua fill | `PersonRow` with a `Monogram`, name and detail; delete icon in the row | MINOR–MAJOR (selected fill, delete placement) |
| Detail hero | Dark-teal hero card with decorative circles, large Lora name, relationship, big avatar, edit pencil | Top bar with the name; sections in a `LazyColumn` | **MAJOR** |
| Next occasion | Aqua card: occasion, idea count, plan status, coral "Plan gift" pill, day count | "Occasions" `SectionHeading` with `OccasionRow` rows | **MAJOR** |
| Gift ideas | "Gift ideas / Future possibilities, not yet given", icon rows, "+ Add idea" link | Heading, `ListRow`s, add text button | MAJOR (styling) |
| History | Beige "History with X / N gifts shared together" card with icon tiles and direction lines | "Gifts" heading with rows | **MAJOR** |
| Wishlists section | Not in the design | Heading, rows, add button | **ADDED** |
| Two-pane | One two-pane screen | `AdaptivePeopleScreen`, 360 dp list | MATCH (structure) / MINOR (styling) |

### 3.4 Gift history and gift detail

| Aspect | Design | Current | Severity |
|---|---|---|---|
| Header | Title, subtitle "Search, filter and revisit…" and a search field | Top bar only, no search | MAJOR / **MISSING IMPL** |
| Filters | "All" (dark filled), "→ Given" (coral outline), "← Received" (teal outline) | `FilterChip` row: All / Given / Received / Ideas, uniform styling | MAJOR (styling; set matches plus Ideas) |
| Grouping | Lora year headings, caps month labels, rows with icon tiles | Flat dated rows (`GiftRow` with a direction icon) | **MAJOR** |
| Detail | Large icon tile, Lora title, "→ Given to Martin", beige info table (Occasion / Date / Price), person row | Top bar, `DetailFields` label/value list, photo, delete | **MAJOR** |
| Ideas tab | Not in the design (ideas live on the person) | Chip "Ideas" | **ADDED** (keep; decide placement, see A4) |
| Photo | Not in the design (the design uses illustrations on cards) | `GiftPhotoView`: uncropped, at most 360 dp | **ADDED** |

### 3.5 Add / edit gift

| Aspect | Design | Current | Severity |
|---|---|---|---|
| Header | Round back button, "GIFTIARY" eyebrow, Lora "Add a gift", subtitle, corner illustration | Top bar "Add gift" | MAJOR |
| Structure | **Three numbered steps** on a beige grouped surface: 1 direction, 2 person, 3 gift name | One form: direction (`SegmentedButton`), giver / recipient dropdowns, name, then details | **MAJOR** |
| Direction | Pill toggles "→ I gave" / "← I received" in direction colours | Material segmented button | MAJOR |
| Person | Search, avatar chips, and a dashed coral "+ New" | Dropdowns; `NeedsPerson` if no people exist | **MAJOR** |
| CTA | Full-width coral "Save gift →" with "Add more details (optional)" (aqua) beside it | Save at the end; details always expanded | **MAJOR** |
| Third-party giver/recipient | Not visible | Supported (Grandma → Martin) | **ADDED** (must survive under "more details") |
| Photo, price, occasion, date, notes | "More details" | In the form | ADDED → belongs under "Add more details" |
| Edit gift | Not designed | Same form | **MISSING DESIGN** (reuse the steps, all expanded) |

### 3.6 Secondary flows (no approved design)

The following are all **MISSING DESIGN**, so the design system should be applied by analogy. Severity
against the design language is MAJOR wherever they use bare top bars and flat rows, which is all of them:
- gift idea detail, editor, and mark-as-given;
- occasion editor, own occasions, and occasion plan;
- wishlists, wishlist detail and editor, and wish detail and editor;
- wishlist access (invite, members), shared wishlists, shared wishlist (reservations), and join;
- settings and account deletion;
- sync attention;
- every auth screen.

### 3.7 States

| State | Design | Current | Severity |
|---|---|---|---|
| Initial loading | CLAUDE.md §28: skeleton only without content | Full-screen spinner (`LoadingContent`) | MAJOR |
| Empty | Editorial (imagery implied) | Centred text (`MessageContent`) | MAJOR |
| Pending sync | "Saved on this device · Waiting to sync" | Home sync line, photo note, SyncAttention | MATCH (wording) / MINOR (placement) |
| Offline / refresh error | Cached content stays, retry | Sharing screens: `OnlineContent`; owned data local | MATCH (behaviour) / MISSING DESIGN (visual) |

---

## 4. Navigation findings and target

1. **Destinations:** Home, People, Gifts, Lists. Settings is not a destination; it is reached from the
   avatar in the Home hero (and the rail foot on tablet). `AppSection.SETTINGS` stays for the rail and
   for route mapping. The phone bar shows exactly four items.
2. **Central add:** one "+" in the bar on phone and at the top of the rail on tablet. It opens a
   **sheet** offering Gift, Gift idea, Person, Wish, and Occasion, each going to the existing Add route.
   - Per-list FABs become redundant on phone; remove them only after the "+" ships.
   - The Wishlist detail "Add wish" FAB stays, because there the list is the context.
   - Not a destination (§29).
3. **Nav graph:** keep the single `GiftiaryNavHost` and the type-safe routes, with no route changes.
   - Add a phone `Scaffold(bottomBar)` in `AdaptiveNavigationFrame` for COMPACT, reusing
     `AppSection`, `routeSections` and `sectionNavOptions` (save/restore state, `launchSingleTop`,
     `popUpTo` start).
   - The bar replaces the `showSectionButtons` path in Home (delete `SectionButtons`).
4. **Bar visibility:**
   - Shown on the four top-level roots: Home, People, GiftHistory, Wishlists.
   - Hidden on detail, editor, auth, join, sync attention and settings. This is the conventional
     choice and the design shows top-level screens only; ambiguity A2.
   - Visibility is derived from the current back-stack entry: a set of top-level route classes, tested
     like the `routeSections` exhaustive test.
5. **Back stack:** a section tap uses the existing `sectionNavOptions`, and back from a root goes to
   Home and then exits. Detail screens keep their back arrow.
6. **Deep links:**
   - `giftiary://auth/*` is handled in `MainActivity`/auth before the nav host, so it is unaffected.
   - The wishlist invite (`JoinWishlist`, token in memory via `PendingWishlistJoin`) opens over the
     current section with the bar hidden. Back returns to the section root.
   - No new deep links.
7. **State restoration:** `sectionNavOptions` already uses `saveState`/`restoreState`. The "+" sheet
   needs `rememberSaveable` visibility only.
8. **Tablet:** the rail keeps its position.
   - Add the coral "+" FAB at the top (`NavigationRail(header = …)`).
   - Apply the aqua pill indicator and relabel to "Lists".
   - Add the aqua wave decoration at the foot, which is decorative only and must not take the Settings
     slot (A3).

---

## 5. Design-system findings

| Element | Current | Design | Verdict |
|---|---|---|---|
| Palette | Semantic `GiftiaryColors`, dark per §31, light ivory / teal / coral | Same, plus **aqua card fill** (~#BFE9E3 light), **beige grouped surface**, **yellow hero gradient**, dark-teal hero card | **Retain**; add the tokens `surfaceAqua`, `surfaceWarm` (beige), `heroGradient*` and `heroTeal` with dark variants (`heroFallback` already exists) |
| Typography | Lora display/headline, Public Sans otherwise | Same, plus a letter-spaced caps eyebrow, a large Lora day number, and the script tagline | **Retain**; add `eyebrow` and `numeral` styles. Script tagline: A5 (new font vs Lora italic) |
| Spacing | 4…48 scale | Generous | **Retain** |
| Radii | 8 / 10 / 12 / 16, "should not read as a stack of rounded cards" | Hero ~32, cards ~24, pills full | **Restyle**: the code comment contradicts the design. Add `card` (~20–24) and `hero` (~28–32); pills use `CircleShape`. Inputs and dialogs stay |
| Primary button | Coral, radius 10 | Coral, full-width; pill on in-card CTAs ("Plan gift") | **Restyle** (shape) |
| Secondary button | Outlined grey | Aqua filled ("Add more details") or text link ("+ Add idea") | **Restyle** |
| Cards | None. Only `ReminderCard` uses `Surface`; lists are flat | Card-based (Coming up, occasion, history, info table) | **Structurally wrong**: new `GiftiaryCard` (aqua / warm / teal variants) |
| List rows | `ListRow`: text only | Leading avatar or icon tile, trailing meta/chevron, selected fill | **Restyle**: add leading/trailing slots |
| Avatars | `Monogram` in People only | Initial avatars everywhere (rows, chips, hero) | **Retain and promote** `Monogram` to components |
| Icon tiles | None | Rounded tiles with a gift icon | **Missing**: new component |
| Chips | `FilterChip` | "All" dark filled; Given / Received outline in direction colours with arrows | **Restyle** |
| Direction toggle | `SegmentedButton` | Two pills with arrows in direction colours | **Structurally wrong** for this use: replace |
| Person picker | Dropdown | Search, avatar chip row, and "+ New" | **Structurally wrong**: new component |
| Inputs | Outlined, radius 8 | Filled, rounded, with a search leading icon | **Restyle** (also add a search field) |
| Dialogs | AlertDialog | Not shown | Retain; the "+" action and the photo source **become bottom sheets** (`ModalBottomSheet`), the phone convention for a choice list (A6) |
| FAB | Per-list extended FAB | Single central "+" | **Structurally wrong** on phone; keep the component for Wishlist detail |
| Top bar | Lora title on the page background | Root screens: large Lora title, subtitle and search in content (no app bar); flows: round back button and eyebrow | **Restyle**: root screens drop `GiftiaryTopBar`; details keep a lighter variant |
| Navigation | Rail only | Bottom bar with wave and central "+"; rail with "+", pill indicator and wave | **Missing / restyle** |
| Imagery | None | Hero illustration, gift-card illustrations, corner art | **Missing assets** (A7): need exported vector or raster from the designer |
| Image aspect | Gift photo uncropped, at most 360 dp tall | Recent-gift cards are about 4 : 5 tiles | Card thumbnails need a **cropped** display (`ContentScale.Crop` in a fixed tile) separate from the detail's uncropped view; decide whether photos replace illustrations (A8). Do not change the detail behaviour approved in the camera follow-up |

---

## 6. Newer functionality: where it fits

| Feature | Placement in the design |
|---|---|
| Gift photos and the camera/gallery choice | Add gift: under "Add more details". Gift detail: photo as the hero in place of the icon tile when present, otherwise the icon tile. Recent-gifts card: cropped thumbnail when a photo exists, illustration otherwise. The source choice becomes a bottom sheet (UI change only; the `GiftPhotoField` contract is unchanged) |
| Offline / pending-sync feedback | A quiet status line under the Home hero ("Saved on this device · Waiting to sync"), tappable to SyncAttention. Per-item "waiting to upload" stays small on detail. Never a banner implying failure (§16, §28) |
| Wishlist sharing / reservations / invites | "Lists" tab: own wishlists, then a "Shared with me" section (current SharedWishlists). Owner share action on the list header → WishlistAccess. Guest reservation states as pill chips (AVAILABLE / RESERVED_BY_ME / RESERVED_BY_OTHER); the owner view never shows reservation UI (§24, unchanged). The join screen uses the flow-header style |
| Reminders | A card under the hero (current `ReminderCard`, restyled warm) |
| Own (person-less) occasions | Footer link in the Coming up card, and an entry from the "+" sheet ("Occasion") |
| Gift ideas tab in history | Keep as the fourth chip after All / Given / Received, or move ideas to People only (A4) |
| Provider auth | Auth screens restyled with the hero/eyebrow language; provider buttons stay secondary-style with marks; flags unchanged |
| Account deletion and privacy | Settings (from the avatar): profile, reminders, privacy policy link, sign-out, then a separated "Delete account" danger area. Deletion screens use the message-state style |
| Third-party gifts | Under "Add more details": giver / recipient override (keeps the Grandma → Martin support) |

---

## 7. Phased roadmap

Ship only after phases 1–5 at least. **Do not ship the current UI first.**

| # | Phase | Screens | Data / domain / nav changes | Risk | Tests |
|---|---|---|---|---|---|
| 0 | **Design sources** | — | none | Blocks pixel review | — (owner commits phone designs and assets to `docs/design/`) |
| 1 | **Foundation** | tokens (aqua, warm, hero, teal-hero), radii `card`/`hero`, `eyebrow`/`numeral` type, `GiftiaryCard`, icon tile, `Monogram` in components, `ListRow` slots, direction pills, restyled chips/buttons, search field, illustration assets | none | Low; touches shared components used by every screen | Compose unit tests for new components; existing layout tests stay green; dark/light contrast check on new tokens |
| 2 | **Navigation shell** | phone bottom bar with wave and "+", rail "+" and pill and wave, "+" sheet, remove Home `SectionButtons`, "Lists" label, avatar → Settings | **nav shell only** (frame + visibility set; no route changes) | Medium: back stack, deep-link entry, state restore | `routeSections`/top-level exhaustive test, bar visibility per route, section re-select restores state, invite link opens with bar hidden, back from root |
| 3 | **High-impact screens** | Home (hero, Coming up card, Recent gifts), People list + Person detail, Gift history + detail, Add a gift (three steps + more details) | Recent gifts: a use case over the existing gift repository (read only). Search: ViewModel filter on loaded lists. Add-gift steps: UI only over the current `GiftEditorViewModel` state | Medium–high: the editor's layout tests and VM contract; T3–T6 adaptive tests | Rewrite the affected layout tests (HomeLayoutTest, GiftEditorLayoutTest, people/history list-detail), VM tests for search/recent, step-flow Compose tests incl. third-party and photo under details |
| 4 | **Secondary flows** | gift ideas, occasions, wishlists, wishes, settings, sync attention, auth, account deletion | none | Low–medium (breadth) | Existing screen tests updated; previews |
| 5 | **New-feature integration** | photo in detail hero and recent cards (cropped thumbnail), pending-sync line, sharing in Lists, reservation pills, photo-source sheet | none (photo thumbnail reuses `GiftPhotoRepository.load`) | Medium: reservation privacy must stay backend-enforced; photo detail stays uncropped | Owner view has no reservation node; thumbnail crop vs detail fit; existing photo/sync tests unchanged |
| 6 | **Adaptive** | apply tablet proposals: Home 2-col, two-pane People/History restyled, Add gift side-by-side CTAs | none | Low–medium | Existing T1–T6 tests updated to the new structure |
| 7 | **Accessibility / localization** | content descriptions for avatars/tiles/"+", touch targets ≥48 dp, font scale 200 %, HU string lengths (eyebrow, chips, "DAYS"/"NAP") | none | Low | Semantics assertions, HU layout smoke, font-scale previews |
| 8 | **Screenshot regression** | key screens × light/dark × EN/HU × compact/expanded | none | Low; new tooling decision (D3 said no Roborazzi until needed — now needed, A9) | Screenshot baselines reviewed against `docs/design/` |

---

## 8. Open ambiguities

- **A1:** The approved phone screens are not in the repo. Commit them, with the tablet zip, to `docs/design/`.
- **A2:** Should the bottom bar be hidden on detail and editor screens? The proposal is to hide it.
- **A3:** Where does Settings sit on phone? The avatar in the hero is the proposal. On tablet, is it
  kept at the rail foot next to the wave?
- **A4:** Should the Gift ideas tab stay in Gift history (as the fourth chip) or move to People
  only?
- **A5:** Is the script tagline font a new font asset, or Lora italic? Adding a font means a new
  resource, not a dependency.
- **A6:** Should the "+" choices and the photo source be a bottom sheet or a dialog? The proposal is a
  bottom sheet.
- **A7:** Illustration assets (hero gift stack, card art, corner art) are needed as SVG or vector
  exports from the designer. They are not to be invented.
- **A8:** In Recent gifts, should a user photo replace the illustration?
- **A9:** Which screenshot tooling should be used? Introducing Roborazzi or Paparazzi is a dependency
  decision.
- **A10:** Is the light palette the design's light theme (the tablet images are light)? Dark-theme
  versions of the new hero, aqua and beige surfaces need designer values.
