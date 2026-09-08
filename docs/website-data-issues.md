# Website data issues (freebuddhistaudio.com)

Running log of wrong, missing or odd data the app has met on the website and its
`/api/v1` endpoints, with how the app copes. Useful both for the FBA team and
for the API migration. Dates are when the issue was observed.

## Durations

| Observed | Where | Value | Handling |
|---|---|---|---|
| 2026-09-05 | Talk `durationSeconds`, **LOC3883** *From Genesis to the Diamond Sutra* | `717860544` (≈22 years) on one fetch, `-3577106752` on a later one | Durations outside 1 s … 100 h are treated as missing; the talk length is the sum of its chapters. |
| 2026-09-05 | Chapter `durationSeconds`, **LOC3883** chapters 5 and 6 | `-1788562800` (both) | Estimated from the MP3 `Content-Length` using a sibling chapter as bitrate reference → 4229 s / 3618 s (the website's own player shows 70:29 for chapter 5, i.e. the same). |
| 2026 (v0.3.0 notes) | Talk `durationSeconds`, various talks | negative values such as `-1772841600` | Clamped to 0 since v0.3.0; now also covered by the plausibility check. |

The garbage values look like Unix timestamps (`1788562800` ≈ Sept 2026) that
ended up in the duration field.

## Language

| Observed | Where | Problem | Handling |
|---|---|---|---|
| 2026-09-05 | `lang_code` in `/api/v1/search` results | Only ever `en` or empty — never another language. Spanish talks tagged `en`: **LOC7113** *El mantra de Padmasambhava*, **LOC7109** *En pos de Padmasambhava*, **LOC7110** *Los demonios de la mente* (Amalamati, Valencia); German talk tagged `en`: **LOC2837** *Weibliche Formen von Erwachen* (Nagadakini, Essen). Empty for French **LOC779** *A La Rencontre De Padmasambhava* (Paris) and Spanish **LOC1477**. English talks appear in both groups too. | Not used. The English-only filter relies on the curated menu labels ("(deutsch)", "(en español)"…), the Languages menu section and the country in Places labels. |
| 2026-09-05 | Talk pages, collection/browse items | No language field at all. | As above. |
| 2026-09-05 | `document.__FBA__.language_code` on every page | Is the *site UI* language (`en`), not the talk's. | Ignored. |

## Search API (`/api/v1/search`)

| Observed | Problem | Handling |
|---|---|---|
| 2026-09-05 | `type=audio` / `type=series` / `type=text` / `type=speaker` all return the identical result set. | The app splits talks from series by the result's `link` (`/series/details` vs `/audio/details`). Speakers/places/collections are matched locally against FBA's indexes and menu. |
| 2026-09-05 | Each result also carries numeric keys duplicating fields (`"0": "X250", "1": "Living with Kindness…"`). | Ignored. |
| 2026-09-05 | Titles/blurbs contain HTML entities (`&oacute;`, `&#39;`) and blurbs contain HTML. | Unescaped / converted to text on parse. |

## Collections API (`/api/v1/collections/{type}`)

| Observed | Problem | Handling |
|---|---|---|
| 2026-09-05 | `page_size` (and `pageSize`, `per_page`, `items`) are ignored; the default page holds **one** item. Only `limit` works (up to at least 1000). | All paging uses `limit=24`; whole indexes (speakers, places) are fetched with `limit=1000`. |
| 2026-09-05 | Speaker index titles carry a count suffix — `"Abayanandi (1)"`; series titles carry `" (series)"`. | Stripped for display. |
| 2026-09-05 | `year` is a string in list items but a number in talk JSON. | Parsed either way. |
| 2026-09-05 | Placeholder images: 53 of 120 places and 184 of 736 speakers use `/images/default.jpg` / `/images/places/default.jpg`. | Treated as "no image" → generated artwork. |
| 2026-09-05 | `themes` index is the raw genre database: 478 entries incl. non-English genres (`Ästhetik und Kunst (deutsch)`), with HTML entities in the JSON. | The app uses the curated **themes** section of the site menu (~90 entries) instead. |

## Curated collection pages (`/collection/<slug>`)

| Observed | Problem | Handling |
|---|---|---|
| 2026-09-05 | `?page=N` is ignored; paging needs `?pageNo=N`. | Uses `pageNo`. |
| 2026-09-05 | No JSON API for named collections (`/api/v1/collections/<slug>` → 400). | Page HTML is parsed (`document.__FBA__.collectionData`). |

## Site menu (`document.__FBA__.sidebar_menu`)

| Observed | Problem | Handling |
|---|---|---|
| 2026-09-05 | Trailing spaces in links: `"/browse?cat=latest&t=audio "`, `"/collection/body-awareness "`. | Trimmed. |
| 2026-09-05 | Link without leading slash: `"browse?cat=series_sangharakshita&t=series"`. | Normalised. |
| 2026-09-05 | Typos in labels: `"series by sangharaskhita"`, `"cardiff (wales"` (missing bracket), `"saddakara (en español)"` (speaker is Saddhakara), `"mahashraddha"` linking to `Mahasraddha`. | Shown as-is (labels are FBA's). |
| 2026-09-05 | Entities in labels (`amalamati (en espa&ntilde;ol)`). | Unescaped. |
| 2026-09-05 | Mixed absolute/relative links; some "people" entries link to `/collection/<name>` rather than `/browse?s=`. | Both link kinds handled. |

## Login, session, account data

| Observed | Problem | Handling |
|---|---|---|
| 2026-09-05 | The `fba` session cookie is **re-issued on every response**; two concurrent requests with the same cookie → the second is redirected into the SSO (`sso.triratna.co/.../no_cookie.php`). | Session-carrying requests are sent one at a time while logged in; audio and images carry no cookies. |
| 2026-09-05 | Session cookies have no expiry attributes; server-side lifetime unknown (a session was still valid after ~5 h). | App re-checks with `/api/v1/my-details` at startup and logs out if the site says `loggedIn: false`. |
| 2026-09-05 | Checkpoint (`talk.checkpoint`) has `time_seconds` as a **string** (`"5"`) and **no timestamp**, so it can't be compared with a local position by recency. | Parsed either way; the account's checkpoint always wins when logged in. |
| 2026-09-05 | History `listenTime` uses `+0000` (no colon) — not strict ISO 8601. | Parsed with a tolerant formatter. |
| 2026-09-05 | Order-only talks return "Talk not found" (404 page) rather than a permission error; `om_only` is `false` on every item for anonymous/non-Order sessions. | Nothing to show until an Order-member test account exists. |
| 2026-09-05 | `/user/login` → 404 and the header's "log in" link is hidden; login is only reachable via `/sso/?login=true&returnTo=…` (Triratna SAML SSO) and the SSO form has no mobile styling. | Native login screen drives the SSO flow. |

## Other

| Observed | Problem | Handling |
|---|---|---|
| 2026-09-05 | `digitalLegacyPage.descriptionHtml` contains inline `style` attributes and links. | Converted to plain text. |
| 2026-09-05 | Series `marquee_image` often `null`; `image` may also be `null` with only `speaker_image` set. | Fallback chain marquee → image → speaker image. |
| 2026-09-05 | Talk `visibility_token` present on every talk (purpose unknown). | Ignored. |
