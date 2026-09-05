# FBAudio v1.0 — What changes, and what we can do before the new API

**Based on:** "Specification for first FBA app version (1.0)" (5 Sep 2026)
**Launch target:** 6 Dec 2026 (FBA's 20th anniversary)
**Purpose:** compare the app as it is today with what the spec asks for, and
say for each change whether it can be built now — before the new FBA API
exists — or has to wait.

Each change is tagged:

| Tag | Meaning |
|---|---|
| **Now** | Buildable today using what the FBA website already provides. |
| **Now, switched off** | Buildable today, but shipped hidden behind a switch until a decision or account is in place (e.g. gating downloads before Join exists). |
| **Needs API** | Waits for the new FBA API. |
| **Needs FBA** | Waits for a decision or asset from the FBA team, not on code. |

Everything applies to both Android and iOS.

---

## 1. Headline findings

1. **Almost the whole spec can be built now.** The website already exposes,
   in machine-readable form, everything the spec's home page needs: the
   curated Collections and Themes, People, Places, Years, Series (with
   images and blurbs), Latest, Introductions, Meditations, the Digital Legacy
   page, and — importantly — the **remastered audio** for each talk.
2. **Login works today.** FBA uses the Triratna single sign-on (the same
   login as The Buddhist Centre Online). Verified with a test account: the
   app can log a user in through the website's own login page and then read
   and write their **listening history** and **resume position**, so
   "Recently listened" can sync with the website now. A proper API login
   should replace this later, but it is not a launch blocker.
3. **Order-only talks** are hidden server-side for anyone who isn't an Order
   member. The mechanism is there; we need an **Order-member test account**
   to verify and finish it.
4. **Transcript ("Text") search does not exist on the website** for
   anonymous users — the search endpoint returns the same results whatever
   type is asked for. Text search therefore waits for the server side; the
   app will not use our own transcripts.
   *Transcripts vs seminars:* FBA has three kinds of text — **lecture
   transcripts** (transcripts of Sangharakshita's recorded talks; this is what
   the app's "View Transcript" shows), **seminar transcripts** (multi-day
   study seminars, text only, no corresponding talk — the literary-executor
   concern), and transcripts of other speakers' talks. The app has never
   surfaced seminars: search returns only talks and series, and the seminar
   browse pages were never wired in. "Remove seminars" therefore costs
   nothing — we simply don't add a texts section.
5. The store/donation policy question in the spec (Apple/Google fees on
   link-out donations) is the one genuine launch blocker, and it is an FBA
   decision, not development work.

---

## 2. Decisions taken in this document (confirm or overrule)

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D1 | Nav bar with 6 tabs (Home · Search · Downloads · Donate · Join · My FBA) | **5 tabs** — Donate is a button on Home, My FBA and every talk/series page instead of a tab. *Agreed.* | iOS only shows 5 tabs; a 6th is hidden under "More", so Donate would vanish. |
| D2 | Gating downloads on membership | Build the gate, ship it **switched off** until Join actually works. | Testers currently download freely; gating with no way to join gains nothing. |
| D3 | Which version plays when a talk has a remastered version | **Remastered** by default, toggle to Original in the player, choice remembered per talk. | Matches FBA's Digital Legacy push. |
| D4 | English \| All languages toggle | Ship as a setting, default **English**, using the language markers available today; refine when FBA supplies proper tags. | Best effort now beats nothing. |
| D5 | Text (transcript) search | **Server-side only.** The app does not ship or use our own transcripts; the Text tab appears when FBA's search supports it. *Agreed.* | Keeps content handling with FBA. |
| D6 | Login before the API | Use the website's Triratna login inside the app (**switched on** once tested on devices). | It's FBA's real login and works today. Downsides: the login page isn't mobile-styled, and we don't yet know how long a session lasts before re-login. |
| D7 | Analytics | None in v1 beyond the app-store consoles. | Nothing in the spec needs them; avoids privacy-policy work before launch. |

---

## 3. Changes, screen by screen

### 3.1 Navigation bar — **Now**

| Today | Change |
|---|---|
| Three tabs: **Home · Search · Downloads**. | Five tabs: **Home · Search · Downloads · Join · My FBA** (per D1). |
| Downloads open for everyone. | When gating is switched on and the user isn't a member, Downloads and the *Download* buttons lead to **Join**. Existing downloads stay playable. |
| Recently listened lives on Home. | Moves to **My FBA**, unchanged, with a Donate button beside it. |

### 3.2 Look and feel — **Now**

| Today | Change |
|---|---|
| Body and secondary text are brown-tinted (light mode). | Text becomes **black**; the brand orange stays for buttons, active tab, links and progress bars. Dark mode unchanged. |

### 3.3 Home page — **Now** (Order talks: see 3.11)

| Today | Change |
|---|---|
| Sangharakshita card with *By Year* and *Series* links. | Keep. Series list gains images. |
| "Support Free Buddhist Audio" card. | Keep as the **Support FBA** button, positioned as in the spec. |
| Recently listened. | Moves to My FBA. |
| No logo/header, no login. | Header: logo + "Free Buddhist Audio" + **Log in / out**. |
| — | **Digital Legacy** card with the support ask and a button; opens an in-app page with the FBA copy, the original-vs-remastered sample, and a link to the *Buddhism for Today – and Tomorrow* series. |
| — | **Collections** image grid (see 3.4). |
| — | **Order talks** link, only for logged-in Order members (3.11). |
| — | Text-link rows, each opening a titled list of talks/series with images: **Introductions, Meditations, Latest, Themes, Series, People, Places**. All fed from the website's existing lists; Themes/People/Places use FBA's curated named lists (the ones in the website's side menu), not the raw tag database. |
| — | **Connect** row: FBA podcast, Dharmabytes, YouTube, Facebook, Instagram, SoundCloud, The Buddhist Centre Online — logos linking out. |
| Home needs the network for the lists. | Home and Collections are cached so they open instantly and work offline after first use. |

*Option for FBA:* the website's editorial "highlights" posters could appear as
a scrolling row on Home, giving FBA a way to feature content without an app
release. Recommend yes.

### 3.4 Collections — **Now**

| Today | Change |
|---|---|
| No collections; a fixed list of 12 topics exists in code but isn't reachable from the UI. Mitra Study data is bundled but no longer reachable either. | A **Collections** screen showing FBA's curated collections as image tiles (The Buddha, Meditation & Mindfulness and its sub-collections, Introducing Buddhism, Living a Buddhist Life, Buddhist Ethics, Buddhist Wisdom). Tiles use FBA's cover image when one exists, otherwise **auto-generated colour artwork** (stable per collection, Apple Music style). Tapping opens a titled, paginated talk list in the same style as search results. |

### 3.5 English | All languages — **Now, switched off** → refine with **FBA** markers

| Today | Change |
|---|---|
| No language handling; Latest shows Spanish/German talks mixed in. | A setting (default English). Non-English content is hidden using the markers FBA already maintains — see below. With the tags or catalogue prefixes FBA has offered, this becomes exact. |

*What markers exist today:*
1. **Curated menu labels** carry a language suffix — "(deutsch)", "(en español)", "(in het nederlands)", "(på svenska)", "(en français)", "(हिंदी में)", "(po polsku)", "(em português)", "(norsk)", "(на русском)"; bilingual speakers are marked "deutsch \| english". Reliable; covers the People and Themes lists.
2. **FBA's Languages menu**: dedicated collections for German, Hindi, Spanish, French (Paris), Dutch, Norwegian (Oslo), Polish, Portuguese (São Paulo), Russian, Finnish, Swedish (Stockholm). Reliable and FBA-maintained.
3. **Places labels** name the country — "(españa)", "(deutschland)", "(méxico)", "(nederland)", "(france)", "(norge)", "(sverige)", "(brasil)", "(россия)". Each talk item carries its centre, so talks from those centres can be hidden.
4. The search results' **language code** — *unreliable* (this week's Spanish talks from Valencia are tagged English). Not used.

The app hides menu entries via (1), and talks via speakers marked in (1)/(2) and centres from (3). India is left visible (talks there are in English or Hindi).

### 3.6 Remastered audio — **Now**

| Today | Change |
|---|---|
| Always plays the original recording; the remastered versions on the website are ignored. | Talks and series with remastered audio get a **Remastered** badge. The player shows a **Remastered \| Original** toggle (only when both exist); remastered plays by default (D3); switching keeps your place. Downloads store the version that would play. The Digital Legacy page uses the same toggle for its sample. |

### 3.7 Search — **Now** (Text tab: **Now, switched off**, needs FBA)

| Today | Change |
|---|---|
| Two modes: **All** and **By speaker**. Talks and series appear in one mixed list. | Tabs **All · Audio**; *All* shows Talks and Series as separate groups, *Audio* shows talks only. The **By speaker** mode is removed (speakers are browsed from People instead). A **Text** tab is added when the server supports transcript search. |
| Seminars were never reachable in the app. | Nothing to remove; the texts/seminars section is deliberately not added. |

### 3.8 Talk and series pages — **Now**

| Today | Change |
|---|---|
| Talk page: image, speaker, series link, Play, Download, View Transcript, description, chapters. | Add a **Donate** button directly under *View Transcript*, and the Remastered badge where relevant. |
| Series pages are plain talk lists without images or text. | Series lists show **images**; series pages show the **blurb** from the website above the talks, with a **Donate** button under it. |

### 3.9 Downloads and transcripts — **Now**

Two of the spec's questions are already answered by the current app:
transcripts **are** downloaded with a talk (since v0.3), and downloaded talks
**always** play from the offline copy, wherever you start them from.

| Today | Change |
|---|---|
| Downloads list shows talks; no indication that a transcript is included. | Each item shows what's stored (**Audio · Transcript**, and Remastered/Original). Filter: **All · Talks · Transcripts**. Transcripts can be downloaded on their own from a talk page. |

### 3.10 Join page — **Now** for the page; purchases **Need FBA** + store setup

| Today | Change |
|---|---|
| Nothing. | A **Join** page with the benefits of joining and the two subscription options. Until in-app purchases are live the buttons say subscriptions open at launch. Purchases themselves go through Apple/Google's own subscription systems; that needs FBA to resolve the store policy and set up the products, and doesn't depend on the API. |

### 3.11 My FBA, login and Order-only talks

| Today | Change | Tag |
|---|---|---|
| No account concept. | **My FBA** tab: account header with Log in / out, Recently listened (moved from Home), Donate, Settings (language toggle, remastered default). | **Now** |
| — | **Log in** with the existing FBA/Triratna account, via the website's own login page shown inside the app. Shows the username after login. | **Now, switched off** until device-tested (D6) |
| Recently listened is app-only. | **Syncs with the website**: talks listened to on the web appear in the app and vice versa; the resume position also syncs, so a talk started on the web continues in the app. Verified with the test account. | **Now, switched off** with login |
| — | **Order talks** link on Home and Order-only items in lists, for logged-in Order members. The website already hides these for everyone else. | **Now, switched off** — needs an **Order-member test account** to verify |
| — | Subscription status tied to the FBA account. | **Needs API** |

### 3.12 Later (v1.1+)

- **Android Auto / CarPlay** — feasible; Android is the cheaper half, CarPlay needs an Apple entitlement application. Best done together with the API work already deferred in `docs/TODO.md`.
- **Transcripts for all talks** and **text search** — server side, when FBA provides them.
- **Playlists** — as the spec says.

---

## 4. Order of work

| Phase | What | Depends on |
|---|---|---|
| 0 | Groundwork so the API can be swapped in later without touching screens; black text; remove seminars | — |
| 1 | New tabs, My FBA and Join pages, new Home layout | — |
| 2 | Collections, Themes/People/Places, generated artwork, language toggle | — |
| 3 | Remastered audio + Digital Legacy page | — |
| 4 | Search tabs, downloads/transcript polish, series images and blurbs, donate buttons | — |
| 5 | Login + history/resume sync | device testing |
| 6 | Order-only talks | Order-member test account |
| 7 | Subscriptions in-app; switch on download gating | FBA store decisions |
| Later | API migration: swap the data layer, token login, playback resumption from `docs/TODO.md` | new API |

Phases 0–5 have no external dependency. If the API slips, the whole spec
still ships on what the website provides today; the API becomes a
post-launch swap.

---

## 5. Questions for FBA

1. Language markers / catalogue prefixes for non-English talks (3.5).
2. Will the new search support transcript ("Text") search, and for which talks?
4. Should the website's "highlights" posters appear on Home (3.3)?
5. Are transcript-only downloads free or member-only?
6. Store policy: link-out donations vs in-app donation; non-profit status; who sets up the subscription products (3.10)? This gates submission to the stores.
7. An **Order-member test account** (or the Order role on our test user) so Order-only talks can be verified (3.11).
8. Is using the website's Triratna login inside the app acceptable until the API ships (D6)? How long does a login session last?
9. When the API arrives: will audio be behind protected links, and what will login look like?

---

## 6. The spec's own questions, answered

| Question | Answer |
|---|---|
| Does removing seminars mean no text results? | Today, yes — there is no transcript search on the website for anonymous users. Text search arrives when the server provides it (D5). |
| Automated transcripts for all talks? | Server side, when FBA provides them. |
| Are transcripts downloaded with the talk? | Already yes. |
| Do downloaded talks always play offline? | Already yes, from any entry point. |
| Can website history be merged into My FBA? | Yes, now — verified with the test account (3.11). |
| CarPlay / Android Auto? | Feasible; v1.1 (3.12). |
| Own analytics? | Recommend none in v1 (D7). |
| Playlists? | v1.1+, as stated. |
