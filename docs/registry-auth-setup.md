# Registry access — dedicated machine account + classic PAT

> **Purpose:** give the CI (and anyone consuming the private SDK) a credential that
> is **not tied to a person**.
>
> **Why not a GitHub App?** GitHub Packages **Maven** only accepts a **personal
> access token (classic)** — App installation tokens return `401 Unauthorized`.
> (Tested: App `twyn-registry`, username `x-access-token` and the app slug, both 401.)
>
> **Where the packages live:** `https://maven.pkg.github.com/twyn-internal/twyn-android-sdk`
> (`com.t4isb:t4fastid`, `com.t4isb:sentinel-sdk`).

---

## Part 1 — Create the machine account

1. Open a **private/incognito window** (so you don't create it under your own login)
   and sign out of GitHub.
2. Go to **https://github.com/signup**.
3. Fill in:
   - **Email:** a company alias, e.g. `ci@twyn.me` (or `github-ci@twyn.me`).
     If your mail provider supports plus-addressing you can also use
     `you+twyn-ci@twyn.me`.
   - **Password:** generate a strong one and store it in the company password
     manager.
   - **Username:** `twyn-ci` (if taken, `twyn-ci-bot`).
4. Solve the puzzle and click **Create account**.
5. **Verify the email** (code sent to the alias). If the alias is a distribution
   list, make sure someone can read it.

> This account is a **service identity**, not a person. Document it in the
> password manager: username, email, password, 2FA seed, recovery codes.

---

## Part 2 — Enable 2FA (required to join an organization)

GitHub **requires 2FA** for members of an organization. Without it you cannot add
`twyn-ci` to `twyn-internal`.

1. Sign in as `twyn-ci`.
2. **Settings → Password and authentication → Two-factor authentication → Enable**.
3. Choose **Authenticator app (TOTP)** and scan the QR with a company-owned
   authenticator (or a shared TOTP store).
4. Save the **recovery codes** in the company vault.

---

## Part 3 — Add the account to `twyn-internal`

1. Sign back in as **your admin account**.
2. **`twyn-internal` → People → Invite member** → enter `twyn-ci` → role **Read**
   (least privilege) → **Send invitation**.
3. Accept the invitation as `twyn-ci`.

Then make sure the account can *see the package* (either is enough):

- **Option A — repo access:** give `twyn-ci` **Read** on the repo
  `twyn-internal/twyn-android-sdk` (where the package is linked).
- **Option B — package access:** package page (`com.t4isb.t4fastid`) →
  **Package settings → Manage access → Add `twyn-ci`** (Read).

---

## Part 4 — Create the classic PAT

Signed in as `twyn-ci`:

1. **Settings → Developer settings → Personal access tokens → Tokens (classic) →
   Generate new token (classic)**.
2. **Note:** `twyn-ci registry read`.
3. **Expiration:** `90 days` (put a calendar reminder to rotate).
4. **Scopes:**
   - ✅ **`read:packages`** — required.
   - ✅ **`repo`** — only if you get `401/404` without it (private repo linked to
     the package). Try without it first.
   - nothing else.
5. **Generate token** and copy the `ghp_…` value.

---

## Part 5 — Set the secrets on the consumer repos

Run as your admin (or paste the token into a file `token.txt` first):

```powershell
# Public sample
gh secret set TWYN_MAVEN_USER  --repo twyn-public/twyn-sdk-android    --body "twyn-ci"
gh secret set TWYN_MAVEN_TOKEN --repo twyn-public/twyn-sdk-android    < token.txt

# Internal demo
gh secret set TWYN_MAVEN_USER  --repo twyn-internal/twyn-demo-android --body "twyn-ci"
gh secret set TWYN_MAVEN_TOKEN --repo twyn-internal/twyn-demo-android < token.txt
```

Delete `token.txt` afterwards.

> The CI workflow does **not** change — it already reads `TWYN_MAVEN_USER` /
> `TWYN_MAVEN_TOKEN` (see `.github/workflows/ci.yml`).

---

## Part 6 — Validate

```powershell
# Re-run the last CI of each repo
gh run list --repo twyn-public/twyn-sdk-android --workflow CI --limit 1
gh run rerun <run-id> --repo twyn-public/twyn-sdk-android

gh run list --repo twyn-internal/twyn-demo-android --workflow CI --limit 1
gh run rerun <run-id> --repo twyn-internal/twyn-demo-android
```

Both should end **success** resolving `com.t4isb:t4fastid` from the private registry
using only the `twyn-ci` credentials.

---

## Part 7 — Housekeeping

- [ ] Store account + PAT + 2FA seed + recovery codes in the password manager.
- [ ] Calendar reminder to **rotate the PAT** before the 90-day expiry.
- [ ] If the token ever leaks: revoke it immediately (Settings → Tokens), issue a
      new one, update the secrets, and check the org audit log.
- [ ] Remove the old personal token from the secrets (already replaced in Part 5).

---

## Reference

- GitHub docs — *Working with the Apache Maven registry* (states classic PAT only).
- `docs/security-hardening.md` — enterprise/org hardening checklist.
- Future alternative: **AWS CodeArtifact + OIDC** (no long-lived secret) — see
  `twyn-public/twyn-sdk-android/docs/registry-auth.md`.
