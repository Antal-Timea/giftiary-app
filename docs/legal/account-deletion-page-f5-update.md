# Account-deletion page: F5 update (for the website)

> **Status:** **published.** These replacements went live on 12 November 2026 in website PR #9
> (`Antal-Timea/giftiary-website` master `a675918`), together with the 12 November privacy policy,
> whose website copies are byte-identical to `docs/legal/privacy-policy-{en,hu}.md`. The checks under
> "After publishing" passed (reviewer and owner on 12 November; GET again on 13 November 2026).
> This file is kept as the record of the change.
>
> The page https://giftiary.app/account-deletion/ is served from the website repository
> (`site/account-deletion/index.html`, deployed to Netlify by the owner); that source is not in this
> repository. The "Old" paragraphs were taken from the live page on 12 November 2026; each was
> replaced whole, and nothing else on the page changed.

## Hungarian

### 1. "Fontos: a többi eszközöd"

Old:

```html
<p>Ha a fiókot más eszközön is használtad, azokon az eszközökön az adatok <strong>nem törlődnek automatikusan</strong>. Ezek az eszközök idővel kijelentkeznek, és a törölt fiók a szokásos módon már nem érhető el rajtuk, de a helyi Giftiary-adatbázisban és az alkalmazás tárhelyén a fiók adatai, ajándék- és személyfotói fizikailag megmaradhatnak. Ezeket a helyi másolatokat úgy távolíthatod el, hogy az adott eszközön <strong>törlöd a Giftiary alkalmazás adatait, vagy eltávolítod az alkalmazást</strong>.</p>
```

New:

```html
<p>Ha a fiókot más eszközön is használtad, az alkalmazás aktuális változata azon az eszközön <strong>magától törli</strong> a fiók helyi adatait, ajándék- és személyfotóit, ha korábban eszközigazolást regisztrált a fiókhoz. Ez nem azonnali távoli törlés: akkor történik meg, amikor az eszköz online van, és a bejelentkezését elutasítják (amikor az alkalmazás legközelebb elindul, előtérbe kerül vagy megújítja a bejelentkezést), és csak azután, hogy a szerverünk megerősítette, hogy az igazolás már nem tartozik létező fiókhoz. Ha a szerver nem érhető el, vagy a válasza nem egyértelmű, semmi nem törlődik, és az eszköz később újra rákérdez. Az eszközön lévő más fiókok adatait ez nem érinti.</p>
<p>Az alkalmazás régebbi változata, illetve az a telepítés, amely soha nem regisztrált igazolást (például mert bejelentkezve soha nem volt online), megtarthatja a fiók adatait és fotóit a helyi Giftiary-adatbázisban és az alkalmazás tárhelyén. Ezeket a helyi másolatokat úgy távolíthatod el, hogy az adott eszközön <strong>törlöd a Giftiary alkalmazás adatait, vagy eltávolítod az alkalmazást</strong>.</p>
```

### 2. Email request paragraph

Old:

```html
<p>A készülékeiden lévő helyi adatokat távolról nem tudjuk törölni: ezeket az alkalmazás adatainak törlésével vagy az alkalmazás eltávolításával távolíthatod el.</p>
```

New:

```html
<p>A készülékeiden lévő helyi adatokat távolról nem tudjuk törölni. Az alkalmazás aktuális változatai a fent leírt módon maguktól eltávolítják őket; egyébként az alkalmazás adatainak törlésével vagy az alkalmazás eltávolításával távolíthatod el őket.</p>
```

## English

### 1. "Important: your other devices"

Old:

```html
<p>If you used the account on other devices, the data on those devices is <strong>not deleted automatically</strong>. Those devices will eventually be signed out and will no longer give normal access to the deleted account, but the account's data and its gift and person photos may physically remain in their local Giftiary database and app storage. You can remove these local copies by <strong>clearing the Giftiary app's data or uninstalling the app</strong> on that device.</p>
```

New:

```html
<p>If you used the account on other devices, a current version of the app on such a device <strong>removes the account's local data and its gift and person photos by itself</strong>, if it registered a device proof for the account earlier. This is not an immediate remote wipe. It happens when that device is online and its sign-in is refused (when the app next starts, returns to the foreground or renews its sign-in), and only after our server has confirmed that the proof no longer belongs to an existing account. If the server cannot be reached or its answer is unclear, nothing is deleted, and the device asks again later. Other accounts' data on that device is not affected.</p>
<p>An older version of the app, or an installation that never registered a proof (for example because it was never online while signed in), may keep the account's data and photos in its local Giftiary database and app storage. You can remove these local copies by <strong>clearing the Giftiary app's data or uninstalling the app</strong> on that device.</p>
```

### 2. Email request paragraph

Old:

```html
<p>We cannot delete the local data on your devices remotely: you can remove it by clearing the app's data or uninstalling the app.</p>
```

New:

```html
<p>We cannot delete the local data on your devices remotely. Current versions of the app remove it by themselves as described above; otherwise you can remove it by clearing the app's data or uninstalling the app.</p>
```

## After publishing

Verify with GET:
- https://giftiary.app/privacy/ shows "12 November 2026" / "2026. november 12." and the device-proof wording;
- https://giftiary.app/account-deletion/ contains none of the "Old" paragraphs and contains the "New" ones in both languages;
- `git diff` of the website PR touches only the policy sources, the generated privacy page and `site/account-deletion/index.html`.
