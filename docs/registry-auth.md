# Registry authentication

The SDK binaries live in the **private** org `twyn-internal` (GitHub Packages).
Consumers (the public sample, the demo, CI) need a token with `read:packages`
that can reach `twyn-internal`.

Two options. **GitHub App is recommended** (ephemeral tokens, not tied to a person,
revocable, least privilege). A fine-grained PAT is the quick alternative.

---

## Option A — GitHub App (recommended)

### 1. Create the App
`twyn-internal` → **Settings → Developer settings → GitHub Apps → New GitHub App`

| Field | Value |
|---|---|
| Name | `twyn-registry` |
| Homepage URL | `https://twyn.me` |
| Webhook | uncheck "Active" |
| Repository permissions → **Packages** | **Read-only** |
| (Metadata) | Read-only (mandatory) |
| Where can this App be installed | **Any account** |

Create it, then note the **App ID**.

### 2. Generate a private key
On the App page → **Generate a private key** → download the `.pem`.

### 3. Install the App
App page → **Install App** → install on **`twyn-internal`** (where the packages
are). Installing on `twyn-public` is optional.

### 4. Set the credentials on the consumer repos
For each consumer repo (`twyn-public/twyn-sdk-android`, `twyn-internal/twyn-demo-android`):

```bash
gh variable set TWYN_APP_ID --repo <owner>/<repo> --body "<APP_ID>"
gh secret  set TWYN_APP_PRIVATE_KEY --repo <owner>/<repo> < path/to/app.pem
```

The workflow mints a short-lived installation token via
`actions/create-github-app-token` and uses it as the Maven password. Nothing
long-lived is stored.

---

## Option B — fine-grained PAT

1. Create a **fine-grained token** (Settings → Developer settings → Personal access
   tokens → Fine-grained).
2. Resource owner: **`twyn-internal`**.
3. Repository access: **Only select repositories → `twyn-android-sdk`**.
4. Permissions: **Packages → Read**.
5. Set it as the secret (replaces the App):

```bash
gh secret set TWYN_MAVEN_TOKEN --repo <owner>/<repo> < token.txt
```

Prefer a **machine account** (e.g. `twyn-ci`) over a personal account so the
credential survives staff changes.

---

## How the workflow uses it

`twyn-sdk-android/.github/workflows/ci.yml`:

1. If `TWYN_APP_ID` (repo variable) is set → mint an App installation token.
2. Otherwise → fall back to the `TWYN_MAVEN_TOKEN` secret.
3. Pass it as the Maven password (`TWYN_MAVEN_TOKEN`) with username
   `x-access-token`.

The registry URL in `settings.gradle.kts`:

```
https://maven.pkg.github.com/twyn-internal/twyn-android-sdk
```

---

## Security notes

- **Never** commit tokens. Use repo/org secrets or, better, the App.
- The App needs only **Packages: Read** — nothing else.
- Rotate immediately if a token leaks (see `SECURITY.md`); push protection is on.
- The publishing side (`twyn-internal/twyn-android-sdk`) uses the ephemeral
  `GITHUB_TOKEN` with `packages: write` — no stored credential.
