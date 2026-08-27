# PokéTrivia for Android

A native, touch-first Pokémon trivia game built with Kotlin and Jetpack Compose.

## Current feature set

- Generations 1–9 individually or in any combination
- Easy mode with four official-artwork choices
- Pokémon game-cry audio when a picture choice is tapped
- Hard mode with progressive description, height, shiny-color, and generation clues
- 10, 25, 50, 100, All, and Endless runs with tiered Poké Ball lives
- Millisecond-precision timed runs and an on-device leaderboard with score, time, format, and remaining lives
- GitHub Releases update check
- Live Pokémon data and artwork from [PokéAPI](https://pokeapi.co/)

## Open in Android Studio

Local Android Studio is optional. GitHub Actions compiles the app using JDK 17, Android tooling, and Gradle 9.5.

Before publishing, change `applicationId`, replace `OWNER/PUBLIC_REPOSITORY` in `app/build.gradle.kts`, add launcher artwork, and configure release signing.

## Repository and release workflow

Keep the development repository private. Every push to `main` creates an installable debug-signed APK under the workflow's **Artifacts** section. When a version is approved, mirror the source to a separate public repository and push a version tag such as `v0.1`. GitHub Actions creates a GitHub Release and attaches the APK. The app checks that public repository's latest release without storing a GitHub token.

Every pushed product update must increment both the GitHub version tag and Android `versionCode`/`versionName`.

Do not put a private-repository token in the app: secrets embedded in an APK can be recovered. Private testers can download workflow artifacts from the private repository. Public users can download release APKs from the public repository.

## Leaderboard scope

Scores currently persist on the device with Room. A global leaderboard needs a small authenticated backend with abuse controls; it should not accept anonymous client-side writes directly to a database.

## Data notice

PokéAPI is free and open to use and exposes game data plus sprite links. Pokémon names, character imagery, and related marks belong to Nintendo, Game Freak, and Creatures. Review branding and distribution rights before public release. This project is not affiliated with or endorsed by those companies.
