# Giftiary – Adatkezelési tájékoztató

**Hatályos:** 2026. november 18.

Ez a tájékoztató azt írja le, hogyan kezeli a Giftiary Android-alkalmazás és a hozzá tartozó szolgáltatás a személyes adatokat. Az angol nyelvű változat tartalmilag megegyezik ezzel a szöveggel.

## 1. Az adatkezelő és elérhetősége

- **Adatkezelő:** Antal Tímea
- **E-mail (adatvédelmi ügyekben is):** support@giftiary.app
- **Weboldal:** https://giftiary.app

A Giftiary egy magánszemély által üzemeltetett alkalmazás. Adatvédelmi tisztviselő kijelölésére nincs szükség. Minden adatvédelmi kérdéssel és kéréssel a fenti e-mail-címen lehet fordulni hozzánk.

## 2. Röviden

- A Giftiaryben az ajándékokra emlékezhetsz, azokat tervezheted és megoszthatod. Személyeket, alkalmakat, ajándékötleteket, ajándékokat és kívánságlistákat rögzíthetsz.
- Az adataidat először a készülékeden tároljuk, majd fiókodhoz kötve szinkronizáljuk a szerverünkre, hogy több eszközön is elérd őket.
- **Nem** használunk analitikát, reklámot, követést vagy hirdetési azonosítót, és **nem** gyűjtünk helyadatot, névjegyeket vagy fájlokat a készülékedről. Fotót csak akkor kezelünk, ha te adsz hozzá egyet: egy ajándékhoz, egy ajándékötlethez, egy személyhez vagy egy kívánsághoz, illetve saját profilfotóként. Az általad megosztott kívánságlista tagjai látják a lista kívánságainak fotóit; a többi fotódat csak a te fiókod látja. Nincs fizetés az alkalmazásban.
- Az adataidat nem adjuk el, és nem adjuk át reklámcélra.
- A fiókodat az alkalmazásban bármikor törölheted (Beállítások → Fiók törlése), vagy az alkalmazás nélkül, e-mailben is kérheted a törlését (lásd 12. pont).

## 3. Milyen adatokat kezelünk

### 3.1. Fiókadatok

- **E-mail-cím:** a regisztrációhoz és a bejelentkezéshez szükséges.
- **Jelszó:** ha e-mail-címmel és jelszóval regisztrálsz. A jelszót a hitelesítési szolgáltatónk kizárólag biztonságos, visszafejthetetlen (hash-elt) formában tárolja; az alkalmazás nem tárolja.
- **Fiókazonosító:** egy véletlenszerű technikai azonosító, amelyhez az adataid kapcsolódnak.
- **Fiókállapot:** például a regisztráció és a megerősítés időpontja, az utolsó bejelentkezés ideje.
- **Eszközigazolás:** amíg be vagy jelentkezve, az alkalmazás minden telepítése létrehoz egy véletlenszerű titkos kódot, és csak azon a készüléken tárolja. A szerverünk ennek csak visszafejthetetlen (SHA-256 hash-elt) formáját tárolja a fiókodhoz kapcsolva, fiókonként legfeljebb 20 telepítésre. Kizárólag arra használjuk, hogy eldöntsük, egy készülék biztonságosan törölheti-e a fiókod helyi adatait, miután a fiókot törölték (lásd 12. pont). Nem hardver- vagy reklámazonosító, és magáról a készülékről semmit nem árul el.

### 3.2. Bejelentkezés Google- vagy Facebook-fiókkal (választható)

Ha a „Folytatás Google-fiókkal” vagy a „Folytatás Facebookkal” lehetőséget választod, a bejelentkezés a szolgáltató saját oldalán, böngészőben történik; a Google- vagy Facebook-jelszavadat nem látjuk. A szolgáltatótól a hitelesítési rendszerünk a következőket kapja meg és tárolja a fiókodnál:

- az e-mail-címedet (ha a szolgáltató megosztja) és azt, hogy igazolt-e;
- a szolgáltatónál használt nevedet és profilképed hivatkozását (URL);
- a szolgáltatói fiókazonosítódat.

Csak az alapvető bejelentkezési hozzáférést kérjük (e-mail és alapprofil). A Giftiary a szolgáltatótól kapott nevet és profilképet **nem jeleníti meg és nem használja**; a megjelenített nevedet nem tölti ki vele. A Google vagy a Facebook egyéb adataihoz nem férünk hozzá, és az alkalmazásban nincs Google- vagy Facebook-SDK.

Ha ugyanazzal az igazolt e-mail-címmel már van Giftiary-fiókod, a hitelesítési szolgáltató a Google- vagy Facebook-bejelentkezést ehhez a meglévő fiókhoz kapcsolhatja.

### 3.3. Profil

- **Megjelenített név (választható):** a profilodban (Profil szerkesztése) adhatod meg. Csak azok látják, akikkel kívánságlistát osztasz meg, vagy akiknek a megosztott listájához csatlakoztál. Ha nem adsz meg nevet, helyette „Giftiary-tag” jellegű általános felirat jelenik meg.
- **Profilfotó (választható):** egy kép, amelyet a Profil szerkesztése oldalon a rendszer fotóválasztójával választasz ki, vagy a kameraalkalmazással készítesz. Négyzetesre vágjuk, 640 × 640 képpontosra méretezzük, JPEG formátumba alakítjuk, és eltávolítjuk a metaadatait. **Csak te látod**: nem látják azok, akikkel kívánságlistát osztasz meg, és nem része a meghívóknak, tagságoknak vagy megosztott listáknak. Csak a szerverünkön tároljuk, a készülékeden nem marad meg; a cseréjéhez internetkapcsolat kell.

### 3.4. Az általad rögzített tartalom

- **Személyek:** név, kapcsolat (pl. „anya”, „barát”).
- **Alkalmak:** típus (pl. születésnap, névnap, évforduló, karácsony, egyéni), nap, hónap, opcionálisan év, egyéni alkalomnál cím.
- **Ajándékötletek és ajándékok:** megnevezés, irány (adott vagy kapott ajándék), ki adta vagy kapta, alkalom, ár vagy becsült ár, termékhivatkozás, megjegyzés, állapot.
- **Kívánságlisták és kívánságok:** a lista neve, a kívánság neve, megjegyzés, becsült ár, termékhivatkozás.
- **Ajándék-, ajándékötlet- és kívánságfotó (választható):** ajándékonként, ajándékötletenként és kívánságonként legfeljebb egy kép, amelyet te választasz ki a rendszer fotóválasztójával, vagy a készüléked kameraalkalmazásával készítesz. Mentés előtt kicsinyítjük (a hosszabb oldal legfeljebb 1600 képpont), JPEG formátumba alakítjuk, és eltávolítjuk a metaadatait (például a helyadatot és az eszköz adatait). Ha egy ajándékötletet átadottnak jelölsz, a fotóját nem másoljuk: az ötletből létrejövő ajándék fotója lesz, az archivált ötletnek pedig ezután nincs fotója.
- **Személy fotója (választható):** az általad rögzített személyenként legfeljebb egy kép, amelyet ugyanígy választasz ki vagy készítesz. Négyzetesre vágjuk, 640 × 640 képpontosra méretezzük, JPEG formátumba alakítjuk, és eltávolítjuk a metaadatait. Az ilyen fotó általában valaki mást ábrázol (lásd 5. pont).

Ezeket az adatokat te írod be; a készülékedről semmit nem olvasunk be automatikusan (például a névjegyeidet vagy a naptáradat sem). A fotóválasztó csak azt az egy képet adja át az alkalmazásnak, amelyet kiválasztasz; a többi fényképedhez nem férünk hozzá. Ha új fényképet készítesz, a kameraalkalmazás a felvételt a Giftiary egy ideiglenes, csak erre a célra létrehozott fájljába menti. A Giftiary ezt a fentiek szerint feldolgozza, az ideiglenes fájlt pedig a feldolgozás után azonnal törli. Ha a fotózás közben az alkalmazás leáll, a megmaradt ideiglenes fájlt egy nap elteltével, a következő fotókészítéskor törli (vagy addig a rendszer törölheti a gyorsítótár ürítésekor). A Giftiary a képet nem menti a galériádba; egyes kameraalkalmazások azonban a saját beállításaik szerint megtarthatnak egy másolatot, ezt a kameraalkalmazásban tudod ellenőrizni.

### 3.5. Megosztási adatok

- **Meghívók:** amikor egy saját kívánságlistádat megosztod, meghívó jön létre. A meghívó titkos kódját a szerver csak visszafejthetetlen (hash-elt) formában tárolja; a kód csak a megosztott hivatkozásban szerepel. A meghívó létrehozásának, lejáratának és esetleges visszavonásának időpontját is tároljuk.
- **E-mailes meghívók:** egy saját kívánságlistádra valakit az e-mail-címe megadásával is meghívhatsz. Ilyenkor a szerver tárolja a meghívott e-mail-címét (kisbetűsítve, a szóközök levágásával), a lista azonosítóját, a meghívó állapotát (függőben, elfogadva, elutasítva, visszavonva vagy lejárt), valamint a létrehozás, a lejárat (14 nappal a létrehozás után) és a lezárás időpontját. Az e-mail-címet kizárólag ehhez a meghíváshoz használjuk: hogy a meghívottnak egy meghívó e-mailt küldjünk (lásd 3.7. pont), és hogy a meghívó annak jelenjen meg az alkalmazásban, aki megerősített e-mail-címként pontosan ezzel a címmel jelentkezik be, és ő elfogadhatja vagy elutasíthatja. Elfogadás előtt a lista nevét, a megjelenített nevedet és – ha a listát egy személyed számára vezeted – a személy nevét látja. A címet reklám- vagy más célra nem használjuk. A meghívóban a meghívott fiókjának azonosítóját nem tároljuk, és neked sem jelezzük, hogy a címhez tartozik-e Giftiary-fiók. A meghívott e-mail-címét a függő meghívóid között csak te látod. Amikor a meghívó lezárul (elfogadják, elutasítják, visszavonják vagy lejár), az e-mail-címet töröljük belőle; ezután a meghívóid között sem jelenik meg.
- **Tagságok:** ki csatlakozott egy listához, és mikor.
- **Foglalások:** melyik tag foglalt le egy kívánságot, és mikor.

### 3.6. Technikai adatok

A bejelentkezés és a szolgáltatás biztonságos működéséhez a hitelesítési és adatbázis-szolgáltatónk technikai adatokat kezel, például a munkamenetek (bejelentkezések) adatait, a kérések időpontját, valamint az IP-címet és az eszköz/alkalmazás azonosító jellegű adatait (user agent), illetve rövid ideig megőrzött technikai naplókat.

Az alkalmazásba nem építettünk analitikai vagy hibajelentő eszközt. A Google Play a saját feltételei szerint összesített technikai statisztikákat (például összeomlási arányokat) tehet elérhetővé számunkra.

### 3.7. E-mailek

A regisztráció megerősítéséhez és az elfelejtett jelszó visszaállításához e-mailt küldünk a no-reply@giftiary.app címről. Ehhez az e-mail-címedet, az üzenet tartalmát (a megerősítő vagy visszaállító hivatkozást) és a kézbesítés technikai adatait kezeljük. Hírlevelet vagy marketing célú e-mailt nem küldünk.

**Meghívó e-mail a meghívott személynek (GDPR 14. cikk).** Amikor egy Giftiary-felhasználó egy e-mail-címet meghív egy kívánságlistára, erre a címre egy e-mailt küldünk, magyarul és angolul. Ebben leírjuk, hogy egy Giftiary-felhasználó meghívta egy megosztott kívánságlistára (a felhasználó és a lista megnevezése nélkül); hogy ha már használja a Giftiaryt, nyissa meg az alkalmazást, egyébként 14 napon belül (a megadott dátumig) ezzel a címmel regisztrálva látja a meghívót; hogy a címet az adta meg, aki meghívta, és csak ehhez a meghíváshoz használjuk, marketingre nem; mikor töröljük a címet; a jogalapot; hogyan tiltakozhat és gyakorolhatja a jogait; valamint egy hivatkozást erre a tájékoztatóra. Az e-mail ugyanaz, akár tartozik a címhez Giftiary-fiók, akár nem. Ha a meghívót függő meghívó mellett ismét elküldik, új e-mailt nem küldünk. Ehhez a meghívott e-mail-címét, a meghívó lejárati dátumát és a kézbesítés technikai adatait kezeljük.

### 3.8. A készülékeden tárolt adatok

- Az adataid helyi másolata az alkalmazás saját adatbázisában, hogy internet nélkül is működjön.
- Az ajándék-, ajándékötlet-, személy- és kívánságfotóid helyi másolata az alkalmazás saját tárhelyén, valamint a feltöltésre vagy a fájltárolóból való törlésre váró fotók listája.
- Egy veled megosztott, más által vezetett kívánságlista kívánságainak fotói akkor töltődnek le, amikor a lista megjeleníti őket, és csak az alkalmazás memóriájában maradnak, amíg a listát használod; az alkalmazás nem menti őket a készülék tárhelyére.
- Egy épp kiválasztott profilfotó, csak a feltöltéséig. Ha nem sikerül feltölteni, és nem próbálod újra, az alkalmazás később automatikusan törli.
- Fotókészítés közben egy ideiglenes képfájl az alkalmazás gyorsítótárában (lásd 3.4. pont).
- A bejelentkezési munkamenet (amíg be vagy jelentkezve).
- Az emlékeztetők beállítása (be/ki) és az utolsó emlékeztető napja, fiókonként.
- Egy jelölő arról, hogy a bevezető képernyőket ezen a készüléken már megnézted (készülékenként, nem fiókhoz kötve).
- Egy ideiglenes jelölő, ha egy fióktörlés még nem fejeződött be a készüléken.
- Minden, ezen a készüléken bejelentkezett fiók eszközigazolásának titkos kódja (lásd 3.1. pont), amíg az adott fiók helyi adatai a készülékről nem törlődnek.

Az Android biztonsági mentése és az új készülékre való átvitel (ha a készülékeden be van kapcsolva) a munkamenetet, a helyi adatbázist, a fotókat, a fióktörlési jelölőt és az eszközigazolások titkos kódjait **nem** viszi át; az emlékeztető-beállításokat és a bevezető jelölőjét igen. A biztonsági mentést a Google a saját feltételei szerint végzi, a te készülékbeállításaid alapján.

### 3.9. Ha írsz nekünk

Ha e-mailben megkeresel minket, a leveledet és az e-mail-címedet a kérésed megválaszolásához kezeljük.

## 4. Az adatkezelés céljai és jogalapjai

| Cél | Adatok | Jogalap (GDPR 6. cikk (1) bek.) |
|---|---|---|
| Fiók létrehozása, bejelentkezés (e-mail, Google, Facebook), fiókkezelés | 3.1, 3.2 | b) pont – a szolgáltatás nyújtása (szerződés teljesítése) |
| Egy törölt fiók adatainak eltávolítása a többi eszközödről (eszközigazolás) | 3.1, 3.8 | b) pont |
| Az általad rögzített tartalom tárolása, szinkronizálása, megjelenítése | 3.4, 3.8 | b) pont |
| Kívánságlisták megosztása (a kívánságok fotóival együtt), csatlakozás, foglalás (kivéve az e-mail-cím alapján meghívott személy e-mail-címét) | 3.3, 3.4 (kívánságok és fotóik), 3.5 | b) pont |
| Meghívás e-mail-cím alapján: a meghívott harmadik személy e-mail-címének kezelése | 3.5 (E-mailes meghívók) | f) pont – jogos érdek: a Giftiary-felhasználó jogos érdeke, hogy egy konkrét személyt meghívhasson a megosztott kívánságlistájához |
| Megerősítő és jelszó-visszaállító e-mailek | 3.7 | b) pont |
| Meghívó e-mail a meghívott személynek | 3.7 | f) pont – jogos érdek: mint a fenti e-mailes meghívóknál; az e-mail egyben megadja a meghívottnak a 14. cikk szerinti tájékoztatást |
| Helyi emlékeztetők | 3.8 | b) pont |
| A szolgáltatás biztonsága, visszaélések megelőzése, hibakeresés, technikai naplók | 3.6 | f) pont – jogos érdek a biztonságos és megbízható működéshez |
| Mások adatainak kezelése, amelyeket te rögzítesz (lásd 5. pont) | 3.4 | f) pont – jogos érdek: annak lehetővé tétele, hogy a felhasználók a saját ajándékozási teendőiket nyilvántartsák |
| Megkeresések, adatvédelmi kérelmek kezelése, jogi kötelezettségek teljesítése | 3.9 | c) pont – jogi kötelezettség, illetve f) pont |

Az értesítések megjelenítéséhez az Android értesítési engedélyére van szükség; ezt te adod meg, és a készülék beállításaiban bármikor visszavonhatod.

## 5. Más személyekről rögzített adatok

A Giftiaryben más emberekről is rögzíthetsz adatokat (például nevüket, születésnapjukat, a nekik szánt vagy tőlük kapott ajándékokat, és ha szeretnéd, egy fotót róluk), és az ajándék-, ajándékötlet- és kívánságfotókon is láthatók lehetnek más személyek. Ezeket az adatokat kizárólag azért kezeljük, hogy a szolgáltatást neked nyújtsuk:
- csak te látod őket, kivéve az általad megosztott kívánságlisták tartalmát, a kívánságok fotóival együtt (lásd 7. pont); egy személy fotóját soha senki más nem látja;
- az érintetteket nem keressük meg és nem értesítjük (kivéve a 3.7. pont szerinti meghívó e-mailt, ha valakit az e-mail-címével meghívsz);
- az adataikat más célra nem használjuk.

Kérjük, csak olyan adatot rögzíts, amelyre a saját, személyes ajándékszervezésedhez szükséged van, és valakiről csak akkor adj hozzá fotót, ha erre a magáncélra használhatod. Ha valaki úgy véli, hogy a Giftiaryben adatot kezelünk róla, a 18. pontban leírt elérhetőségen fordulhat hozzánk.

## 6. Tárolás és szinkronizálás

A Giftiary offline is működik: a változtatások először a készülékeden mentődnek, majd internetkapcsolat esetén titkosított (HTTPS) kapcsolaton szinkronizálódnak a szerverre. A szerveren minden fiók csak a saját adataihoz fér hozzá, a vele megosztott kívánságlisták kivételével (lásd 7. pont); ezt adatbázisszintű hozzáférés-szabályozás biztosítja.

Az ajándék-, ajándékötlet-, személy- és kívánságfotók is először a készülékeden mentődnek, és internetkapcsolat esetén töltődnek fel egy privát fájltárolóba; az elem csak a fotó feltöltése után szinkronizálódik vele. A tároló nem nyilvános: a fotóknak nincs nyilvános hivatkozása, és egy fotót csak olyan bejelentkezett fióknak ad ki, amelynek a hozzáférési szabályok ezt megengedik. Minden fiók a saját mappájához fér hozzá, és másik eszközödre a fotóid szükség esetén onnan töltődnek le. Más fiók kizárólag kívánságfotót nyithat meg: az általad megosztott kívánságlista tagja a listán éppen szereplő kívánság fotóját megnézheti, de nem módosíthatja és nem törölheti (lásd 7. pont).

A profilfotód nem a készülékeden mentődik először: azonnal feltöltődik egy külön privát fájltárolóba, amelyhez csak a te fiókod fér hozzá, és csak a sikeres feltöltés után számít megváltozottnak.

## 7. Kívánságlisták megosztása, meghívók és foglalások

- Saját kívánságlistádat meghívó-hivatkozással oszthatod meg. A hivatkozást te továbbítod, az általad választott módon (például üzenetküldőben). Aki a hivatkozást megkapja, a meghívó érvényességi ideje alatt (jelenleg 7 nap) bejelentkezve csatlakozhat a listához. A meghívót bármikor visszavonhatod.
- Valakit az e-mail-címével is meghívhatsz (lásd 3.5. pont). Erre a címre egy meghívó e-mailt küldünk (lásd 3.7. pont). A meghívó 14 napig érvényes, és csak az fogadhatja el, aki megerősített e-mail-címként pontosan ezzel a címmel jelentkezik be. A függő e-mailes meghívót bármikor visszavonhatod.
- **A tagok látják:** a lista nevét, a kívánságokat (név, megjegyzés, becsült ár, termékhivatkozás és fotó) és a lista tulajdonosának megjelenített nevét.
- **A lista tulajdonosa látja:** a tagok megjelenített nevét és a csatlakozásuk idejét, a nyitott meghívó-hivatkozásokat, valamint a függő e-mailes meghívókat (a meghívott címével).
- **Foglalások:** a tagok egy kívánságnál csak azt látják, hogy szabad, általuk foglalt, vagy valaki más foglalta – azt nem, hogy ki.
- **Mit tud meg a lista tulajdonosa a foglalásokról:** **azt soha, hogy ki foglalt le egy kívánságot.** A saját magadnak vezetett listádnál a foglalásokról semmit nem látsz – azt sem, hogy egy kívánság foglalt-e –, így a meglepetés megmarad. Az egy személyed számára vezetett listánál csak azt látod, hogy egy kívánság foglalt-e („Lefoglalva”), hogy ne vedd meg ugyanazt az ajándékot; azt nem, hogy ki és mikor foglalta le, és a foglalásról mást sem.
- A tulajdonos eltávolíthat tagot, és a tag kiléphet; ilyenkor a tag foglalásai érvényüket vesztik.
- **Kívánságfotók:** a tag egy kívánság fotóját csak addig nézheti meg, amíg a lista tagja, és sem a kívánságot, sem a listát nem törölték; a fotót a tagok soha nem módosíthatják és nem törölhetik. Ha egy kívánság fotóját lecseréled vagy eltávolítod, vagy törlöd a kívánságot vagy a listát, a tagok a régi fotót attól kezdve nem nyithatják meg, hogy a változás az eszközödről szinkronizálódott; az eltávolított vagy kilépett tag pedig a lista fotóit sem nyithatja meg többé. A tag alkalmazása ezeket a fotókat csak a memóriájában tartja, amíg a listát használja, és nem menti a készülék tárhelyére. Azt a példányt, amelyet valaki már megnézett vagy más módon, például képernyőképként elmentett, nem tudjuk visszavonni.
- A megosztás csak az adott kívánságlistához ad hozzáférést. A személyeid (és fotóik), alkalmaid, ajándékötleteid (és azok fotói), ajándékaid (és azok fotói), a profilfotód és más listáid (azok kívánságaival és fotóival együtt) nem válnak láthatóvá. Ez arra a listára is igaz, amelyet egy személyed számára vezetsz: a tagok a lista címzettjeként a személy nevét látják, a fotóját soha.

## 8. Emlékeztetők és értesítések

Ha bekapcsolod az emlékeztetőket, az alkalmazás a készülékeden, helyben állítja össze a közelgő alkalmakról szóló értesítést (amely tartalmazhatja a személyek nevét és az alkalmat). Az értesítést nem a szerverünk küldi, és ehhez semmilyen adat nem kerül továbbításra. A zárolt képernyőn az értesítés tartalma – a készülék beállításaitól függően – rejtve marad. Az emlékeztetőket a Beállításokban bármikor kikapcsolhatod.

## 9. Adatfeldolgozók és más címzettek

Adataidat nem adjuk el, és nem adjuk át harmadik félnek reklám- vagy marketingcélra. A szolgáltatás működtetéséhez az alábbi szolgáltatókat vesszük igénybe:

- **Supabase** (Supabase, Inc.) – hitelesítés (felhasználói fiókok, bejelentkezés, Google- és Facebook-bejelentkezés kezelése), adatbázis és fájltárolás (ajándék-, ajándékötlet-, személy-, kívánság- és profilfotók). A projektünk az Európai Unióban, Frankfurtban (eu-central-1) található adatközpontban fut. A Supabase további alvállalkozókat (például felhőinfrastruktúra-szolgáltatót) vehet igénybe. Adatvédelmi tájékoztatója: https://supabase.com/privacy
- **Resend** – a megerősítő és jelszó-visszaállító e-mailek kézbesítése (a hitelesítési rendszer egyéni SMTP-szolgáltatójaként), valamint a meghívott e-mail-címekre küldött meghívó e-mailek kézbesítése. Adatvédelmi tájékoztatója: https://resend.com/legal/privacy-policy
- **Porkbun** – a support@giftiary.app postafiók szolgáltatója: az erre a címre küldött adatvédelmi és támogatási megkeresések fogadása és tárolása.
- **Google** – az alkalmazás terjesztése a Google Playen.

**Önálló adatkezelők:** ha Google- vagy Facebook-fiókkal jelentkezel be, a bejelentkezést a Google, illetve a Meta a saját adatkezelési szabályai szerint kezeli (https://policies.google.com/privacy, https://www.facebook.com/privacy/policy). Az Android biztonsági mentését és a Google Play-t a Google a saját feltételei szerint üzemelteti.

Ha jogszabály kötelez rá, az adatokat hatóságnak is átadhatjuk.

## 10. Adattovábbítás az EU-n kívülre

A szerverünk az EU-ban (Frankfurt) található, de szolgáltatóink és alvállalkozóik – különösen az Egyesült Államokban székhellyel rendelkező szolgáltatók, mint a Supabase, a Resend, a Porkbun és a Google – bizonyos adatokat az EU-n kívül is kezelhetnek (például üzemeltetés, támogatás vagy e-mail-kézbesítés során). Ilyen esetben az adattovábbítás az adatfeldolgozási szerződéseikben rögzített, a GDPR szerinti garanciákon alapul (például az Európai Bizottság általános szerződési feltételein vagy megfelelőségi határozatán, így az EU–USA adatvédelmi kereten, ha az adott szolgáltató csatlakozott hozzá).

## 11. Megőrzési idő

- **Fiókadatok és az általad rögzített tartalom:** a fiókod fennállásáig, illetve amíg törlöd őket.
- **Eszközigazolások:** a szerveren tárolt hash a fiók törléséig megmarad, és vele együtt törlődik. A készüléken tárolt titkos kód addig marad meg, amíg az adott fiók helyi adatai a készülékről nem törlődnek.
- **Törölt elemek:**
  - Ha az alkalmazásban törölsz egy elemet (például egy személyt vagy ajándékot), az mindenhol eltűnik a nézetekből.
  - A szerveren „törölt” jelöléssel megmarad, hogy a többi eszközöd is értesüljön a törlésről; a szinkronizálás változásnaplója szintén megmarad.
  - Mindkettő a fiók törléséig marad meg; automatikus végleges törlés jelenleg nincs.
- **Ajándék-, ajándékötlet-, személy- és kívánságfotók:**
  - Ha egy fotót lecserélsz vagy eltávolítasz, vagy törlöd az ajándékot, az ajándékötletet, a személyt vagy a kívánságot (illetve a kívánságot tartalmazó listát), azon az eszközön, ahol ezt teszed, a régi fotó helyi másolata azonnal törlődik.
  - A fájltárolóból akkor törlődik, amikor a változás erről az eszközről szinkronizálódott; amíg az eszköz offline, a fotó a fájltárolóban marad.
  - A többi eszközödön a helyi másolat a változás letöltése utáni szinkronizálások során törlődik; ha a másolatot az adott eszköz egy napon belül töltötte le, legfeljebb körülbelül egy nappal később. Kijelentkezett eszközön ez a következő bejelentkezésig nem történik meg.
  - A fotókat nem őrizzük „törölt” jelöléssel.
  - Az az ajándékötlet-fotó, amely az ötlet átadottnak jelölésekor az ajándék fotója lett, minden más ajándékfotóhoz hasonlóan marad meg és törlődik.
  - Arról, hogy egy megosztott kívánságlista tagjai mikortól nem nyithatnak meg egy kívánságfotót, a 7. pont szól.
- **Profilfotó:** ha lecseréled vagy eltávolítod, az előző fotó közvetlenül a változás után törlődik a fájltárolóból. Ha ez a törlés nem sikerül (például megszakad a kapcsolat), a maradványt egy későbbi profilfotó-cserénél, illetve a fiókkal együtt töröljük.
- **Lejárt és visszavont meghívók:** sorokként (lejárati és visszavonási időponttal) a fiók törléséig megmaradnak.
- **E-mailes meghívók:** a meghívott e-mail-címét csak addig őrizzük, amíg a meghívó függőben van, legfeljebb 14 napig. Ha a meghívót elfogadják, elutasítják vagy visszavonják, a címet azonnal töröljük; ha lejár, egy automatikus feladat a lejárat után perceken belül törli. A meghívó sora ezután a cím nélkül (a listával, az állapottal, valamint a létrehozás, a lejárat és a lezárás időpontjával) a fiókod törléséig megmarad; a meghívott fiókjának törlése nem törli. A kézbesítés technikai adatait az e-mail-szolgáltatónk a saját beállításai szerint őrzi.
- **Kijelentkezés:**
  - A kijelentkezés nem törlés. A készüléken a fiók adatai és fotói rejtve, de tárolva maradnak, hogy ugyanazzal a fiókkal visszajelentkezve újra elérd őket.
  - Ha a készülékről is el szeretnéd távolítani az adatokat, töröld a fiókot, vagy töröld az alkalmazás adatait / távolítsd el az alkalmazást.
- **Technikai naplók és e-mail-kézbesítési adatok:** a szolgáltatóink beállításai szerinti korlátozott ideig.
- **Biztonsági mentések:** a tárhelyszolgáltatónk által készített biztonsági mentések a törölt adatokat is tartalmazhatják, amíg azok a szokásos mentési ciklusban felülíródnak.
- **Megkeresések:** a kérés megválaszolásához és dokumentálásához szükséges ideig.
- **Lezárt törlési kérelmek levelezése:** a kérelem lezárásától számított 90 nap elteltével kézzel, véglegesen töröljük a teljes levelezést és mellékleteit a kezelt postafiókok valamennyi mappájából és az általunk kezelt esetleges további példányokból, beleértve az elküldött leveleket is. Ez a megőrzési szabály a levelezésre vonatkozik, nem a kérelem teljesítéséről vezetett külön nyilvántartásra.
- **Törlési kérelmek teljesítési nyilvántartása:** a kérelem lezárásától számított egy évig őrizzük a teljesítés igazolásához szükséges minimális nyilvántartást, majd kézzel töröljük. A nyilvántartás az ügyazonosítót, az érintett fiók UUID-jét, a beérkezés, megerősítés, teljesítés, lezárás és értesítés dátumát, a záró ellenőrzés eredményét, valamint a levelezés és a nyilvántartási sor törlési határidejét és a levelezés tényleges törlésének dátumát tartalmazza. Nem másolunk bele e-mail-címet, levéltartalmat, fotót vagy más felhasználói tartalmat. Korlátozott hozzáféréssel kezeljük; az UUID-t tartalmazó nyilvántartást személyes adatnak tekintjük.

## 12. Fiók törlése

**Az alkalmazásban:** Beállítások → Fiók törlése, megerősítés után. A törlés azonnali és nem vonható vissza.

**Mi történik:**

- **A szerveren** először a fiók összes ajándék-, ajándékötlet-, személy- és kívánságfotója, majd a profilfotói törlődnek a fájltárolóból, ezután egyetlen lépésben, véglegesen törlődik:
  - a fiókod összes személye, alkalma, ajándékötlete, ajándéka, kívánságlistája és kívánsága;
  - a meghívóid (az e-mailes meghívóid is) és a szinkronizálás változásnaplója;
  - a profilod (megjelenített név és a profilfotód hivatkozása);
  - a te tagságaid és foglalásaid mások listáin, valamint a saját listáidhoz tartozó összes tagság és foglalás;
  - végül maga a felhasználói fiók, a bejelentkezési munkamenetekkel, a kapcsolt Google-/Facebook-identitásokkal és az eszközigazolásokkal együtt.

  Ezek az adatok nem „törölt” jelöléssel maradnak meg, hanem ténylegesen törlődnek. Ettől kezdve a megosztott listáid tagjai sem a listákat, sem a kívánságok fotóit nem nyithatják meg. Ha a fotók törlése után a fiók törlése nem sikerül, néhány fotó már törlődhetett; a fióktörlés ilyenkor újra megpróbálható.
- **Azon a készüléken, amelyen a törlést elvégzed:** az alkalmazás törli a fiók helyi adatait és fotóit, emlékeztető-beállításait és függő emlékeztetőit, majd kijelentkeztet. Ha az alkalmazás a folyamat közben leáll, a következő indításkor befejezi.
- **A fiók többi eszközén** az a telepítés, amely eszközigazolást regisztrált (lásd 3.1. pont), magától törli a fiók helyi adatait és fotóit, de nem azonnal: csak akkor, amikor az eszköz online van, és a bejelentkezését elutasítják (amikor az alkalmazás legközelebb elindul, előtérbe kerül vagy megújítja a bejelentkezést; nyitott alkalmazásnál ez általában körülbelül egy órán belül megtörténik), és csak azután, hogy a szerverünk megerősítette, hogy az igazolás már nem tartozik létező fiókhoz. Ha a szerver nem érhető el, vagy a válasza nem egyértelmű, semmi nem törlődik, és az eszköz később újra rákérdez. Az eszközön lévő más fiókok adatait ez nem érinti. Az alkalmazás régebbi változata, illetve az a telepítés, amely soha nem regisztrált igazolást (például mert bejelentkezve soha nem volt online), megtarthatja az adatokat a helyi adatbázisában; ott az alkalmazás adatainak törlésével vagy az alkalmazás eltávolításával távolíthatod el őket.
- Azok az e-mailes meghívók, amelyeket más felhasználók a te e-mail-címedre küldtek, az ő fiókjukhoz tartoznak, ezért a fiókod törlése nem törli őket (lásd 11. pont).
- Ha egy másik eszközöd a törlés idején még érvényes bejelentkezéssel fotót tölt fel, az a fájltárolóban maradhat. Az ilyen maradványfotót más felhasználó nem érheti el, és kézi karbantartással töröljük.
- A biztonsági mentésekre a 11. pont vonatkozik.

**E-mailben, az alkalmazás nélkül:** ha nem férsz hozzá az alkalmazáshoz, vagy nem szeretnéd újratelepíteni, a törlést e-mailben is kérheted:

- Írj a support@giftiary.app címre, lehetőleg arról az e-mail-címről, amellyel a Giftiaryt használod, és írd meg, hogy a fiókod törlését kéred.
- Ezután a fiókhoz tartozó e-mail-címre visszaigazoló levelet küldünk. A törlést akkor végezzük el, ha erre a levélre arról a címről válaszolsz és megerősíted a kérést. Így ellenőrizzük, hogy a fiók valóban a tiéd.
- Ha nem tudod, melyik e-mail-cím tartozik a fiókodhoz (Google- vagy Facebook-bejelentkezésnél ez a szolgáltatótól kapott cím), írd meg, melyik bejelentkezési módot használtad. A visszaigazolást mindig a fiókhoz tartozó e-mail-címre küldjük; e-mailben csak így tudjuk ellenőrizni a kérést.
- Alapesetben nem kérünk jelszót, ellenőrző kódot vagy más bejelentkezési adatot, sem személyazonosító okmányt. Ilyet soha ne küldj nekünk.
- A határidőt a kérésed beérkezésétől számítjuk. A visszaigazoló levelet a beérkezés után haladéktalanul elküldjük, a megerősítésed után indokolatlan késedelem nélkül töröljük a fiókot, és a kérésed beérkezésétől számított egy hónapon belül tájékoztatunk a kérés teljesítéséről. A megerősítés nem indít új határidőt. Ha a kérést nem tudjuk teljesíteni (például mert a fiókhoz tartozó címről nem érkezik megerősítés), erről és az okáról is ezen a határidőn belül tájékoztatunk. A szerveren ugyanaz törlődik, mint az alkalmazásbeli törléskor (a fotókkal együtt); a készülékeiden lévő helyi adatokat távolról nem tudjuk törölni; az alkalmazás aktuális változatai a fent leírt módon maguktól eltávolítják őket, egyébként az alkalmazás adatainak törlésével vagy az alkalmazás eltávolításával távolíthatod el őket. Az elvégzett törlésről e-mailben értesítünk.

A fióktörlés lépései és részletei: https://giftiary.app/account-deletion/

## 13. Jogaid

A GDPR alapján jogosult vagy:

- tájékoztatást kérni és **hozzáférni** a rólad kezelt adatokhoz;
- a pontatlan adatok **helyesbítését** kérni (a legtöbb adatot az alkalmazásban magad is módosíthatod);
- az adataid **törlését** kérni (lásd 12. pont);
- az adatkezelés **korlátozását** kérni;
- **adathordozhatóságot** kérni (az adataidat géppel olvasható formában megkapni);
- **tiltakozni** a jogos érdeken alapuló adatkezelés ellen.

Kérésedet a support@giftiary.app címre küldheted. Indokolatlan késedelem nélkül, legkésőbb a kérés beérkezésétől számított egy hónapon belül válaszolunk. A kérés teljesítése előtt ellenőrizhetjük, hogy a fiók valóban hozzád tartozik.

Panaszt tehetsz a Nemzeti Adatvédelmi és Információszabadság Hatóságnál (NAIH, 1055 Budapest, Falk Miksa utca 9–11., https://naih.hu), vagy a lakóhelyed szerinti uniós felügyeleti hatóságnál, illetve bírósághoz fordulhatsz.

## 14. Adatbiztonság

- Az alkalmazás és a szerver közötti kapcsolat titkosított (HTTPS).
- Minden fiók csak a saját adataihoz fér hozzá, a vele megosztott kívánságlisták kivételével (7. pont); ezt adatbázisszintű hozzáférés-szabályozás biztosítja.
- A jelszavakat, a meghívókódokat és az eszközigazolásokat a szerveren csak visszafejthetetlen (hash-elt) formában tároljuk.
- A fotók nyilvános hivatkozás nélküli, privát fájltárolóban vannak, a profilfotókat pedig minden más fotótól elkülönítve tároljuk. Minden fiók a saját mappájához fér hozzá; más fiók csak egy olyan megosztott listán éppen szereplő kívánság fotóját nézheti meg, amelynek tagja, és azt sem módosíthatja, sem nem törölheti.
- A személy-, profil-, ajándék- és ajándékötlet-fotók a kívánságlista-megosztáson és a meghívókon keresztül sem érhetők el; a szerver a tulajdonosukon kívül senkinek nem adja ki őket.
- A szerver a lista tulajdonosának soha nem adja ki, hogy ki foglalt le egy kívánságot. A tulajdonos saját magának vezetett listájánál a foglalásokról semmit nem ad ki neki; az egy személye számára vezetett listánál csak azt, hogy egy kívánság foglalt-e.
- A bejelentkezési munkamenetet, a helyi adatbázist, a fotókat és az eszközigazolások titkos kódjait az Android biztonsági mentése nem menti.

Teljes biztonságot egyetlen rendszer sem tud garantálni, de észszerű intézkedésekkel védjük az adataidat.

## 15. Gyermekek

A Giftiary jelenleg felnőtteknek készült, és nem gyermekeknek szól. Ha úgy gondolod, hogy egy gyermek adatai kerültek a szolgáltatásba, kérjük, jelezd nekünk.

## 16. Amit nem csinálunk

- Nem használunk analitikai, reklám- vagy hibajelentő eszközt, és nem követjük a tevékenységedet más alkalmazásokban vagy weboldalakon.
- Nem kérünk hozzáférést helyadathoz, névjegyekhez, kamerához, fényképekhez vagy fájlokhoz; fotót csak az általad kiválasztott vagy a kameraalkalmazással készített képből készítünk.
- Nem kezelünk fizetési adatot.
- Nem adjuk el az adataidat.

## 17. A tájékoztató módosítása

Ha a Giftiary működése vagy az adatkezelés változik, frissítjük ezt a tájékoztatót, és a fenti hatálybalépési dátumot módosítjuk. A mindenkor hatályos változatot a https://giftiary.app/privacy/ oldalon tesszük közzé.

## 18. Kapcsolat

Antal Tímea – support@giftiary.app – https://giftiary.app
