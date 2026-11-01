# Approved design vs current app: UI audit

Status: read-only audit, 2026-11-01, against `develop` @ `5509652` (code identical to `d7f059c`).
No product code, resources, backend, website or Play Console were changed.

Conclusion: **the current UI is not release-ready against the approved design.** The colors, fonts and
spacing tokens are mostly right. The screen structure is not: navigation, Home, the cards and imagery,
the add-gift flow, search, and list styling all differ. The redesign is mostly UI-only. It needs one
navigation-shell change and no data or domain change, except search, which can filter in the ViewModel.

---

## 0. Update: approved designs recovered (2026-11-01)

The approved designs have been found and committed. They are the 39 artboards marked `APPROVED - ` on
Claude's design canvas "Gifty — Visual Direction Exploration". They are now in
**`docs/design/approved/`**: rendered PNGs, the verbatim `.dc.html` sources, and the canvas assets. The
README there lists every board and the approval evidence for it.

**Where this section and §1–§8 disagree, this section wins.** Below:
- "**Visible**" means the fact can be seen in an approved artboard; the file is named.
- "**Inference**" marks this audit's proposal for functionality the approved boards do not cover. It
  is not an approved design fact.

### 0.1 Facts verified from the approved pixels

| Topic | What the approved artboards show (Visible) | Source file(s) |
|---|---|---|
| Bottom navigation | Four destinations: **Home, People, Gifts, Lists**, with stroked icons (house, people, gift, list). The selected item is filled dark teal `#17423E` with a 700 label; unselected items are `#547370` with a 600 label at 10.5. The ivory bar has a 28 px top radius and tucks under a 52 dp aqua `#BFEAE3` **wave**. | `phone-home`, `ds-04-navigation-content` |
| Central + / Quick Add | A 58 dp coral `#D6285A` circle with a 3 px ivory ring, centred on the wave, half above the bar. "Never a fifth destination"; it always opens the **Quick Add bottom sheet**: Remember (Gift I gave, Gift I received), Plan (Gift idea, Wishlist item), Organize (Person, List). | `phone-quick-add-sheet`, `ds-03`, `ds-04` |
| Where the bar shows | **Root** screens (Home, People, Gifts, Lists) have the bar and no back arrow. **Pushed** screens show the bar *with* the wave and + (Gift detail, Gift idea detail, Wishlist detail owner and guest, Mark as given): "navigation may stay or go depending on depth". Their back control is a 48 dp aqua circle. **Task flows** hide the bar: Add gift, Add gift idea, Add/Edit person, Add/Edit wish, Create list, Sign in. Also no bar on Profile, Settings, Share & Invite, onboarding and the error state. | `ds-04`, each phone board |
| Profile / Settings entry | The "T" avatar top right of the Home hero is labelled "Your profile". Profile (avatar, name, email, Edit profile, stats, **Settings**, Sign out) leads to Settings. | `phone-home`, `phone-profile`, `phone-settings` |
| Home hero | **Editorial photograph** (hands holding a gift stack) on warm yellow `#E3A73A`, with a 32 dp bottom radius. Text: "GIFTY" wordmark, Lora "Who are we making happy next?", the date, and a **Caveat** handwriting line "Small gifts, big stories ♡". | `phone-home`, `ds-06` |
| Coming up | Aqua `#BFEAE3` band, radius 32 at full bleed. Each row has a 3 dp coral (nothing planned) or teal (planned) marker, a monogram, the name, "Birthday · date", the idea count, a plan status with an icon, a Lora day count with "DAYS", and a chevron. Also on People, above "All people". | `phone-home`, `phone-people` |
| Recent gifts | "Recent gifts / Your memory collection" with "See all", and **photo cards** of the gifts. | `phone-home` |
| People | Lora 28 title with a standfirst, search field, Coming-up band, then "All people" rows: a 48 dp **photo avatar** or aqua monogram, name, "relationship · next birthday", a chevron, and indented hairline dividers. | `phone-people`, empty and no-results states |
| Person detail | Full-bleed **photo hero** with the name and relationship in white, plus back and edit circles. Aqua occasion card with a coral pill "Plan gift". "Gift ideas" rows with icon tiles and "+ Add idea". "History with Peter" on parchment, with a year and occasion overline and a two-column grid of **photo cards** with direction lines. | `phone-person-detail` |
| Gift history | Lora title, standfirst, search, and chips **All** (dark filled) / **→ Given** (coral outline) / **← Received** (teal outline). Lora year headings, caps month overlines, rows with a 52 dp **photo thumbnail** or an aqua gift-glyph tile, and "direction · person · occasion" in direction colour. **There is no Ideas chip.** | `phone-gift-history` |
| Gift detail | **The gift photo is a full-bleed hero** with "→ Given to Peter" and a Lora title on it, plus back and overflow circles. Then a person row, an aqua info block (date, price) and the note in Caveat. The bar and + are present. | `phone-gift-detail`, `phone-state-gift-pending-sync` |
| Add gift | Aqua back circle, "GIFTY" wordmark, Lora 36 "Add a gift", standfirst. Three numbered steps: direction pills (selection teal/aqua, **not** coral); person search plus monogram chips plus a dashed coral "New"; gift name. Then coral "Save gift →" and an aqua "Add more details (optional)" stacked **vertically**. The expanded form adds Occasion, Date, Price + currency, a **Photo** tile (84 dp dashed square), and Notes, with Save at the end. | `phone-add-gift-quick`, `phone-add-gift-details` |
| Typography | Lora 700 for display 36, screen title 28, sheet title 23, editorial section 22. Lora 600 at 17 for section titles. Public Sans for everything functional, from 15 down to 10.5. Caveat 500 for one handwriting line on photography only. No italics. | `ds-02` |
| Colours (light) | Background `#FBF7EE`, parchment `#F4EEDF`, field white, text `#17423E` / `#47615D` / `#2C5852` / `#6E837D`, placeholder `#7C948F`, aqua `#4FD6C2` (tints 0.14–0.35), aqua band `#BFEAE3`, aqua pale `#DDF5F0`, coral `#D6285A`, yellow `#E3A73A`, received teal `#0F5F56`, nav muted `#547370`. | `ds-01` |
| Spacing | 4 / 8 / 12 / 16 / 20 / 22 / 24 / 26 / 32. The gutter is 22 on browse screens and 24 on task screens. | `ds-02` |
| Shape | Fields have radius 16; the primary button 18; pills 999; the Coming-up band 32 (24 inset); hero bottoms 32; row thumbnails 14 (gift) and 16 (wish). Content stays **uncontained**: rows, whitespace and hairlines, with "no card in sight". | `ds-02`, `ds-03`, `ds-04` |
| Imagery | **Photography, not illustration**, at three levels: editorial heroes; supporting cut-outs at 0.26–0.50 opacity on creation, sign-in and profile screens; none on utility screens. | `ds-06`, `source/assets/` |
| States | Offline: "Offline · 2 changes waiting to sync" line above content. Pending sync: "Waiting to sync · Saved on this device". Refresh failed: "Couldn't refresh · Showing saved data · Try again" above cached content. Plus a skeleton Home, People empty and no-results states, and a generic error with Try again and Go back. | `phone-state-*`, `ds-05` |
| Sharing | Lists shows "Your lists" (privacy stated in words, e.g. "Only you") and "Shared with you". The wishlist owner sees "Shared with family · Manage" and no reservation state. A guest sees "I'll get this" / "You're getting this · Undo" / "Reserved". Share & Invite: people with access and "Share with someone", with the note "gift reservations stay hidden from the list's owner". | `phone-lists`, `phone-wishlist-owner`, `phone-wishlist-guest`, `phone-share-invite` |
| Auth / account | Sign in has "Continue with Google", email and password, "Forgot password?", Sign in, and "Create account". There are three onboarding chapters (Remember, Plan, Share). Settings has Language, Appearance, Notifications (occasion reminders, gift planning reminders), Privacy, "Your data", About, Sign out and **Delete account**. | `phone-sign-in`, `phone-onboarding-*`, `phone-settings` |

### 0.2 Corrections to §1–§8

- **§1:** The approved phone screens are no longer missing. The tablet zip is the canvas's row 16,
  which is **not approved** (a "proposed large-screen layout"); it is a proposal, not an authority.
- **§3, §5:** "Palette MATCH" was wrong for the **light** theme. The code's light tokens differ from
  the approved values:

  | Token | Code | Approved |
  |---|---|---|
  | background | `#F7F2E8` | `#FBF7EE` |
  | textPrimary | `#143A34` | `#17423E` |
  | primaryAction | `#C8234F` | `#D6285A` |
  | surfaceContext | `#E4EEEA` | `#BFEAE3` |
  | received | `#1A7A6D` | `#0F5F56` |
  | nav unselected | `#586E69` | `#547370` |

  Treat the light palette as **MAJOR (restyle tokens)**. The dark palette matches CLAUDE.md §31, but no
  dark board is approved (see R3 in the README).
- **§5 radii:** The approved values are field 16, primary button 18, pills, and the large aqua and hero
  32. The current 8 / 10 / 12 are structurally too small.
- **§5 cards:** The approved system is **not card-based**. It uses rows, whitespace and hairlines, with
  surfaces only where the content changes kind: the aqua band, the info block, parchment history, and
  sheets. The `GiftiaryCard` component proposed in §5 should be read as just these specific surfaces,
  not as a general card.
- **§3.4:** The Ideas chip has no approved place in Gift history; ideas live on Person detail and Gift
  idea detail. Keeping it is a product decision: **inference**.
- **§4:** The bar is shown on pushed detail screens and hidden only in task flows and on
  Profile, Settings and Share. This reverses the earlier proposal. The Quick Add sheet's entries are as
  drawn; it has **no Occasion entry**.
- **§5 direction toggle:** Selection is teal/aqua; coral is only for creation. The coral outline on
  the "Given" chip in history is a filter, not a selected state.
- **§3.5:** "Save gift" and "Add more details" are stacked vertically on phone. Side by side is only
  in the unapproved tablet proposal.

### 0.3 Earlier ambiguities

| ID | Status |
|---|---|
| A1 | **Resolved:** approved screens in `docs/design/approved/` |
| A2 | **Resolved:** bar hidden in task flows, Profile, Settings and Share; shown on root and pushed detail screens (DS-04). Person detail drawn without wave and + is open as README R2 |
| A3 | **Resolved (phone):** Home avatar → Profile → Settings. Tablet: no approved design |
| A4 | **Resolved as design fact:** no Ideas chip in approved history. Whether to keep the existing tab is a product decision (inference) |
| A5 | **Resolved:** Caveat 500, one line, on photography only (needs a font resource) |
| A6 | **Partly resolved:** Quick Add is a bottom sheet (approved). The photo-source choice has no approved design |
| A7 | **Resolved, changed:** imagery is photography, not illustration; the approved assets are in `source/assets/`. Licensing is open as README R4 |
| A8 | **Resolved:** Recent gifts, history rows and Gift detail use the gift's photo; without one, an aqua gift-glyph tile (DS-04) |
| A9 | Open (tooling decision) |
| A10 | **Resolved for light** (DS-01 values above). Dark theme: no approved boards (README R3) |

### 0.4 Newer functionality and its approved reference

| Feature | Approved visual reference? |
|---|---|
| Gift photos | **Yes, for display and slot:** Photo tile in Add gift details, Gift detail hero, history thumbnails, Recent gifts cards. **No** for the camera / gallery choice and the photo-chosen state. |
| Offline / pending-sync | **Yes:** Home offline line, Gift detail pending-sync line, refresh-failed line. **No** for the SyncAttention screen. |
| Wishlist sharing / reservations | **Yes:** Lists, owner and guest wishlist, Share & Invite. **No** for invite acceptance (the join link) or wish detail. |
| Provider auth | **Google: yes** (Sign in). **Facebook: no.** |
| Account deletion / privacy | **Partly:** Settings rows "Privacy", "Your data" and "Delete account". **No** for the confirmation, progress and done screens. |
| Reminders | Settings toggles **yes**. The Home reminder card: **no**. |
| Own occasions / occasion plan | **No** approved screens. |

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

Superseded: see §0.3 for the current status of A1–A10, and the README in `docs/design/approved/` for
R1–R4.

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
