# Feature flags

Switches the app reads from the hosted Supabase project, so a feature can be offered or
withdrawn without a new release. They live in the table `public.feature_flags` (migration
`supabase/migrations/20261124100000_feature_flags.sql`):

| Column | Meaning |
|---|---|
| `key` | The flag's name, lower-case snake_case |
| `enabled` | `true` = on, `false` = off |

Anyone may read the table, signed in or not; nobody can change it through the API (row level
security, select-only grants). Only the project owner changes values, in the dashboard.

The app treats a missing row, a failed read or no network as **off**. A flag can only ever turn
something on.

## `facebook_login_enabled`

Whether the sign-in and account creation screens offer **Continue with Facebook**. It starts as
`false`. Email sign-in and Google are not affected by it.

The button appears only when **both** of these hold:

1. The build includes Facebook: `auth.facebook.enabled=true` in `local.properties` when the
   release was built (as for every release so far).
2. `facebook_login_enabled` is `true`.

The Facebook OAuth code and the Supabase provider settings stay in place either way; the flag only
hides the button.

### Before turning it on

- [ ] The Meta app is **Live** (not in Development mode), so every tester can sign in, not only
      the app's roles.
- [ ] Supabase → Authentication → Sign In / Providers → Facebook is enabled with the Meta app's
      ID and secret, as in the Gate A checklist (`docs/release-checklist.md`).

### Turn Facebook Login on

1. Open the project in the Supabase dashboard.
2. **Table Editor** → schema `public` → table `feature_flags`.
3. In the row `facebook_login_enabled`, set `enabled` to `true` and save.

Or **SQL Editor**:

```sql
update public.feature_flags set enabled = true where key = 'facebook_login_enabled';
```

### Turn Facebook Login off

The same, with `false`:

```sql
update public.feature_flags set enabled = false where key = 'facebook_login_enabled';
```

Do not delete the row to turn it off. A missing row also reads as off, but setting `false` keeps
the switch visible for next time.

### Check the current value

```sql
select key, enabled from public.feature_flags;
```

### When the change reaches the app

- The app reads the flag each time the sign-in or account creation screen opens. A screen that is
  already open keeps what it showed; leaving and reopening it (or restarting the app) picks up the
  change. Nothing is cached between app starts.
- Until the answer arrives, the screen shows email and Google straight away; the Facebook button
  appears when the flag reads as on.
- Signed-in users see no difference. Turning the flag off does not sign anyone out.
- **Turning it off also hides it from people who signed up with Facebook.** They have no Giftiary
  password. **Forgot password?** with the email Facebook shared, or Google with the same email,
  may let them in, but neither is verified on this project yet (see O2 in
  `docs/release-checklist.md`). Turn the flag off only if that is acceptable, or tell those testers
  first.

### Which builds honour the flag

Only builds that contain this change (after 0.1.0-beta.6). In **0.1.0-beta.6 and earlier**, the
button depends only on the build setting, so testers on those builds still see Facebook whatever
the flag says, until they update.
