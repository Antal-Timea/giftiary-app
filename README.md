# Giftiary

Giftiary helps people remember, plan and share gifts.

- `giftiary-android/` — the native Android app (Kotlin, Jetpack Compose, Room). It is offline-first:
  everything is stored on the device first and works without a network.
- `supabase/` — the Supabase backend the app syncs with: schema, row level security, the sync
  ledger and wishlist sharing. See [supabase/README.md](supabase/README.md).
