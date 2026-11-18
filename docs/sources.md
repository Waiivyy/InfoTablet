# Data sources

Verified on 2026-11-16/17 by fetching every source live. This file records what each source returns, how the app reads it, how often it is polled, and what happens when it fails.

## Ground rules for every source

- **User-Agent:** `InfoTablet/<version> (home information display)`. No cookies are stored; session cookies that sites set are ignored.
- **Politeness:** requests run one at a time, at least 1 s apart per host. Nothing is polled more often than the table below says. Every result is cached in SQLite and shown from the cache, so a refresh never blocks the screen.
- **gzip:** let the HTTP client add `Accept-Encoding: gzip` itself (OkHttp does). Every Cloudflare-fronted source serves gzip; FHMZ does not.
- **Failure:** a source that fails keeps its last good data on screen, greyed out, with "Nema podataka" and the time of the last success. One source failing never affects another.
- **TLS:** every source forces HTTPS (HTTP redirects with 301/302). Android 4.2 needs TLS 1.2 enabled and modern root certificates. OkHttp 3.12 + Conscrypt handles the protocol; the roots are bundled in the app (see "TLS and certificates" at the end).
- **Attribution:** each tile shows its source name; "O aplikaciji" lists all sources with their licence.

## Summary

| Tile | Source | Format | Refresh | Status |
|---|---|---|---|---|
| Weather | Open-Meteo forecast API | JSON | 30 min | Works |
| Air quality (forecast, pollen) | Open-Meteo Air Quality API (CAMS Europe) | JSON | 60 min | Works; model underestimates valley smog |
| Air quality (measured) | FHMZ zrak portal (official stations) | JSON, HTML fallback | 60 min (at HH:30) | Works; undocumented endpoint |
| Weather warnings | MeteoAlarm feed for BA | JSON (or Atom) | 30 min | Works |
| Smog episodes | Vlada Kantona Sarajevo news list | HTML | 30 min in season, 3 h otherwise | Works; two levels only (Upozorenje, Uzbuna) |
| Fuel | uhvatipopust.ba (data from FMT eOPC) | HTML with embedded JSON | 3x/day | Works |
| Fuel (alternative) | cijenegoriva.ba | JSON API | n/a | Not used: terms forbid display in apps without a written agreement |
| News | klix.ba, n1info.ba, radiosarajevo.ba | RSS/Atom + article HTML | 30 to 60 min | Works |
| Sport | nfsbih.ba (BiH football federation) | HTML + tiny JSON | 2 to 3x/day, more on match days | Works |
| Sport (planned) | API-Football free plan | JSON | n/a | Not usable: free plan excludes the current season |
| Exchange rates | CBBH daily list | JSON | daily after 16:05 | Works |
| Holidays | Built-in table 2026 to 2030 | static | app updates | Works; religious days are not general days off |
| Water works | ViK Sarajevo service notices | HTML | 30 to 60 min on working days | Works |
| Power cuts | Elektroprivreda BiH planned outages | HTML (one table) | 3 h | Works; planned cuts only |
| Heating | Toplane-Sarajevo: unheated buildings today, announcements | HTML | 45 min by day in the heating season | Works |
| Gas works | Sarajevogas news feed | RSS (WordPress) | 3 h | Works; notices are rare |
| Roads and borders | BIHAMK | RSS (HTML inside) + HTML | 30 to 60 min, 06 to 21 h | Works |
| Winter equipment | Static rule (1 Nov to 1 Apr) | none | none | n/a |
| Updates | GitHub Releases API | JSON | monthly, on demand, after 2 days of failures | Works |

---

## Weather: Open-Meteo forecast

- **URL:** `https://api.open-meteo.com/v1/forecast?latitude=43.8563&longitude=18.4131&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,precipitation,is_day,pressure_msl&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,snowfall,uv_index&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,precipitation_sum,snowfall_sum,uv_index_max&timezone=Europe%2FSarajevo&forecast_days=7&forecast_hours=24`
- **Response:** about 3 KB. `current` (15-minute resolution), `hourly` arrays (24 entries from the current hour), `daily` arrays (7 days). Times are local ISO strings without offset (`2026-11-17T06:43`) because `timezone` is set. Grid elevation 543 m.
- **Used for:** current conditions, next 12 hours, 7 days, sunrise and sunset (also drive the light/dark theme switch and night dimming), precipitation probability, snowfall, UV. A second city (for example Munich) is a smaller call with other coordinates: `current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1`, once an hour, only when a second city is chosen. `timezone=auto` gives the city's own day and `utc_offset_seconds`, so the tile can show its local time. Places come from a built-in list (no geocoding requests).
- **Weather codes:** WMO codes (0 clear, 1 to 3 cloud cover, 45/48 fog, 51 to 57 drizzle, 61 to 67 rain, 71 to 77 snow, 80 to 82 showers, 85/86 snow showers, 95 to 99 thunderstorm). The app maps them to Bosnian text and an icon.
- **Terms:** free for non-commercial use, under 10,000 calls/day. Data under CC BY 4.0: show "Weather data by Open-Meteo.com" with a link (in O aplikaciji; the tile shows "Open-Meteo"). Our use is about 50 to 100 calls/day.
- **Failure:** keep the last forecast; if it is older than 3 hours, grey it out. Without any forecast the theme and dimming use a sunrise/sunset calculation (within 1 to 2 minutes of Open-Meteo for Sarajevo).
- **Home location:** one of the ten BiH cities that name a MeteoAlarm region (Sarajevo by default), so the warning region always matches. Fuel, air stations, water and smog stay Sarajevo-only sources.

## Air quality

### Forecast and pollen: Open-Meteo Air Quality (CAMS Europe)

- **URL:** `https://air-quality-api.open-meteo.com/v1/air-quality?latitude=43.8563&longitude=18.4131&current=european_aqi,pm10,pm2_5,nitrogen_dioxide,ozone,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen&hourly=european_aqi,pm2_5,pm10&timezone=Europe%2FSarajevo&forecast_days=2&domains=cams_europe`
- **Response:** about 2.4 KB. Grid cell 43.9 / 18.4 (about 10 km). Pollen values are 0 outside the season.
- **Caveat:** the model cell averages over the valley and misses inversions. At 00:00 on 17 Nov it gave PM2.5 25.5 µg/m³ (AQI 46, "fair") while the official Otoka station measured PM2.5 85 µg/m³ ("poor"). So the app shows measured station values as the headline number and uses CAMS for the 48-hour trend and pollen.
- **Terms:** CC BY 4.0 via Open-Meteo; credit "Copernicus Atmosphere Monitoring Service (CAMS)".

### Measured: FHMZ stations (zrak.fhmzbih.gov.ba)

- **Primary (undocumented JSON), as implemented:** three calls for all stations, `/rest/index/data/eaqi/0`, `/rest/index/data/pm25/0` and `/rest/index/data/pm10/0` (about 3 KB each, no gzip), rows `[id, code, name, lat, lon, canton, value, level, isComplete, ...]`, plus the portal home page (6 KB gzip) whose `#f-filter-hour option[value=0]` title gives the measurement hour ("17.11.2026. u 01:00"; "u 24:00" means midnight). Per-station detail exists at `/rest/index/data-station/{id}/0` (rows `[componentId, name, value µg/m³, level]`) but is not needed.
  - Sarajevo station ids: Bjelave 3, Vijećnica 17, Otoka 12, Ilidža 13, Ivan Sedlo 1 (also Saraj Polje 34, Vogošća 27, Hadžići 26, Ilijaš 18).
  - Level is 0-based EEA scale: 0 dobar, 1 prihvatljiv, 2 umjeren, 3 loš, 4 vrlo loš, 5 izuzetno loš.
  - The JSON has no timestamp. The hour comes from the fallback page or is assumed to be the last full hour (data appears about 25 minutes after the hour).
- **Fallback (HTML):** `https://www.fhmzbih.gov.ba/latinica/ZRAK/AQI-satne.php` (32 KB, no gzip). Timestamp `div.box-title div.subtitle` ("17.11.2026. u 00 h"). Rows `tr:has(td.IKZ):has(a[href^=ams])`; expand `colspan` to 13 slots `[AQI_img, SO2, SO2_img, NO2, NO2_img, O3, O3_img, PM10, PM10_img, PM25, PM25_img, CO, H2S]`. `#` = not available, empty `td.nema` = not measured. Index letter from `img[src]` `index/X.png` (a no data, b good ... g extremely poor).
- **Poll:** once per hour at about HH:30.
- **Terms:** no robots.txt, no terms page. The portal states the real-time values are not validated official data. Credit "FHMZ BiH".

## Weather warnings: MeteoAlarm (Bosnia and Herzegovina)

- **URL (JSON):** `https://feeds.meteoalarm.org/api/v1/warnings/feeds-bosnia-herzegovina` (about 134 KB decoded). Parse with `android.util.JsonReader` (streaming) to keep memory low.
- **Alternative (Atom):** `https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-bosnia-herzegovina` (44 KB, English only; Bosnian text needs the per-warning link).
- **Structure:** `warnings[].alert.info[]`, one `info` per language (`en`, `bs`). Fields used: `language`, `event` ("Vjetar žuto Upozorenje"), `headline`, `description`, `instruction`, `onset`, `expires` (ISO with offset), `severity`, `parameter[awareness_level]` ("2; yellow; Moderate", "3; orange; Severe", "4; red; Extreme"), `parameter[awareness_type]` ("1; Wind", "10; Rain" ...), `area[].geocode[EMMA_ID]`, `senderName`.
- **Regions:** BA001 Bihać, BA002 Prijedor, BA003 Banja Luka, BA004 Tuzla, BA005 Livno, **BA006 Sarajevo**, BA007 Višegrad, BA008 Mostar, BA009 Foča, BA010 Trebinje. Issued by FHMZ (Federation regions) and RHMZ RS (RS regions).
- **Rules in the app:** use `bs` texts. Drop warnings whose `expires` is in the past (the feed still contains them). Show all levels for BA006; for other regions only orange and red. De-duplicate identical event/onset/expires pairs.
- **Verified:** on 17 Nov a yellow wind warning was active for Sarajevo, 15:00 to 15:00 next day.
- **Terms:** "Licensed under terms equivalent to CC BY 4.0" (feed `rights`). Credit "MeteoAlarm (EUMETNET)" and the issuing service (`senderName`).
- **Fallback:** FHMZ has no warnings feed. Its home page (`https://www.fhmzbih.gov.ba/latinica/index.php`, 100 KB, no gzip) carries a text box with today's MeteoAlarm warnings (`div.MAcontainer + div`, split on `<p></p>`; `strong` = region, date, from, to). It has no level field, only the awareness phrase. Use only if MeteoAlarm fails, at most every 1 to 3 hours.

## Smog episodes (Kanton Sarajevo)

### Current plan: two levels only

The *Plan interventnih mjera u slučajevima prekomjerne zagađenosti zraka u Kantonu Sarajevo* (adopted 11.12.2025, Službene novine KS 51/2025, in force since 26.12.2025) repeals the 2023 plan. It has **two episode levels: Upozorenje and Uzbuna. "Pripravnost" no longer exists.** The app still recognises the word in old or mistaken texts but never shows it as a current level. The "Pregled Plana ..." PDF on mkipgo.ks.gov.ba/mjere describes the old plan and must not be used.

- **Upozorenje** is declared and lifted by the cantonal ministry (Naredba); **Uzbuna** by the cantonal government (Odluka).
- **Zones:** A = Stari Grad, Centar, Novi Grad, Novo Sarajevo, Ilidža (below 700 m); B = Vogošća; C = Ilijaš; D = Hadžići; E = above 700 m and Trnovo (no measures).
- **Thresholds (µg/m³):** PM10 24 h average 90 (Upozorenje) / 200 (Uzbuna); SO2 1 h 400 / 500 and NO2 1 h 250 / 400 (3 consecutive hours); CO 8 h 12,000 / 15,000.

Banner texts (from the plan's measure tables):

| Level | Banner (Bosnian) |
|---|---|
| Upozorenje | Zona A: zabrana vožnje za automobile i teretna vozila EURO 2 i starija. Zabranjeni građevinski radovi na otvorenom. Djeca u školama i vrtićima ne borave napolju. Rizične grupe: ograničite boravak napolju; za rad napolju maska FFP2. |
| Uzbuna | Sve mjere Upozorenja, a u zoni A zabrana vožnje za EURO 3 i starija vozila (taxi izuzet). Škola od 8:30, vrtić do 9:00, fakulteti online. Javni prevoz besplatan ili subvencioniran. Zabranjena javna okupljanja i sport na otvorenom. |

### Source: vlada.ks.gov.ba news list

Declarations and lifts are ordinary news items of the cantonal government's press service. There is no RSS feed (`/rss.xml` and `/aktuelno/novosti-feed` are stale or broken) and no dedicated episode page.

- **List:** `https://vlada.ks.gov.ba/aktuelno/novosti` (82 KB, 20 items, newest first, 2 to 4 items per day). Items `#block-gavias-vitaco-content div.views-row`; date `.views-field-uid .field-content` ("16.11.2026 (user)", date only); link and title `.views-field-title h3 a`; teaser `.views-field-body .field-content`.
- **Article:** `/node/<id>` (58 KB). Title `h1.page-title span`; timestamp `article.node .node__meta time[datetime]` (ISO with offset); body `div.field--name-body`.
- **Detection (title, then body of new candidates only):**
  1. Candidate: contains `epizod` and a level word (`uzbun*`, `upozorenj*`, `pripravnost*`), or a level word plus `zrak`/`zagađ`. Reject titles with `preventivnih mjera`, `spašavanja`, `vjetr`, `padavin`, `snijeg`, `poplav`, `topljenj` (civil-protection weather warnings use "Upozorenje" on the same list).
  2. Check **lift before declare** (lift sentences can also contain "proglašava"): lift = `ukinu*|ukida*|ukidanj*|van snage|prestaj*|prestanak`; declare = `proglas*|proglaš*`.
  3. Levels in order of appearance; "na snazi ostaje ... X" keeps level X for the zones in that clause.
  4. Zones from `zon\w*\s+([A-E](?:\s*,\s*[A-E])*,?(?:\s+i\s+[A-E])?)`; "cijelo područje Kantona" = A to D; "četiri gradske općine i Ilidža" / "pet općina" = A. A lift with no zone means all zones.
  5. Process notices oldest to newest. If no new notice arrives within 7 days of the last declaration, the banner switches to "nepotvrđeno" and then hides.
- **Real examples (2026):** node 127287 "Vlada KS proglasila epizodu "Uzbuna" za Zonu A" (16.01.); 127296 "Ukinute epizode "Uzbuna" i "Upozorenje" u Kantonu Sarajevo" (20.01.); 127303 "... proglašena epizoda „Upozorenje" za veći dio Kantona Sarajevo" (22.01., zones A to D); 127311 "Ministarstvo ukinulo epizodu "Upozorenje" u Kantonu Sarajevo" (25.01.).
- **Poll:** page 0 every 30 min from 15 October to 15 April, every 3 hours otherwise; fetch an article only for a new candidate node id.
- **Fallback:** klix.ba and n1info.ba headlines (already fetched for the news tile) matching `epizod` + level word + Sarajevo, without another city name and without "?". Used only as a trigger to re-check vlada.ks.gov.ba sooner, never as the episode state itself (headlines have many false positives and rarely report lifts).
- **Terms:** robots.txt allows news and `/node/` paths (only `/search/`, `/admin/`, `/user/` are disallowed). Public government information; credit "Vlada Kantona Sarajevo".

## Fuel prices

### Why not cijenegoriva.ba

cijenegoriva.ba has a free JSON API (`/api/v1/gradovi/{grad}`), but its terms of use (`/uvjeti-koristenja#podaci`, updated 5 Oct 2026) reserve both of our uses:

> "ponavljano i sistematsko preuzimanje manjih dijelova, kao što je automatsko prikupljanje (scraping) stranica, API-ja ili datoteka sajta ..."
> "automatski prikaz naših cijena na drugom sajtu, u aplikaciji ili servisu, osim službenim widgetom"

Every API response repeats: "Prikaz na drugom sajtu ili u aplikaciji samo uz partnerski dogovor." The API also gives only min/avg/max and the single cheapest station per fuel, not a top 5. robots.txt disallows `/izvoz-cijena`. The site offers a keyed partner source on request. The app does not use cijenegoriva.ba unless a written agreement exists.

### Official source

Federation stations must report every price change to the Federal Ministry of Trade's **eOPC** register; the ministry's free app "FMT FBiH Oil Info" shows it. eOPC has no public, documented interface (the web portal is login-only for station operators). Republika Srpska is not covered by eOPC.

### Used: uhvatipopust.ba

- **URL:** `https://uhvatipopust.ba/cijene-goriva/sarajevo` (93 KB decoded, gzip, Cloudflare).
- **robots.txt:** `User-agent: * / Disallow:` (everything allowed). Terms (`/uslovi-koristenja`) only forbid unlawful use; the site itself aggregates public data.
- **Structure:** the page embeds the full station list as JSON in Vue component attributes:
  - `<gas-boxes-for-city :prices="[...]">` (HTML-escaped JSON): one object per station with `name`, `company_name`, `address`, `lat`, `lon`, `hours`, `image` (logo URL on the eOPC backend) and `latestPriceForG1` ... `latestPriceForG9` = price in hundredths of a KM (373 = 3,73 KM/l), `null` when the station does not sell that fuel.
  - `<gas-boxes-for-city :types="[...]">`: fuel type ids: G1 Dizel D5, G7 Dizel aditivirani, G3 Super 95, G8 Super 95 aditivirani, G4 Super 98, G9 98 aditivirani, G5 LPG, G6 LUEL.
  - Static HTML table "Danas (Ponedjeljak, 16.11.2026.) najjeftinije gorivo ..." gives the data date and the cheapest station per fuel (used as an internal cross-check).
- **Mapping to the app:** Dizel = G1, Super 95 = G3, Super 98 = G4, LPG = G5 (additive variants ignored by default).
- **Station names:** clean for display: drop "d.o.o.", "d.d.", trailing city ("BINGO PETROL d.o.o. Tuzla" becomes "Bingo Petrol"), title case.
- **Price date:** there is no per-station price date. The page date is the data date (the list is refreshed daily from eOPC; prices stay valid until changed). The app stores the date it first saw each price and drops stations missing from the latest list. If the page date itself is older than 7 days, the tile greys out.
- **Outliers:** a price more than 15% from the city median for that fuel gets a "PROVJERITI" mark and is excluded from the home tile. On 16 Nov LPG at Energopetrol (1,27) was 22% under the median (1,63).
- **Verified 16 Nov 2026 (Sarajevo):** Dizel 20 stations, min 3,58 (Green Oil, Marka Marulića 4), median 3,74, max 3,87; Super 95 18 stations, min 2,79 (Bingo Petrol); Super 98 5 stations, min 3,12 (Selex); LPG 19 stations, min 1,27.
- **Poll:** 3x/day (07:30, 12:30, 18:30). Cross-check: the cheapest station per fuel computed from the JSON must equal the static table.

## News

Three portals, all behind Cloudflare, all accept the plain User-Agent, all UTF-8, none supports conditional GET. Times are RFC 822 with offset (`Mon, 16 Nov 2026 23:25:00 +0100`).

| | klix.ba | n1info.ba | radiosarajevo.ba |
|---|---|---|---|
| Feed | `https://www.klix.ba/rss` (RSS, 30 items, 7 KB gzip) | `https://n1info.ba/feed/` (RSS, 50 items, 13 KB gzip; no encoding declared, pass UTF-8) | `https://radiosarajevo.ba/rss` (Atom despite the name, 25 entries, 4 KB gzip) |
| Lead in feed | Yes: `description` = full first sentence (unescape `&quot;`) | Cut at 150 chars mid-word | No text |
| Article lead selector | `div#excerpt span.lead` | `p[data-testid=article-lead-text]` | first `p` of body |
| Article body selector | `div#text > p` | `div[data-testid^=article-content-wrapper] > div.rich-text-block > p` | `div.text-editor-article > p` |
| Cheap shortcut | none | stop reading after `article-content-wrapper-1` (saves 75% memory) | `og:description` in `<head>` (first ~240 chars) |
| Article size (gzip / decoded) | 50 / 340 KB | 35 / 450 KB | 23 / 160 KB |
| Crime ("crna hronika") marker | link path `/vijesti/crna-hronika/` | any `<category>` "Crna Hronika", or path `/crna-hronika/` | link path `/vijesti/crna-hronika/` |
| Skip | | `/video/` items | `/radio/`, `/multimedia/` |

- **Reading articles:** fetch only articles that are new and will be shown; cache the extracted sentences by link and never refetch. Read the stream only until the per-site stop marker plus a few KB (hard cap about 300 KB), then parse that part with jsoup, one article at a time.
- **Sentence splitting:** split at `[.!?…]["”“]?\s+(?=["„“]?[A-ZČĆŽŠĐ])`, but not after numbers (`69.`), initials (`M. O.`, `U.S.`) or abbreviations (d.o.o, d.d, tzv, npr, dr, mr, prof, sl, ul, br, str, god, mil, mlrd, tj, itd, sv, st). Show at most 3 sentences or about 350 characters.
- **Fallback:** if a page yields under 40 characters (video page, template change), show what the feed has (klix lead, n1 description cut to its last full sentence, or the headline) and log it.
- **Categories:** derived from the link path (and the feed category) into a small set: BiH/Sarajevo, Regija/Svijet, Ekonomija, Sport, Kultura/Magazin, Tehnologija/Auto, Crna hronika.
- **Budget:** 3 feed requests per refresh plus 3 to 10 article requests per hour; worst case about 18 requests and 0.6 MB per hour.
- **Terms:** robots.txt allows the feeds and article paths on all three (klix disallows `/api/*`, n1 disallows `/api/v1/*`; the app never calls those, nor n1's `/wp-json/`, which is blocked by Cloudflare). All three assert copyright (klix: "Zabranjeno preuzimanje sadržaja bez dozvole izdavača"). The app shows headline, a short lead and the source on one private screen, and never republishes.

## Sport

### API-Football free plan: not usable

API-Football's own tutorial states: "This exact season is not available on the Free Plan in 2026. Free access covers seasons from Year-4 through Year-2, which currently means 2022, 2023 and 2024." Developer reports from October 2026 confirm it and add that the free plan serves fixtures only from yesterday to tomorrow. Limits: 100 requests/day, 10/minute. Ids for reference if a paid plan is ever used: league 315 (WWin liga BiH), BiH national team 1113, FK Sarajevo 679, FK Željezničar 654 (last two unverified).

### Used: nfsbih.ba (Nogometni/Fudbalski savez BiH)

Server-rendered Joomla HTML, 37 to 70 KB per page. robots.txt allows these pages. No terms page; low-volume use with caching.

- **League table:** `https://www.nfsbih.ba/en/competitions/football-men/wwin-league-bh/` (no season id in the URL). Rows `#akeeba-renderjoomla table.table-striped tr` with exactly 10 `td`: position, crest, club (`td[2] > a`; trailing "(-1)" = points deduction; `href` = club page), P, W, D, L, "GF : GA", GD, Pts. Title `#akeeba-renderjoomla h4` ("WWIN liga BiH 26/27").
- **Club schedule:** take the club page link from its table row (the season id `54152223` changes each year, never hard-code it), for example `/en/stats/team/54152223/409-fk-sarajevo/`. Rows with 7 `td`: home, away (`td[3]`), "dd.MM.yyyy HH:mm" (`td[4]`), "(HT) FT" (`td[5]`). Future matches also show "(0:0) 0:0", so played/unplayed is decided by kickoff time; rows are in round order, so sort by date. nfsbih team ids: Sarajevo 409, Željezničar 411, Zrinjski 517, Borac 424, Široki Brijeg 831, BSK 436, Velež 607, Čelik 462, Radnik 530, Sloga Doboj 593.
- **Round results and next round:** sidebar module on the league page: `div.tabelarni div[id$=-rezultati] tr` and `div[id$=-naredno] tr`; a single `td[colspan=6] strong` row is a date row for the following matches.
- **National team next match:** `https://www.nfsbih.ba/en/teams/men/a-national-team/`, box `div.content-counter`: kickoff `div.counter[data-live-score-start]` (epoch ms), teams `ul.club-logo h6`, competition `div.title-color h4`. Live score JSON from `data-live-score-url`, for example `/index.php?option=com_repka&task=livescore&format=json&id=296` (98 B: `homeScore`, `awayScore`, `status`). The box can be absent.
- **National team results:** `.../results-of-a-national-team/a-national-team-results-for-2026/`, rows `div.itemFullText table.table-striped tr` with 4 `td` (date, "Home - Away", "3 - 2" or "-", competition). Hand-edited page whose URL contains the year; fall back to the previous year in January.
- **Poll:** table 2x/day plus once about 2.5 h after league kickoffs; club 2 to 3x/day, every 15 to 20 min during its match; national team 2x/day, live JSON every 3 to 5 min during a match. About 8 to 12 requests/day normally.
- **Fallback:** TheSportsDB free key `123` (league 4624): only the top 5 table rows and one home-only next/last match, with wrong times seen in 2 of 4 events. Marked as partial.
- **Verified 17 Nov 2026:** table after round 13 (1. HŠK Zrinjski 34, 2. FK Borac 31, 3. FK Sarajevo 20 ...); next BiH match BiH vs Romania 17.11. 20:45, Zenica.

## Exchange rates: CBBH

- **URL:** `https://www.cbbh.ba/CurrencyExchange/GetJson?date=yyyy-MM-dd` (734 B gzip). XML at `GetXml` (root tag is misspelled `CurrencyExchaneData`; do not validate it).
- **Structure:** `{"CurrencyExchangeItems":[{"AlphaCode":"USD","Units":"1","Buy":"1.771328","Middle":"1.775767","Sell":"1.780206", ...}], "Date":"2026-11-17T00:00:00", "Number":226}`. All numbers are strings; divide by `Units` (100 for HUF, JPY, RSD). XDR has empty Buy/Sell. Key on `AlphaCode`, not `Country` (typos).
- **Semantics:** the server returns the newest list dated on or before the requested date, never an error, so always read `Date`. Lists are published after 16:00 on working days and dated the next day (Friday's list is dated Saturday).
- **Poll:** from 16:05 on working days request tomorrow's date; if `Date` is not newer than the cached list, retry every 30 min until 19:00.
- **Verified:** list No. 226 dated 17 Nov 2026: USD 1.775767, CHF 2.115783, GBP 2.343715 (middle). EUR fixed at 1.95583.
- **Terms:** use allowed with "Centralna banka BiH" named as source and a link to www.cbbh.ba; no commercial use.

## Holidays (Federation of BiH)

No network source: the app ships a table (2026 to 2030) and receives corrections through app updates.

### Legal basis

There is no FBiH or state law on holidays. The Federal Ministry of Labour and Social Policy (fmrsp.gov.ba) applies:

- **Zakon o praznicima (Sl. list SFRJ 6/73):** Nova godina 1 and 2 January, Praznik rada 1 and 2 May. If one of the two days is a Sunday, the next working day is also off (precedents 3.1.2022, 3.1.2023).
- **1 March, Dan nezavisnosti** and **25 November, Dan državnosti** (Sl. list RBiH 9/95): non-working, never moved by law. The FBiH government can decide a one-off extra day, as it did for 2.3.2026.
- **Religious holidays are not general non-working days in FBiH.** Zakon o radu FBiH, čl. 54 st. 3: workers may take up to 4 days a year for religious or traditional needs, 2 of them paid. Shops and offices are generally open.

So the Kalendar shows two kinds of day: **neradni dan** (state holidays, everyone) and **vjerski praznik** (labelled "neradni za vjernike").

### Table

| Year | State non-working days | Religious holidays |
|---|---|---|
| 2026 | 1-2.1., 1.3. + 2.3. (government decision), 1-2.5., 25.11. | Božić (prav.) 7.1., Ramazanski bajram 20-21.3., Uskrs 5-6.4., Vaskrs 12-13.4., Kurban-bajram 27-28.5., Svi sveti 1.11., Božić 25.12. |
| 2027 | 1-2.1., 1.3., 1-2.5. + 3.5. (Sunday rule), 25.11. | 7.1., Ramazanski bajram 9-10.3.*, Uskrs 28-29.3., Vaskrs 2-3.5., Kurban-bajram 16-17.5.*, 1.11., 25.12. |
| 2028 | 1-2.1. + 3.1. (Sunday rule), 1.3., 1-2.5., 25.11. (Saturday, not moved) | 7.1., Ramazanski bajram 26-27.2.* (borderline, possibly 27.2.), Uskrs and Vaskrs 16-17.4., Kurban-bajram 5-6.5.*, 1.11., 25.12. |
| 2029 | 1-2.1., 1.3., 1-2.5., 25.11. (Sunday, a one-off decision is possible) | 7.1., Ramazanski bajram 14-15.2.* (borderline, possibly 15.2.), Uskrs 1-2.4., Vaskrs 8-9.4., Kurban-bajram 24-25.4.*, 1.11., 25.12. |
| 2030 | 1-2.1., 1.3., 1-2.5., 25.11. | 7.1., Ramazanski bajram 4-5.2.* (borderline, possibly 3.2.), Kurban-bajram 13-14.4.*, Uskrs 21-22.4., Vaskrs 28-29.4., 1.11., 25.12. |

\* Provisional. The Islamic Community (Rijaset IZ BiH) has published only 2026 ("Značajni datumi u kalendaru Islamske zajednice u 2026. godini", islamskazajednica.ba, 31.12.2025). Later years are computed with the criterion IZ BiH follows (Diyanet / Istanbul 2016); the method reproduces all eight published dates for 2023 to 2026, and 2027 matches the KS school calendar. Dates can shift by one day: refresh from the IZ BiH list each December and ship the correction in an app update.

Easter: Catholic (Gregorian) and Orthodox (Julian, +13 days) computed with the Meeus algorithms; the app also computes them at runtime as a cross-check of the table.

Each row is stored with a status (`official`, `computed`, `provisional`) so provisional dates can be marked "okvirno" on screen.

## Water works: ViK Sarajevo

- **URL:** `https://www.viksa.ba/objave/servisne_informacije` (9 KB gzip, 10 notices per page, newest first; `?str=2` for older). Always use `https://www.viksa.ba` (the bare domain redirects). No RSS or JSON.
- **Selectors:** item `div.service-item-top` (the markup nests `<a>` inside `<a>`, so select the div, not `a > div`); date `p.fs-6` ("16.11.2026 u 15:40"); title `h5.news-card-title`; full body `p.fw-light` (the list contains the complete text with all streets, no detail requests needed); link `a[href^=/clanak/]`.
- **Rhythm:** about 3 notices per working day, none at weekends: water works today (about 08:30), sewer works today (about 09:00), planned water works for tomorrow (about 15:00); on Friday one notice covers Saturday to Monday, split per day in the body.
- **Target date:** from the title with `(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})`, plus the day list for multi-day notices.
- **Matching streets:** normalise both sides (lowercase; č/ć to c, š to s, ž to z, đ to dj; hyphens, dashes and dots to spaces; drop "ul."/"ulica"; collapse spaces). Match whole words, allowing the last 1 to 2 letters of words of 5+ letters to differ ("Orahov brijeg" matches "Orahovog brijega", "Rakovica" matches "u Rakovici"). Each configured place can have several spellings ("Mali Kačanik" / "Kačanik mali").
- **Display:** only notices for today or tomorrow that match a configured street or neighbourhood. Strip the call-centre boilerplate from "Normalizaciju u vodosnabdijevanju" / "Za sve informacije" onward.
- **Poll:** every 30 to 60 min on working days 07:00 to 17:00, every 2 to 3 hours otherwise. Change detection: first `/clanak/<id>`.
- **Terms:** robots.txt sets no restrictions; no terms page. Credit "ViK Sarajevo".

## Power, heating and gas notices

Shown together with the water notices: in Korisno ("Voda · struja · grijanje · plin") and, for today and tomorrow, in the banner row of Početna (a weather or smog warning takes the row first). All three use the ViK street matcher and the same street list, and run only when streets are set.

### Power cuts: Elektroprivreda BiH

- **URL:** `https://www.epbih.ba/stranica/servisne-informacije`: one server-rendered page (about 270 KB, no compression, no ETag) with every planned cut in the Federation for the next 2 to 3 days. The area and municipality dropdowns only hide rows in the browser; there is no feed or JSON behind them.
- **Rows:** `table.iskljucenja_tabela tr.item[data-ed=edsa]` (Elektrodistribucija Sarajevo), cells: municipality, `td.ulica` (one street or settlement per row), house numbers, date `dd.MM.yyyy`, time `HH:mm-HH:mm`. Street spellings vary ("Armije Rbih", "Vroce"), so the case- and diacritic-folding matcher matters.
- **Display:** "Bez struje od 08:30 do 15:00" with the street and house numbers. A cut for today drops out once its end time has passed. Unplanned cuts are not published anywhere.
- **Poll:** every 3 hours (about 2 MB a day); only the Sarajevo rows are cached (about 27 KB).
- **Terms:** robots.txt has no rules. The terms allow loading and printing information "za potrebe gledanja i čitanja u nekomercijalne svrhe" and forbid republishing; they also say information may not be obtained "na način koji nije omogućen ili ponuđen preko stranice". The app reads the public page for private, non-commercial display in one household and republishes nothing; the owner accepted that reading. Credit "Elektroprivreda BiH".
- **TLS:** Sectigo OV certificate (USERTrust root), not behind Cloudflare; works on the tablet.

### Heating: Toplane-Sarajevo

- **Today:** `https://www.toplanesarajevo.ba/korisnici/obavijest-o-radovima` (9 KB): heading `div.blank-header h3` ("... na dan 18.11.2026."), table rows with 8 cells: boiler house, address (free text), responsibility, contractor, start, planned end ("-" when unknown), flats, reason. Covers planned work and faults.
- **Announcements:** the homepage (`div.news`, title and description) links to `/novost/{id}/{slug}`; only items whose text mentions "prekid", "isporuk" or "obustav" are opened (at most three). Article: `article.news time[datetime]`, `h3` title (all caps on the site, shown in sentence case), body `div.col-lg-9 p` with addresses one per line. Shown for 5 days after posting, as "Najava".
- **Poll:** every 45 minutes from 06 to 21 h in the heating season (October to April), every 3 hours at night, every 6 hours outside the season.
- **Terms:** robots.txt has no rules; no terms page ("Sva prava zadržana"). Credit "Toplane-Sarajevo".

### Gas works: Sarajevogas

- **URL:** `https://www.sarajevogas.ba/feed/` (WordPress RSS, 10 items, full text in `content:encoded`). Works notices name the street in the title or text ("Sanacija curenja gasa u Dobrinjskoj ulici"); they are rare.
- **Display:** posts from the last 5 days that mention a configured street, as "Najava".
- **Poll:** every 3 hours. **Terms:** robots.txt blocks only `/wp-admin/`; no terms page. Credit "Sarajevogas".

## Roads and borders: BIHAMK

- **RSS:** `https://bihamk.ba/spi/stanje-na-cesti-u-bih/rss` (4 KB gzip): one `item` whose `description` CDATA holds HTML: `div.category` (`h2`: Granični prijelazi, Autoceste, Magistralne ceste, Regionalne ceste, Gradske ceste) with `div.row` entries (`h3` title, `p` text). Parse the CDATA with `Jsoup.parseBodyFragment`. The feed dates are the request time; ignore them.
- **HTML:** `https://bihamk.ba/spi/stanje-na-cesti-u-bih` (25 KB gzip): real update time `time[datetime]` ("2026-11-16 19:48:14", local), general report `div.text-editor p` (messy, de-duplicate non-empty paragraphs).
- **Border waits:** text only, never minutes. All 47 crossings said "Zadržavanja putničkih vozila nisu duža od 30 minuta." Show a border line only when a crossing's text differs from that default.
- **Roads:** show "Gradske ceste" entries that mention configured streets (same matcher as ViK) and Sarajevo-area motorway/main-road entries.
- **Poll:** RSS every 30 to 60 min from 06:00 to 21:00 (the information centre works 06 to 20 h), HTML hourly. Change detection: hash of the description.
- **Terms:** robots.txt allows everything; no website terms. Credit "BIHAMK".

## Winter equipment (static)

Winter equipment is mandatory in BiH from 1 November to 1 April. The app shows a reminder in Korisno during that period (and a one-time note on Početna in the last days of October). BIHAMK's report also states it ("Na snazi je zakonska obaveza posjedovanja zimske opreme").

## Updates: GitHub Releases

- **URL:** `https://api.github.com/repos/Waiivyy/InfoTablet/releases/latest` with `Accept: application/vnd.github+json` (unauthenticated limit 60 requests/hour; the app uses a handful per month). 404 means no release yet. Drafts and pre-releases are ignored by that endpoint.
- **When:** once a month; at once when an enabled source has been failing for 2 days, then at most daily while it keeps failing; and on "Provjeri ažuriranje". A check that does not finish is retried after an hour.
- **Release format:** tag `vX.Y.Z`, one `.apk` asset from this repository, under 25 MB. The version code is X·10000 + Y·100 + Z.
- **Download:** the asset's `browser_download_url`, which redirects to `release-assets.githubusercontent.com`. Redirects are followed by hand and only HTTPS to `github.com` or `*.githubusercontent.com` is allowed. The file must match the asset's size and its `digest` (SHA-256) from the API.
- **Before it is offered:** same package name, higher version code, and the same signing certificate as the installed app (Android checks the signature again when installing). A refused version is not downloaded again by the automatic checks.
- **Install:** "Nova verzija" with an Instaliraj button in Postavke, Ažuriranja and in the banner row of Početna (a weather or smog warning takes the row first). The system installer asks for confirmation; after the update the app opens itself again. Needs "Nepoznati izvori" (Unknown sources) to be on.

---

## TLS and certificates on Android 4.2

- The tablet's system trust store dates from 2013 (140 roots, pulled from `/system/etc/security/cacerts`). It does not contain ISRG Root X1 (Let's Encrypt) or other roots added after 2013.
- Conscrypt modernises the TLS protocol and ciphers but uses the system trust store, so it does not fix missing roots. The app therefore ships a small bundle of current root certificates and trusts system roots plus the bundle.
- The development Mac's traffic goes through a TLS-inspecting gateway, so real certificate chains cannot be inspected from it. The definitive check is the on-device HTTPS test against every host below (Phase 3).

Hosts the app contacts: `api.open-meteo.com`, `air-quality-api.open-meteo.com`, `zrak.fhmzbih.gov.ba`, `www.fhmzbih.gov.ba`, `feeds.meteoalarm.org`, `uhvatipopust.ba`, `www.klix.ba`, `n1info.ba`, `radiosarajevo.ba`, `www.nfsbih.ba`, `www.thesportsdb.com` (fallback), `www.cbbh.ba`, `www.viksa.ba`, `bihamk.ba`, `www.epbih.ba`, `www.toplanesarajevo.ba`, `www.sarajevogas.ba`, `api.github.com`, `github.com`, `release-assets.githubusercontent.com`, `vlada.ks.gov.ba`.

## Daily request estimate

About 450 to 550 requests/day in total, spread over 18 hosts; no host gets more than about 100/day (Open-Meteo, with the optional second city).
