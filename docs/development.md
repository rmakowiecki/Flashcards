# Development

How to build, run and check the project locally. The app and its backend run against a private Firebase project,
so a fresh clone does not build until the private configuration below is in place.

## Prerequisites

- Android Studio (its bundled JDK is enough; the build needs JDK 17 or newer)
- Node 22, for Cloud Functions and the Firestore rules tests
- The Firebase CLI (`firebase-tools`), for deploys and the emulators

## Checkout

The animated background comes from [Morpheus](https://github.com/rmakowiecki/Morpheus), a git submodule built as a
composite build ([ADR-0069](adr/0069-morpheus-is-an-external-library-built-from-a-submodule.md)). After cloning:

```shell
git submodule update --init
```

## Private configuration

These files are gitignored and never committed:

- `app/google-services.json`: the Firebase config, with entries for the release and debug application ids.
- `local.properties`: `GOOGLE_WEB_CLIENT_ID`, used for Google sign-in.
- The release keystore and its passwords, needed only to sign a release build ([ci-cd.md](ci-cd.md)).

## App

Open the project in Android Studio and run the `app` configuration, or from the command line:

```shell
./gradlew :app:installDebug
```

## Website

The public site (`:web`) is served by Firebase Hosting and deployed separately from app releases:

```shell
./gradlew :web:wasmJsBrowserDevelopmentRun   # local preview with live reload
./gradlew :web:wasmJsBrowserDistribution     # the deployable site, in web/build/dist/wasmJs/productionExecutable
firebase deploy --only hosting               # builds the site first (predeploy), then uploads it
```

## Cloud Functions

Setup, local tests against the emulators and deploys are in [functions/README.md](../functions/README.md).

## Firestore rules

The security rules tests run against the Firestore emulator; see
[firestore-tests/README.md](../firestore-tests/README.md).

## Checks

```shell
./gradlew staticAnalysis   # Spotless, detekt, Konsist and Android Lint: the gate every pull request must pass
./gradlew formatCode       # fix formatting
./gradlew test             # unit tests
```

The same checks run in CI on every pull request ([ci-cd.md](ci-cd.md)). Testing conventions are in
[TESTING.md](../TESTING.md).

## Releases

Branches, versioning and how a release reaches Google Play: [release-flow.md](release-flow.md).
