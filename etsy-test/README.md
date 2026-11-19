# BootyBay: Hallowmiss mug studio (Android)

BootyBay is a low-power Android app. It designs **Hallowmiss** creepy-Christmas mugs from public-domain
medieval/Victorian etchings on Wikimedia Commons, sends the ones you approve to **Printful**, lists them
on **Etsy**, and watches sales.

## Get the APK
Every push builds the app in GitHub Actions (workflow **BootyBay APK**). Download `BootyBay.apk` from the
**bootybay-latest** release, or from the run's artifacts. Install it, allowing installs from your browser
when Android asks.

## How it works
| Piece | What it does |
|---|---|
| **GO / PAUSE** | Starts or stops the background routine. The status dot shows RUNNING / WORKING / STALLED / PAUSED. |
| **Heartbeat (every 15 min)** | One `HEAD` request to nasa.gov, reading its clock (shows last ping and drift). It also checks Etsy for new sales, then decides whether a design batch or research run is due. |
| **MAKE 10** | Makes 10 designs now, even while paused. |
| **Automate** | Makes a batch (default 10) every 24 h. Stops at 30 designs waiting for approval so it doesn't burn battery on work you haven't reviewed. |
| **Design engine** | Searches Commons for public-domain/CC0 etchings (witches, skulls, danse macabre, Krampus, ravens, and on the Christmas side Santa, holly, firs, and so on). It adaptively thresholds each scan into clean ink, **vectorises** it (outline trace, RDP simplification, smooth curves), and lays out a 2700×1050 Printful 11oz wrap printed on both sides. The Halloween etching gets snowfall knocked out of the ink and holly on the frame. The Christmas etching is inked blood-red or pine with bats circling. Each design saves a print PNG and a vector `.svgz`. |
| **Approvals** | Approve, reject, or leave feedback. Rejections lower that motif's weight, approvals raise it. |
| **Post** | Approved design → imgbb (Printful needs a URL) → Printful product + mockup → Etsy listing with mockup and wrap images → published (or left as a draft, a setting). |
| **Sales** | New paid Etsy receipts trigger a notification and a Printful order (draft by default, auto-confirm is optional). |
| **Finances / Stats** | Revenue, actual Printful costs, estimated Etsy fees, listing fees, net profit; 30-day chart, top designs, motif performance. |
| **R&D** | Daily scan of the top ~100 Etsy results for Christmas / Halloween / creepy-Christmas mugs (plus eBay with keys). Shows trending motifs, keywords, and median price, and feeds the generator. |
| **Ideas** | Suggestion box: "more krampus", "fewer owls", or a phrase in "quotes". Also shows automatic advice and the current motif weights. |

### Power choices
- No foreground service and no wakelocks. One OS-batched 15-minute WorkManager job, network-gated.
- Design batches only run when the battery isn't low (optionally only while charging).
- Images are downloaded as ~1000 px thumbnails. The UI is pure black (OLED-off) with no animations.

### Guardrails
- Only public-domain / CC0 source images are used. Credits are stored with each design.
- R&D copies trends (themes, keywords, prices), never other sellers' artwork.
- A trademark blocklist (Disney, Grinch, Hocus Pocus, …) filters keywords, tags and suggestions.
- Amazon has no open product-search API, so it is not scraped.

## Setup (Settings tab)
1. **Printful**: create a *Manual order platform / API* store and a private API token. Tap *Find my stores* and enter the store ID.
2. **imgbb**: free API key from api.imgbb.com.
3. **Etsy**: create an app at etsy.com/developers. Add the redirect URI `bootybay://etsy-callback`, enter the keystring and shared secret, then tap *Connect Etsy*. You need a shipping profile in your shop; Printful's Etsy shipping template works.
4. Optional: eBay developer App ID / Cert ID for eBay research.
5. Tap **GO**.

Build locally: `./gradlew assembleRelease` (JDK 17 + Android SDK 35).
