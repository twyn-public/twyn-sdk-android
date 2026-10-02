# Android integration guide — Twyn SDK

> Goal: get the SDK running in **your** app in ~10 minutes.
> Copy the snippets, replace the placeholders, done.

---

## 1. Get access to the SDK

The SDK binary is hosted in a **private** Maven registry.

1. Ask Twyn for a **GitHub token** with the `read:packages` scope (or use your own
   account if it has access to the `twyn-internal` org).
2. Add the registry + credentials:

**`settings.gradle.kts`**
```kotlin
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/twyn-internal/twyn-android-sdk")
            credentials {
                username = providers.gradleProperty("twynUser").orNull
                password = providers.gradleProperty("twynToken").orNull
            }
        }
    }
}
```

**`gradle.properties`** (do **not** commit this file)
```properties
twynUser=your-github-user
twynToken=ghp_xxxxxxxxxxxxxxxxxxxx
```

> The registry is **private** → a token is required to download the SDK.

## 2. Add the dependency

**`app/build.gradle.kts`**
```kotlin
dependencies {
    implementation("com.t4isb:t4fastid:1.0.7")
}
```

## 3. Theme (one line, required)

The SDK UI uses Material components. Set a **Material Components** theme on your
`application`:

```xml
<application android:theme="@style/Theme.MaterialComponents.DayNight.NoActionBar" ...>
```

## 4. Permissions

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-feature android:name="android.hardware.camera.front" android:required="true" />
```

## 5. Launch the SDK

```kotlin
// 1) Register listeners once (they live on the SDK's companion object).
T4FastID.setEnrListener(this)          // EnrollListener
T4FastID.setSDKStatusListener(this)    // SDKStatusListener

// 2) Build the launch Intent + optional extras.
val intent = T4FastID.createIntent(
    this,
    /* sdkKey   */ "YOUR-SDK-KEY",     // provided by Twyn (optional for dev)
    /* personId */ "12345678900",      // the identity being enrolled
    /* canal    */ "TWYN",             // channel
    /* env      */ "dev",              // "dev" or "prod"
).apply {
    putExtra("language", "en")                    // "pt" | "en"
    putExtra("requestSteps", arrayOf("T4_FACE"))  // T4_FACE, T4_FINGER, T4_DOCUMENT
    putExtra("iBeta", true)                        // return the raw result, no retry dialog
}

// 3) Start. Results arrive through the listeners.
startActivity(intent)
```

## 6. Handle the result

```kotlin
override fun onEnrollFaceCompleted(result: EnrollFaceResult?) {
    // result?.livenessStatus : Boolean?
    // result?.tcn            : String?
    // result?.croppedFace    : String? (base64)
}

override fun onEnrollFaceError(error: EnrollFaceError?) {
    // error?.error_code, error?.error_message
}
```

> ⚠️ **The final decision is server-side.** `livenessStatus` is the *capture* result.
> The authoritative **APPROVED / CHALLENGE / REJECTED** comes from your backend
> (the audit API). See `README.md` → *Result dialog*.

## 7. Device integrity (Sentinel) — optional but recommended

```kotlin
SentinelIntegrator.initAsync(
    applicationContext,
    "https://your-sentinel.example.com",   // ← your Sentinel host
    allowCleartext = false,
)
SentinelIntegrator.scanAndSubmit(this, deepScan = false)
```

The verdict flows into the transaction and the gateway applies `SENTINEL_BLOCK` /
`SENTINEL_REVIEW`.

## 8. R8 / ProGuard

The SDK ships consumer rules. If you build the AAR yourself, add:

```proguard
-keep class com.t4isb.t4fastid.** { *; }
-keepclassmembers class com.t4isb.t4fastid.** { *; }
```

---

## Options reference

| Extra / parameter | Values | Meaning |
|---|---|---|
| `sdkKey` | string | App key provided by Twyn (optional in dev) |
| `personId` | string | The identity being enrolled |
| `canal` | string | Channel (default `TWYN`) |
| `env` | `dev` / `prod` | Environment |
| `language` | `pt` / `en` | SDK UI language |
| `requestSteps` | `T4_FACE`, `T4_FINGER`, `T4_DOCUMENT` | Which captures to run |
| `iBeta` | `true` / `false` | Return the raw liveness result without a retry dialog |
| `tcn` | string | Pre-set transaction token (optional) |
| `tot` | `ENR` / `VER` | Transaction type (optional) |

## Result dialog (what the user sees)

The sample reads the decision from the **audit API** and shows a dialog:

| Decision | Dialog | Meaning |
|---|---|---|
| `APPROVED` | green ✓ APROVADO | transaction authorized |
| `REJECTED` | red ✕ REPROVADO | blocked (spoof, root, etc.) |
| `CHALLENGE` | amber ! EM ANÁLISE | needs review |

with **liveness**, **risk**, **Sentinel** verdict and the **reason codes**.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Could not resolve com.t4isb:t4fastid` | token missing/invalid → check `twynUser`/`twynToken` |
| `InflateException: MaterialButton` | set a Material Components theme (§3) |
| `NoClassDefFoundError: com.sentinel.sdk.Sentinel` | the AAR is older than 1.0.7 → bump the version |
| SDK opens but "Error While Getting Required Data" | your app package isn't allowed on the gateway → ask Twyn to register it |
| Dialog shows "INDEFINIDA" | the *Gateway base URL* field is empty or wrong |
