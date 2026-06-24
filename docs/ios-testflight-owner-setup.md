# FBAudio iOS — TestFlight setup (for the account holder)

This is a one-time setup that lets the FBAudio iOS app be built and distributed
to beta testers via **TestFlight**, while you keep full control of the signing
identity and can revoke access at any time.

**Roughly 15 minutes of your time**, plus a one-time Apple "Beta App Review"
(automatic, usually within a day) before external testers can install.

## What this does and doesn't do
- ✅ Lets pre-release builds go to testers' real iPhones/iPads via TestFlight.
- ✅ You keep control: everything below is **revocable** by you at any time.
- ❌ This is **not** a public App Store release. Nothing goes live to the public;
  no store listing, screenshots, or review for sale is involved.

You'll create two things and send them to the developer:
1. A **distribution certificate** (from a CSR the developer gives you).
2. An **App Store Connect API key** with the **App Manager** role.

The developer builds and uploads from their own CI (Codemagic) using these; you
never have to run a build or click through an upload yourself.

---

## Step 0 — prerequisites
- You're the **Account Holder** or an **Admin** on the Apple Developer account
  (these steps need that level; App Manager/Developer can't do them).
- The app record already exists in App Store Connect (bundle ID
  `com.dharmachakra.fba`, app "Free Buddhist Audio").
  If it doesn't, create it first: App Store Connect → Apps → ➕ → New App → iOS,
  bundle ID `com.dharmachakra.fba`, a primary language, and any SKU.

---

## Step 1 — create the distribution certificate (from the developer's CSR)
The developer will send you a file named **`fba_distribution.certSigningRequest`**.
This is safe to share — it contains only a public key; the matching private key
never leaves the developer's build machine.

1. Go to **developer.apple.com → Certificates, IDs & Profiles → Certificates**.
2. Click the **➕**.
3. Under **Software**, choose **Apple Distribution** → **Continue**.
4. **Choose File** → upload the `fba_distribution.certSigningRequest` they sent.
5. **Continue** → **Download** the resulting **`.cer`** file.
6. **Send that `.cer` file back to the developer.**

> Note: a distribution certificate is account-wide and limited to ~2–3. If you're
> at the limit, you don't need a new one — tell the developer and they'll reuse an
> existing one (you'd then export it as a `.p12` instead; ask and they'll explain).

---

## Step 2 — create an App Store Connect API key (App Manager)
1. Go to **App Store Connect → Users and Access → Integrations** tab →
   **App Store Connect API** (the "Team Keys" section).
2. Click **➕** (Generate API Key).
3. **Name**: e.g. `FBAudio CI`.
4. **Access**: **App Manager**.  *(This is the minimum role that lets the
   automation upload builds AND distribute them to TestFlight testers. Admin also
   works but isn't needed.)*
5. **Generate**.
6. Note the **Issuer ID** (shown at the top of the Keys list) and the **Key ID**
   (next to the key), and **Download** the **`.p8`** file.
   - ⚠️ The `.p8` can only be downloaded **once** — keep a copy.

---

## Step 3 — send the developer four things
1. The **`.cer`** from Step 1
2. The **Issuer ID**
3. The **Key ID**
4. The **`.p8`** key file

Please send these over a **private channel** (not a public post). The `.p8` +
Issuer + Key ID together can upload builds as your account, so treat them like a
password.

---

## What happens after that (developer side — no action needed from you)
- The developer wires these into Codemagic (the macOS CI). From then on they
  push code → CI builds, signs, and uploads to TestFlight automatically.
- **The first external build triggers a one-time Beta App Review** by Apple
  (usually < a day). You may receive a notification email about it; no action is
  typically required. Later builds clear automatically unless something major
  changes.
- Testers install via the **TestFlight app**, either by invite or a public link.

## Your ongoing control
- **Revoke anytime**: App Store Connect → Users and Access → Integrations →
  revoke the API key. Builds/uploads stop immediately; the developer keeps no
  lingering access.
- The API key is **scoped to App Manager** — it cannot manage account users,
  billing, or legal agreements, and cannot elevate itself.
- The signing **private key stays on the build machine**, not in the certificate
  you handed over, so the `.cer` alone can't be misused.

## Optional: if you'd rather host the build pipeline yourself
Instead of handing over the API key, you can own the Codemagic project and store
the key there as a secret, adding the developer as a build-triggering
collaborator. Same end result; the key never leaves your environment. Ask the
developer if you'd prefer this — they'll set it up that way.
