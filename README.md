# Stapje

Stap voor stap bijhouden hoe het gaat tijdens de opbouw na ziekte.

Compose Multiplatform-app (Android + web) om per werkdag bij te houden:

- **Gewerkt**: hele dag / ochtend / middag / niet gewerkt, plus het aantal uren (stappen van een half uur)
- **Wat gedaan**: activiteiten om aan te tikken (zelf aan te passen), plus een schakelaar **Labwerk** met uren en omschrijving
- **Hoe gaat het**: per dagdeel (ochtend, middag, avond) pijn algemeen, rugpijn, energie en gevoel, elk met één tik op een schaal van 0 tot 10. Een dagdeel mag leeg blijven en later worden aangevuld.
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
composeApp/src/          gedeelde Kotlin Multiplatform-module
  commonMain/   model, opslag (JSON), herinneringslogica, alle schermen
  androidMain/  Android-specifieke stukjes van de gedeelde code (terugknop)
  wasmJsMain/   web-ingang, localStorage, browsermeldingen, index.html
androidApp/src/main/     Android-app
                MainActivity, SharedPreferences, AlarmManager, meldingen, herstart-receiver
```

## Bouwen en draaien

Je hebt JDK 21 of nieuwer en de Android SDK nodig. Android Studio (met de Kotlin Multiplatform-plugin) of IntelliJ IDEA werkt het makkelijkst.

```bash
# Android: debug-APK installeren op een aangesloten telefoon/emulator
./gradlew :androidApp:installDebug

# Web: ontwikkelserver met live reload (opent http://localhost:8080)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web: productieversie (statische bestanden)
./gradlew :composeApp:wasmJsBrowserDistribution
# -> composeApp/build/dist/wasmJs/productionExecutable/  (upload naar elke statische host)
```

### GitHub
`.github/workflows/build.yml` bouwt bij elke push naar `main` de APK (als downloadbaar artifact) en zet de website op GitHub Pages. Zet daarvoor in de repository bij *Settings → Pages* de bron op **GitHub Actions**.

### Google Play

De job `play-store` in dezelfde workflow bouwt een ondertekende release-bundle (`.aab`) en uploadt die naar Google Play:

- bij een push naar `main` naar de track **internal** (interne test);
- via *Actions → Build → Run workflow* naar een track naar keuze (internal, alpha, beta of production).

Het versienummer gaat vanzelf: `versionCode` is het runnummer van de workflow, `versionName` wordt `1.0.<runnummer>`.

**Eenmalig instellen**

1. **Uploadsleutel maken** (bewaar het bestand en de wachtwoorden goed, bijvoorbeeld in een wachtwoordmanager):
   ```bash
   keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **App aanmaken in de [Play Console](https://play.google.com/console)** met pakketnaam `nl.theeuwesit.stapje` en *Play App Signing* aanzetten. Google beheert dan de echte app-sleutel; `upload.jks` is alleen je uploadsleutel.
3. **Eerste versie met de hand uploaden.** De Play API kan pas uploaden als er al één bundle in de Console staat. Bouw die lokaal met dezelfde variabelen als in CI:
   ```bash
   STAPJE_KEYSTORE_FILE=$PWD/upload.jks STAPJE_KEYSTORE_PASSWORD=… STAPJE_KEY_ALIAS=upload STAPJE_KEY_PASSWORD=… \
     ./gradlew :androidApp:bundleRelease
   # -> androidApp/build/outputs/bundle/release/androidApp-release.aab
   ```
   en upload die bij *Testen → Interne test*.
4. **Serviceaccount voor de upload:** maak in Google Cloud een serviceaccount met een JSON-sleutel en nodig het in de Play Console uit (*Gebruikers en rechten*) met rechten om releases te beheren voor deze app.
5. **Secrets in GitHub** (*Settings → Secrets and variables → Actions*):

   | Secret | Inhoud |
   |---|---|
   | `ANDROID_KEYSTORE_BASE64` | uitvoer van `base64 -i upload.jks` |
   | `ANDROID_KEYSTORE_PASSWORD` | wachtwoord van de keystore |
   | `ANDROID_KEY_ALIAS` | `upload` (of de alias die je koos) |
   | `ANDROID_KEY_PASSWORD` | wachtwoord van de sleutel |
   | `PLAY_SERVICE_ACCOUNT_JSON` | de volledige inhoud van het JSON-sleutelbestand |

6. **Aanzetten:** maak onder *Variables* de variabele `PLAY_UPLOAD_ENABLED` met waarde `true`. Zolang die er niet is, slaat de workflow de Play-job over.

Zolang de app in de Play Console nog nooit gepubliceerd is, accepteert Google alleen conceptreleases. Zet dan ook de variabele `PLAY_RELEASE_STATUS` op `draft` en haal die weg na de eerste goedkeuring.

De job draait in de GitHub-omgeving `play-store`. Wil je elke upload eerst goedkeuren, zet dan bij *Settings → Environments → play-store* een *Required reviewer* aan.

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
5. **App.** De project-URL en de *publishable key* (of *anon key*) komen uit *Project Settings → API*. Die sleutel is bedoeld om in apps te staan. De gegevens worden beschermd door Row Level Security. De build zet ze in een gegenereerd `SupabaseConfig.kt`:
   - **GitHub Actions:** maak bij *Settings → Secrets and variables → Actions → Variables* de variabelen `SUPABASE_URL` en `SUPABASE_PUBLISHABLE_KEY` aan. De workflow gebruikt ze voor de website, de APK en de Play-bundle.
   - **Lokaal:** zet ze in `local.properties` (staat niet in git):
     ```properties
     supabase.url=https://<jouw-project>.supabase.co
     supabase.publishableKey=sb_publishable_…
     ```
     Omgevingsvariabelen `SUPABASE_URL` en `SUPABASE_PUBLISHABLE_KEY` gaan voor.

   Zonder deze gegevens werkt de app alleen lokaal, zonder inloggen.

## Versies

Kotlin 2.4.21 · Compose Multiplatform 1.12.2 · supabase-kt 3.1.4 · Ktor 3.1.3 · AGP 9.5.1 · Gradle 9.9.1 · minSdk 26 / targetSdk 37.
Pas `gradle/libs.versions.toml` aan als je nieuwere versies wilt gebruiken.
