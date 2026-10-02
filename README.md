# Twyn Android SDK — integration sample

> Everything you need to integrate the **Twyn Android SDK** (T4FastID / Twyn PAAS):
> a working sample app, a step-by-step guide, and the SDK pulled as a signed binary
> from a private registry.
>
> This repo contains **no proprietary logic** — only the integration surface.

## Start here

| I want to… | Go to |
|---|---|
| **integrate the SDK in my app** | [`docs/integration.md`](docs/integration.md) (10-minute guide) |
| **see it working first** | `sample/` — build & run it (below) |
| **understand the options** | [`docs/integration.md`](docs/integration.md) → *Options reference* |

## Run the sample app (3 steps)

**1. Get a registry token** — ask Twyn for a GitHub token with `read:packages`.

**2. Configure credentials** — create `gradle.properties` (do **not** commit):
```properties
twynUser=your-github-user
twynToken=ghp_xxxxxxxxxxxxxxxxxxxx
```

**3. Build & install:**
```bash
./gradlew :sample:assembleDebug
adb install -r sample/build/outputs/apk/debug/sample-debug.apk
```

## What the sample shows

A full demo you can use as a template:

- **Endpoints** — *Gateway base URL* and *Sentinel base URL* (placeholders; set your own).
- **Parameters form** — `personId`, `canal` (default `TWYN`), `env`, language,
  `requestSteps`, `sdkKey`, `tcn`/`tot`.
- **Launch** — `T4FastID.createIntent(...)` + the three listeners.
- **Result dialog** — **APROVADO / REPROVADO / EM ANÁLISE**, with liveness, risk,
  Sentinel verdict and reason codes.
- **Callback log** + captured face preview.

## SDK at a glance

| | |
|---|---|
| Coordinates | `com.t4isb:t4fastid:1.0.7` |
| Format | Android Archive (`.aar`) + stripped native libs |
| Registry | `https://maven.pkg.github.com/twyn-internal/twyn-android-sdk` (private) |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| minSdk / targetSdk | 24 / 36 |
| Theme | must be a **Material Components** theme |

> The registry is **private** — a GitHub token with `read:packages` is required.
> Credentials come from `gradle.properties` / CI secrets; never commit them.

## Minimal code

```kotlin
// 1) register listeners once
T4FastID.setEnrListener(this)
T4FastID.setSDKStatusListener(this)

// 2) launch
val intent = T4FastID.createIntent(this, sdkKey, personId, "TWYN", "dev").apply {
    putExtra("language", "en")
    putExtra("requestSteps", arrayOf("T4_FACE"))
}
startActivity(intent)

// 3) receive the result
override fun onEnrollFaceCompleted(r: EnrollFaceResult?) { /* r?.livenessStatus, r?.tcn */ }
override fun onEnrollFaceError(e: EnrollFaceError?) { /* e?.error_code, e?.error_message */ }
```

> ⚠️ The **final decision is server-side**. `livenessStatus` is the capture result;
> the authoritative verdict comes from your backend / the audit API.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Could not resolve com.t4isb:t4fastid` | token missing/invalid → check `twynUser`/`twynToken` |
| `InflateException: MaterialButton` | use a Material Components theme |
| `NoClassDefFoundError: com.sentinel.sdk.Sentinel` | AAR older than 1.0.7 → bump the version |
| "Error While Getting Required Data" | app package not allowed on the gateway → ask Twyn to register it |

## License

The **sample code** is released under the MIT License (see `LICENSE`). The **SDK
binary** is proprietary and governed by a separate commercial agreement.
