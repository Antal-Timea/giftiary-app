# Giftiary — Claude Development Guidelines

This file defines the engineering principles, architectural decisions, and working preferences for the Giftiary project.

Read this file before making any code changes.

These rules apply to all implementation tasks unless a later explicit instruction overrides a specific rule.

---

# 1. Project Context

Giftiary is a production-quality native Android application for remembering, planning, and sharing gifts.

Core product concept:

**Remember → Plan → Share**

The application will eventually support:

- remembering gifts the user gave and received
- managing people important to the user
- birthdays and other occasions
- gift ideas and their lifecycle
- converting gift ideas into actual gifts
- wishlists
- private wishlist sharing
- guest reservations of wishlist items
- offline-first usage
- multi-device synchronization

This is also intended to be a high-quality portfolio project demonstrating senior Android engineering practices.

Do not treat it as a tutorial/demo application.

---

# 2. Platform

Giftiary is a native Android application.

Current Android project:

`giftiary-android/`

Do not convert it to Kotlin Multiplatform.

Do not create iOS code.

Do not reuse or copy code from older Giftiary implementations unless explicitly requested.

Older implementations may be used as product references only.

---

# 3. Technology Stack

Prefer the existing project configuration and dependencies.

Core stack:

- Kotlin
- Jetpack Compose
- Material 3
- Coroutines
- Flow
- Hilt
- Room
- KSP
- Navigation Compose
- kotlinx.serialization

Planned later:

- Supabase Auth
- Supabase Postgres
- Supabase Storage
- WorkManager

Retrofit should only be introduced if Giftiary needs a genuine external REST API.

Do not use Retrofit as an unnecessary wrapper around Supabase.

Do not add libraries merely because they might be useful later.

Before adding a dependency, determine whether the existing stack can solve the problem cleanly.

---

# 4. General Engineering Philosophy

Write code that an experienced Android engineer would be comfortable maintaining.

Prefer:

- simple solutions
- explicit behavior
- clear ownership
- immutable state
- meaningful naming
- small cohesive classes
- idiomatic Kotlin
- constructor injection
- testable business logic
- predictable data flow

Avoid:

- unnecessary abstractions
- premature generalization
- speculative infrastructure
- excessive interfaces
- generic base classes
- generic repositories
- generic use cases
- abstract factories without a concrete need
- excessive extension functions
- unnecessary wrappers
- large utility classes
- architecture for architecture's sake

Do not attempt to make every problem generic.

A small amount of duplication is preferable to a bad abstraction.

---

# 5. Keep the Code Human

The code should not look artificially generated or unnecessarily elaborate.

Avoid:

- comments that simply repeat the code
- comments explaining obvious Kotlin syntax
- excessive documentation on trivial classes
- verbose class names
- unnecessary helper methods
- excessive sealed hierarchies
- huge configuration files
- placeholder abstractions
- speculative TODOs
- creating files simply to make the architecture look more sophisticated

Comments should explain **why**, not **what**.

Good:

`// The owner must never receive reservation state from the backend.`

Bad:

`// Set the name.`

`name = name`

Prefer code that explains itself.

---

# 6. Architecture

Use a pragmatic single-module Clean Architecture.

Do not introduce multiple Gradle modules unless there is a demonstrated reason later.

Current package structure:

    app.giftiary

    data/
        local/
            dao/
            entity/
            mapper/
            converter/
        repository/

    domain/
        model/
        repository/
        usecase/

    presentation/
        navigation/
        home/
        people/
        gifts/
        lists/
        components/
        theme/

    di/

Not every package needs to contain files immediately.

Do not create empty architectural layers purely for symmetry.

---

# 7. Dependency Direction

The intended dependency flow is:

    Compose UI
        ↓
    ViewModel
        ↓
    Use Case
        ↓
    Domain Repository
        ↓
    Repository Implementation
        ↓
    Room / Remote data source

Presentation must not access DAOs directly.

Domain must not depend on Room, Compose, Supabase, or Android framework classes.

Persistence-specific metadata should generally remain outside domain models.

---

# 8. Vertical Slice Development

Prefer implementing one complete feature slice at a time instead of creating infrastructure for the entire application upfront.

Typical order:

    Domain
    ↓
    Persistence
    ↓
    Repository
    ↓
    Use case
    ↓
    ViewModel
    ↓
    Compose UI
    ↓
    Tests

For example:

    PersonEntity
    ↓
    PersonDao
    ↓
    PersonRepositoryImpl
    ↓
    PersonRepository
    ↓
    People use cases
    ↓
    PeopleViewModel
    ↓
    PeopleScreen

Finish and test a slice before expanding into unrelated features.

---

# 9. Offline-First Principle

User-owned Giftiary data is offline-first.

Room is the local source of truth.

The UI observes Room-backed state.

Normal user writes must succeed locally without requiring network connectivity.

The future flow is:

    User action
        ↓
    Room transaction
        ├── update local entity
        └── enqueue sync operation
        ↓
    UI immediately observes local result
        ↓
    background synchronization

Do not design normal local writes around successful network requests.

---

# 10. Atomic Local Writes

When a user-owned entity is changed, the entity mutation and corresponding sync queue mutation must happen atomically.

Example:

    Room transaction
    ├── INSERT Person
    └── enqueue CREATE Person

Similarly:

    Room transaction
    ├── soft-delete Person
    └── enqueue DELETE Person

Never allow:

    save entity
    transaction ends
    enqueue sync later

because the application could crash between those operations.

---

# 11. IDs

Use client-generated UUIDs for offline-capable entities.

The same UUID will eventually be used in:

- Room
- Supabase/Postgres

Do not create separate local and remote IDs.

Room stores UUIDs as TEXT through explicit converters.

---

# 12. Soft Deletion

User-owned synchronized entities use soft deletion.

Use:

`deletedAt`

Normal application reads should exclude deleted records.

Do not physically delete synchronized domain data during normal user actions.

Hard deletion may later exist only for deliberate cleanup/maintenance.

Avoid aggressive Room `CASCADE` deletion.

Deletion consequences should be controlled by domain logic.

---

# 13. Sync Metadata

Local synchronized entities may contain persistence metadata such as:

- ownerUserId
- version
- remoteState
- createdAt
- updatedAt
- deletedAt

Do not expose persistence metadata to domain models unless the domain genuinely needs it.

Current remote state:

    enum class RemoteState {
        LOCAL_ONLY,
        SYNCED,
    }

Do not add states such as:

- SYNCING
- RETRYING
- FAILED
- CONFLICT

without a demonstrated need.

Pending synchronization state is primarily represented by the sync queue.

For a local entity that has never existed remotely:

    remoteState = LOCAL_ONLY
    version = 0

---

# 14. Sync Queue

Synchronization is state synchronization, not event sourcing.

The queue represents what the remote state eventually needs to become.

There must be at most one pending sync operation per entity.

Key:

`entityType + entityId`

Operations:

    CREATE
    UPDATE
    DELETE

Operations must be coalesced.

Expected behavior:

    no operation + CREATE  -> CREATE
    no operation + UPDATE  -> UPDATE
    no operation + DELETE  -> DELETE

    CREATE + UPDATE         -> CREATE

    UPDATE + UPDATE         -> UPDATE

    UPDATE + DELETE         -> DELETE

For:

    CREATE + DELETE

of an entity that has never existed remotely:

- if another synced entity can reference it (Person, Occasion, GiftIdea), keep the pending
  CREATE; it creates the tombstone remotely
- if nothing can reference it (Gift) and none of its mutations is in flight, remove the pending
  operation entirely

Reason: a local child (e.g. a Gift of a deleted local-only Person) may still sync later, and the
remote parent must exist for its foreign key. A mutation already in flight may have committed, so
the deletion must still follow it.

Do not simply replace every existing queue entry with the latest operation.

The sync system sends the current Room state rather than replaying every user action.

---

# 15. Synchronization Direction

When full synchronization is implemented later, the default order is:

    PUSH local pending changes
    ↓
    PULL remote changes

This avoids overwriting unsynchronized local edits before they have been uploaded.

Use fixed dependency-aware entity ordering where required.

Do not build a generic dependency graph unless there is a real need.

---

# 16. Sync Failure Philosophy

A successful local save remains a successful user action even if remote synchronization fails.

The UI should eventually distinguish between:

    saved locally
    waiting to sync
    sync attention required

and actual save failure.

Do not present a network synchronization failure as if the user's local data was lost.

Cached content should remain visible during refresh and network failures.

---

# 17. Domain Rules — Person

`Person` is not the authenticated `User`.

A Person represents someone relevant to the user's gift history/planning.

The authenticated user does not need a Person record.

This distinction must remain throughout the architecture.

---

# 18. Domain Rules — Gifts

An actual historical gift is represented by `Gift`.

Direction:

    GIVEN
    RECEIVED

The current user can be represented by a null giver/recipient Person reference.

Example:

User gave Peter a gift:

    direction = GIVEN
    giverPersonId = null
    recipientPersonId = Peter

User received something from Peter:

    direction = RECEIVED
    giverPersonId = Peter
    recipientPersonId = null

The model must also support third-party relationships such as:

    Grandma → Martin

---

# 19. Domain Rules — Gift Ideas

`GiftIdea` is different from `Gift`.

Lifecycle:

    IDEA
      ↓
    PLANNED
      ↓
    PURCHASED

There is deliberately no:

`GIVEN`

GiftIdea status.

When an idea is marked as given:

    Room transaction
    ├── create Gift
    ├── archive GiftIdea
    ├── enqueue Gift sync
    └── enqueue GiftIdea sync

The resulting Gift should retain:

`sourceGiftIdeaId`

for provenance.

GiftIdea uses:

`archivedAt`

when converted into an actual Gift.

---

# 20. Money

Never use `Double` or `Float` for monetary values.

Use:

    data class Money(
        val amountMinor: Long,
        val currencyCode: String,
    )

Example:

`12990 HUF`

is stored as:

    amountMinor = 12990
    currencyCode = "HUF"

Currency codes use three uppercase letters.

Persistence should preserve the amount/currency pair consistently.

---

# 21. Occasions

An Occasion may represent:

- birthday
- name day
- anniversary
- Christmas
- custom occasion

Do not model occasions using only `LocalDate`.

The year may legitimately be unknown.

Use:

    day
    month
    year?

Example:

`12 May, unknown year`

is valid.

Calendar-invalid dates must be rejected.

February 29 with an unknown year is valid.

---

# 22. Wishlists and Wishes

Wishlist is user-owned and private by default.

A Wish belongs to a Wishlist.

Prefer deriving Wish ownership from its Wishlist rather than duplicating ownership unnecessarily.

Do not add reservation information directly to the Wish domain model.

Sharing and reservation are collaborative server-side concepts.

---

# 23. Wishlist Sharing

Sharing must not grant access to unrelated private data.

Sharing a Wishlist must never implicitly expose:

- Person records
- gift history
- GiftIdeas
- private occasions
- unrelated wishlists

Access is granted only to the shared wishlist and the data required to display it.

---

# 24. Reservation Privacy

Wishlist reservations are intentionally different from normal offline-first user data.

Reservations will be server-authoritative.

Do not implement optimistic offline reservation.

Reason:

Two users must never both believe they successfully reserved the same wish.

What the wishlist owner may know depends on whose list it is.

For the owner's own wishlist (the owner is the beneficiary), the surprise is kept. The owner must not know:

- whether a wish has been reserved
- who reserved it

and must not receive reservation state from the backend at all.

For a wishlist the owner created for another person (`person_id` is set), the owner may see only the
neutral fact that a wish is reserved (`Reserved` / `Lefoglalva`), so they do not buy the same gift.
The owner must never receive the reserver's identity, the reservation time, a count, or any other
detail that could identify who reserved it. Do not widen this exception beyond person-lists.

Guests may eventually see only:

    AVAILABLE
    RESERVED_BY_ME
    RESERVED_BY_OTHER

Do not solve owner privacy merely by hiding reservation information in Compose.

Privacy must be enforced at the backend/data-access level.

---

# 25. Backend Direction

The planned backend is Supabase:

- Auth
- PostgreSQL
- Storage
- Row Level Security

Do not introduce a custom backend server without a demonstrated requirement.

Private user-owned tables will be protected through RLS.

Collaborative operations such as:

- invitation acceptance
- reservation
- reservation cancellation

may use carefully designed PostgreSQL RPC functions.

Do not expose raw reservation records to clients.

---

# 26. Supabase Storage

When media storage is introduced, store stable storage paths in domain/persistence data.

Example:

`users/{userId}/people/{personId}/avatar.jpg`

Do not persist temporary signed URLs as the canonical media reference.

---

# 27. Compose State

Prefer immutable screen state.

ViewModels should expose state rather than individual mutable UI properties.

Use StateFlow where appropriate.

Compose screens should primarily:

- render state
- emit user actions

Keep business logic out of composables.

---

# 28. Loading and Error States

Use these conceptual states consistently:

    EMPTY
    NO RESULTS
    INITIAL LOADING
    FULL ERROR
    CACHED + REFRESH ERROR
    OFFLINE
    PENDING SYNC
    SYNCED

Rules:

## Initial loading

Show skeleton/loading only when there is no useful local content.

## Refresh

Keep cached content visible.

Do not replace useful content with a full-screen spinner.

## Refresh failure

Keep cached content visible and expose a retry mechanism.

## Offline

Keep locally available functionality usable.

## Pending sync

A local save is valid.

Use subtle messaging such as:

    Saved on this device
    Waiting to sync

Do not say:

`Save failed`

when the local transaction succeeded.

---

# 29. Navigation

Use type-safe Navigation Compose.

Main application destinations are expected to evolve around:

    Home
    People
    Gifts
    Lists

Quick Add is an action, not a navigation destination.

Expected navigation direction:

    Home
    People
    PersonDetail
    GiftHistory
    GiftDetail
    GiftIdeaDetail
    Wishlist
    AddGift
    AddGiftIdea
    AddPerson
    AddWish
    CreateList
    Profile
    Settings

Do not build routes before they are needed.

---

# 30. UI Design Direction

Giftiary has a warm, calm, premium visual identity.

Avoid generic Material-demo appearance.

Design direction:

- warm ivory backgrounds
- dark teal
- aqua support color
- coral for meaningful actions
- editorial imagery
- restrained rounded geometry
- generous spacing
- clear hierarchy

Typography direction:

- Lora for expressive/editorial typography
- Public Sans for functional UI typography

Do not implement arbitrary colors directly inside feature screens.

Use semantic design tokens.

The full design system should be implemented centrally.

---

# 31. Dark Theme

Dark theme uses semantic tokens rather than simple color inversion.

Existing design direction includes:

    background            #121F1D
    surface               #172725
    surfaceElevated       #1C302D
    surfaceContext        #1D3B36
    surfaceContextQuiet   #2A5A52
    inputSurface          #192B28
    navigationSurface     #182927

    textPrimary           #F2EDE1
    textSecondary         #AFC3BC
    textLabel             #BCD0C9
    textMuted             #8AA09A
    placeholder           #829993

    primaryAction         #D6285A
    onPrimaryAction       #FFF7F4
    primaryActionText     #FA8399

    support               #4FD6C2
    onSupport             #F2EDE1

    directionReceived     #79D9C6
    directionGiven        #FA8399

    border                #404E49
    divider               #2D3835

Do not scatter these literal values through composables.

---

# 32. Localization

The application should be designed for localization.

Do not hardcode user-facing text inside composables.

Use Android string resources.

Initial languages are expected to include:

- English
- Hungarian

Do not put localized strings into domain models.

---

# 33. Testing Philosophy

Test behavior that matters.

Prioritize tests for:

- business rules
- state transitions
- Room transactions
- synchronization queue coalescing
- repository behavior
- use cases
- ViewModel state
- important DAO queries
- migrations

Avoid tests that merely reproduce implementation details.

Do not generate dozens of trivial tests solely to increase coverage.

---

# 34. Room Schema

Keep:

`exportSchema = true`

Commit Room schema history to Git.

When the database schema changes after real development data exists:

- increment the database version
- provide a migration
- test the migration

Do not use destructive migration as the normal solution.

During the initial pre-production schema-design phase, schema v1 may still be corrected deliberately when explicitly requested.

---

# 35. Database Indexes

Think about actual query and synchronization patterns when defining indexes.

Common candidates include:

- ownerUserId
- foreign keys
- updatedAt
- frequently filtered status fields

Do not add indexes blindly, but do not wait for obvious performance problems either.

Foreign-key columns should normally be indexed.

---

# 36. Security

Do not place secrets in:

- source files
- Git
- BuildConfig values committed to Git
- example configuration containing real credentials

Never commit:

- service-role Supabase keys
- private tokens
- signing credentials

Supabase client applications may eventually contain the appropriate public/anon client key, but authorization must never depend on that key being secret.

Authorization belongs in RLS and server-side rules.

---

# 37. Git Hygiene

Make focused changes.

Do not modify unrelated files.

Do not reformat the entire project for a small feature.

Do not rewrite working code without a concrete reason.

Preserve existing conventions unless there is a clear improvement.

Generated build artifacts and local IDE configuration must not be committed.

Room schema exports are intentionally committed.

---

# 38. Dependency Changes

Before changing dependency versions:

1. inspect the existing version catalog
2. determine why the change is needed
3. consider compatibility with the existing stack
4. make the smallest necessary change
5. compile and test afterwards

Do not upgrade dependencies as an unrelated side task.

Newer is not automatically better.

---

# 39. Working Process

For each implementation task:

1. Read this `CLAUDE.md`.
2. Inspect the relevant existing files.
3. Understand the current implementation before editing.
4. Identify the smallest coherent change.
5. Implement it.
6. Add/update meaningful tests.
7. Run tests.
8. Compile the project.
9. Fix failures.
10. Review the diff for unnecessary changes.

Do not stop after generating code.

If the environment allows builds/tests, run them.

Do not ask the user to run commands that you can run yourself.

---

# 40. Do Not Silently Change Architecture

If an implementation request conflicts with an existing architectural rule:

- identify the conflict
- preserve the existing architecture by default
- explain the tradeoff before making a major architectural change

Do not silently introduce a new architecture because another approach is fashionable or technically possible.

---

# 41. Do Not Over-Scope Tasks

If asked to implement People, do not also implement Gifts.

If asked to add a repository, do not redesign navigation.

If asked to fix a database issue, do not redesign the UI.

Stay within the requested scope unless another change is required for correctness.

If you notice an unrelated improvement, mention it in the final report instead of automatically implementing it.

---

# 42. Definition of Done

A task is not complete merely because code was generated.

Unless the task explicitly says otherwise, completion means:

- implementation is complete
- project compiles
- relevant tests pass
- architecture remains consistent
- no unrelated files were modified
- no obvious TODO placeholders remain
- no known broken state was intentionally left behind

---

# 43. Final Self-Review

Before reporting completion, check:

## Architecture

- Did I preserve dependency direction?
- Did I avoid unnecessary abstractions?
- Did I accidentally expose persistence details to the domain?
- Did I maintain offline-first behavior?

## Data

- Are writes atomic where required?
- Are soft deletion rules preserved?
- Is sync metadata consistent?
- Are UUIDs used consistently?
- Are monetary values safe?

## UI

- Is business logic outside composables?
- Is state immutable where practical?
- Are strings localized?
- Are design tokens used instead of arbitrary values?

## Security

- Did I expose private information?
- Did I introduce credentials?
- Did I weaken future reservation privacy?

## Quality

- Does the project compile?
- Do tests pass?
- Did I change anything unrelated?
- Is the solution simpler than an equally correct alternative?

Fix problems found during this review before finishing.

---

# 44. Reporting Changes

After an implementation task, provide a concise report containing:

- what was implemented
- important architectural decisions
- files or areas changed
- tests added/updated
- build/test result
- any deliberate deviation from this document
- any important issue discovered but intentionally left outside the task

Do not produce a long tutorial explaining code that is already clear from the implementation.

---

# 45. Agent Handoff Workflow

When `docs/agent/CURRENT_TASK.md` exists, also read `docs/agent/WORKFLOW.md` before starting work.

Use:
- `docs/agent/CURRENT_TASK.md` for the current assignment;
- `docs/agent/STATUS.md` for your completion/audit report;
- `docs/agent/REVIEW.md` for reviewer follow-up.

This GitHub handoff replaces long manual copy/paste reports between agents. Follow the task's merge/implementation permissions exactly.
