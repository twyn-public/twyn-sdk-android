# Security Policy — twyn-sdk-android (public sample)

## Reporting a vulnerability

Please **do not** open a public issue. Report privately:

- GitHub → **Security** → **Report a vulnerability** (Security Advisories), or
- email **security@twyn.me**

We aim to acknowledge within 2 business days.

## Scope

This repository contains the **public integration sample only**. The Twyn SDK
binary is proprietary and covered by your commercial agreement — report issues in
the binary through the same private channel.

## What this repository must never contain

- ❌ **Secrets**: no tokens, keys, keystores or credentials (`ghp_`, `gho_`, `.pem`,
  `.jks`, …). Credentials live in `gradle.properties` (git-ignored) or CI secrets.
- ❌ **Production/dev hostnames hardcoded**: the sample uses **placeholders**
  (`https://your-gateway.example.com`, `https://your-sentinel.example.com`). The
  integrator sets their own endpoints.
- ❌ **Personal data**: no real `personId`, no captured faces.

> A CI grep + **push protection** guard against accidental commits of secrets.

## Endpoints

The sample's *Gateway base URL* and *Sentinel base URL* fields default to
**placeholders**. They control only:
- the **audit read** (the decision shown in the result dialog), and
- the **Sentinel scan** (device integrity).

They do **not** change the SDK's own liveness transport (baked into the SDK binary).

## Registry access

The SDK is pulled from a **private** Maven registry. Use a GitHub token with the
**`read:packages`** scope, ideally from a dedicated machine account, with an
expiry. See the org-level `docs/security-hardening.md` (machine accounts &
token rotation).

## Supported versions

The latest released SDK version. Older majors are supported on request.
