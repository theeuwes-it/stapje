# Stapje

Stap voor stap bijhouden hoe het gaat tijdens de opbouw na ziekte.

Compose Multiplatform-app (Android + web) om per werkdag bij te houden:

- **Gewerkt**: hele dag / ochtend / middag / niet gewerkt, plus het aantal uren (stappen van een half uur)
- **Wat gedaan**: activiteiten om aan te tikken (zelf aan te passen), plus een schakelaar **Labwerk** met uren en omschrijving
- **Hoe gaat het**: pijn algemeen, rugpijn, energie en gevoel, elk met één tik op een schaal van 0 tot 10
- **Herinnering** op een zelf gekozen tijdstip, alleen op werkdagen die nog niet zijn ingevuld

## Zo snel mogelijk invullen

- Het startscherm toont meteen een grote knop **Vandaag invullen**.
- Dagdeel en uren worden voorgevuld met wat je de vorige keer koos. Een dagdeel kiezen vult automatisch de bijbehorende uren in.
- Alles staat op één scherm: geen extra stappen of menu's.
- De opslaan-knop laat zien hoeveel vragen er nog openstaan.
- Vergeten werkdagen van de afgelopen twee weken staan bovenaan, met een knop **Invullen** erbij.
- Tik op de melding en het formulier van vandaag gaat direct open. Met **Over 1 uur** stel je de herinnering een uur uit.

## Projectstructuur

```
composeApp/src/
  commonMain/   gedeelde code: model, opslag (JSON), herinneringslogica, alle schermen
  androidMain/  MainActivity, SharedPreferences, AlarmManager, meldingen, herstart-receiver
  wasmJsMain/   web-ingang, localStorage, browsermeldingen, index.html
```

## Bouwen en draaien

Je hebt JDK 17 of nieuwer en de Android SDK nodig. Android Studio (met de Kotlin Multiplatform-plugin) of IntelliJ IDEA werkt het makkelijkst.

```bash
# Android: debug-APK installeren op een aangesloten telefoon/emulator
./gradlew :composeApp:installDebug

# Web: ontwikkelserver met live reload (opent http://localhost:8080)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web: productieversie (statische bestanden)
./gradlew :composeApp:wasmJsBrowserDistribution
# -> composeApp/build/dist/wasmJs/productionExecutable/  (upload naar elke statische host)
```

### GitHub
`.github/workflows/build.yml` bouwt bij elke push naar `main` de APK (als downloadbaar artifact) en zet de website op GitHub Pages. Zet daarvoor in de repository bij *Settings → Pages* de bron op **GitHub Actions**.

## Goed om te weten

- **Opslag is lokaal, met synchronisatie als optie.** Zonder Supabase-gegevens werkt de app volledig offline. Met Supabase en Google-login blijven Android en web gelijk (zie hieronder). Ook ingelogd blijft alles offline werken: wijzigingen gaan mee bij de volgende synchronisatie.
- **Herinneringen op web** werken alleen zolang de pagina open staat, omdat een gewone website zonder server geen meldingen kan sturen als hij dicht is. Op Android werkt de herinnering altijd, ook na een herstart.
- **Android 13+** vraagt bij de eerste start om toestemming voor meldingen. Weiger je die, dan opent de knop *Meldingen toestaan* de systeeminstellingen.
- De herinnering gebruikt `setAndAllowWhileIdle`. In de spaarstand kan Android hem een paar minuten later afleveren. Daarvoor is geen extra toestemming voor exacte alarmen nodig.

## Synchronisatie (Supabase + Google-login)

Hoe het werkt: elke dag en de instellingen krijgen een tijdstempel. Bij het synchroniseren wint per dag de nieuwste wijziging, en verwijderde dagen worden op alle apparaten verwijderd. Er wordt gesynchroniseerd:
- na inloggen,
- na elke wijziging,
- als de app weer op de voorgrond komt,
- elke 5 minuten,
- op Android ook vlak voor de herinnering, zodat je geen melding krijgt als je de dag al op de website hebt ingevuld.

Eenmalig instellen:

1. **Database.** Voer `supabase/migrations/20261113000000_stapje_init.sql` uit in de SQL-editor van je Supabase-project, of met `supabase db push`. Dit maakt `day_entries` en `user_settings` aan, met Row Level Security: iedereen ziet alleen zijn eigen gegevens.
2. **Google Cloud.** Ga naar *APIs & Services → Credentials* en maak een *OAuth client ID* van het type **Web application** aan.
   - Bij *Authorized redirect URIs* zet je `https://<jouw-project>.supabase.co/auth/v1/callback`.
3. **Supabase → Authentication → Sign In / Providers → Google.** Zet de provider aan en vul het client ID en secret uit stap 2 in.
4. **Supabase → Authentication → URL Configuration.**
   - *Site URL*: het adres van de website, bijvoorbeeld `https://theeuwes-it.github.io/stapje/`.
   - *Redirect URLs*: voeg zowel dat adres toe als `stapje://login` (voor de Android-app).
5. **App.** Vul in `composeApp/src/commonMain/kotlin/nl/theeuwesit/stapje/sync/SupabaseConfig.kt` de project-URL en de *publishable key* (of *anon key*) in. Die sleutel is bedoeld om in apps te staan. De gegevens worden beschermd door Row Level Security.

## Versies

Kotlin 2.1.21 · Compose Multiplatform 1.8.2 · supabase-kt 3.1.4 · Ktor 3.1.3 · AGP 8.7.3 · Gradle 8.14.3 · minSdk 26 / targetSdk 35.
Pas `gradle/libs.versions.toml` aan als je nieuwere versies wilt gebruiken.
