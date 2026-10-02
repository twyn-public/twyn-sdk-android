# Registry authentication

The SDK binaries live in the **private** org `twyn-internal` (GitHub Packages).
Consumers (the public sample, the demo, CI) need a credential with `read:packages`
that can reach `twyn-internal`.

## ⚠️ Important: GitHub Packages Maven requires a **classic PAT**

GitHub's own docs state: *"GitHub Packages only supports authentication using a
personal access token (classic)."* In practice:

| Credential | Maven on GitHub Packages |
|---|---|
| **PAT (classic)** with `read:packages` | ✅ works |
| Fine-grained PAT | ⚠️ not supported for Maven (use classic) |
| **GitHub App** installation token | ❌ **401 Unauthorized** |
| `GITHUB_TOKEN` | ✅ only for packages in the **same** repo/org |

We tried a dedicated **GitHub App** (`twyn-registry`, `Packages: read`) — the token
is minted fine, but `maven.pkg.github.com` returns **401**. So the App cannot be
used for the Maven registry. (It can still be useful for the API or other
registries.)

**Therefore the consumer credential must be a classic PAT.** Prefer a **dedicated
machine account** so it is not tied to a person.

---

## Recommended: dedicated machine account + classic PAT

1. Create a GitHub **machine account** (e.g. `twyn-ci`), add it to `twyn-internal`.
2. Sign in as it → **Settings → Developer settings → Personal access tokens →
   Tokens (classic)** → Generate new token.
   - Scope: **`read:packages`** only.
   - (If it must also read private repos: add `repo`.)
3. Set the credentials on each consumer repo:

```bash
gh secret set TWYN_MAVEN_USER  --repo <owner>/<repo> --body "twyn-ci"
gh secret set TWYN_MAVEN_TOKEN --repo <owner>/<repo> < token.txt
```

The workflow passes them as the Maven username/password:

```yaml
env:
  TWYN_MAVEN_USER: ${{ secrets.TWYN_MAVEN_USER }}
  TWYN_MAVEN_TOKEN: ${{ secrets.TWYN_MAVEN_TOKEN }}
```

Registry URL (`settings.gradle.kts` / `build.gradle`):

```
https://maven.pkg.github.com/twyn-internal/twyn-android-sdk
```

---

## Alternative: move the registry to a service with OIDC

If a long-lived PAT is unacceptable, host the Maven artifacts on a registry that
supports short-lived/oidc credentials and use GitHub Actions **OIDC**:

- **AWS CodeArtifact** (you already run on AWS) — GitHub OIDC → assume an IAM role
  → `aws codeartifact get-authorization-token`. No stored secret.
- Azure Artifacts / Cloudsmith / JFrog — similar.

This is the most secure long-term option but requires moving publishing and the
consumer configuration.

---

## Security notes

- **Never** commit tokens. Use repo/org secrets.
- Use a **machine account**, not a person, so the credential survives staff changes.
- Rotate immediately if a token leaks (`SECURITY.md`); push protection is on.
- The publishing side (`twyn-internal/twyn-android-sdk`) uses the ephemeral
  `GITHUB_TOKEN` with `packages: write` — no stored credential.
