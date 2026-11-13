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

- **Opslag is lokaal.** De Android-app en de website hebben elk hun eigen gegevens (SharedPreferences en localStorage van de browser). Om ze te synchroniseren is een backend nodig, bijvoorbeeld Supabase of Firebase. De `KeyValueStore`/`Repository`-laag is zo opgezet dat die later te vervangen is.
- **Herinneringen op web** werken alleen zolang de pagina open staat, omdat een gewone website zonder server geen meldingen kan sturen als hij dicht is. Op Android werkt de herinnering altijd, ook na een herstart.
- **Android 13+** vraagt bij de eerste start om toestemming voor meldingen. Weiger je die, dan opent de knop *Meldingen toestaan* de systeeminstellingen.
- De herinnering gebruikt `setAndAllowWhileIdle`. In de spaarstand kan Android hem een paar minuten later afleveren. Daarvoor is geen extra toestemming voor exacte alarmen nodig.

## Versies

Kotlin 2.1.21 · Compose Multiplatform 1.8.2 · AGP 8.7.3 · Gradle 8.14.3 · minSdk 26 / targetSdk 35.
Pas `gradle/libs.versions.toml` aan als je nieuwere versies wilt gebruiken.
