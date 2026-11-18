# Mukul plus — সম্পূর্ণ অ্যাপ্লিকেশন কোড অডিট রিপোর্ট

**রিপোর্ট তারিখ:** ২০২৬-১১-১৮
**ব্রাঞ্চ:** `arena/b4635bd1-mukul-plusbd` (base: `8ccf642`)
**রিলিজ যাচাই:** `Cricfyv2` ট্যাগ → কমিট `8ccf64273e815097275db7d746565163ccabc6a6` = এই রিপোজিটরির HEAD
**কোডবেস:** ৬০টি Kotlin ফাইল, ২৯,৬১৯ লাইন, ১৪টি স্ক্রিন

---

## ১. APK ফাইল যাচাই (সরাসরি বাইনারি স্ক্যান সম্ভব হয়নি)

| পরীক্ষা | ফলাফল |
|---|---|
| সরাসরি APK ডাউনলোড (aria2/curl/gh) | ❌ ব্যর্থ — এই স্যান্ডবক্স থেকে `release-assets.githubusercontent.com` ব্লকড |
| APK ↔ সোর্স ম্যাচিং | ✅ `app-update.json`-এ `commit_sha = 8ccf642…` = HEAD; `Cricfyv2/Sports/Vricfy/Mcp/Boxmovie/Areana` সব ট্যাগ একই কমিটে |
| SHA-256 নির্ভুলতা | ⚠️ `sha256_checksums.txt` কপি করা ফাইলের নাম দিয়ে হ্যাশ করে — তাই **প্রতিটি রিলিজে হ্যাশ আলাদা** (`Cricfyv2: 62b837b3…`, `Sports: ededb2dd…`) যদিও APK **তিনটিতেই হুবহু একই সাইজ (30,392,024 বাইট)**। অর্থাৎ চেকসাম ফাইলটি কার্যত অকেজো |
| APK সাইজ | ৩০.৪ MB (মিনিফাই বন্ধ → কোড সহজে decompile করা যায়) |
| রিলিজ বিল্ড টাইপ | `assembleDebug` — অর্থাৎ **স্টোর-স্বাক্ষরিত release বিল্ড নয়, ডিবাগ APK** |
| তার সাথে বান্ডল করা তৃতীয় পক্ষের APK | `CRICFy_v7.4.3.apk` (২১ MB), `Cricfy_V7.4.1.apk`, `moviebox-v4.0.04` (৮৪ MB), `SportzX_new_3.2v.1.apk` — এর কোনোটিই এই কোডবেসে নেই, এবং অ্যাপের `AppStoreRepository`-ও সেগুলোর দিকে ইঙ্গিত করে না ⇒ **রিলিজের সাথে অপ্রাসঙ্গিক/রেড ফ্ল্যাগ** |

> ⚠️ সাইজ দিয়ে প্রমাণ হয় এই সোর্স ওই APK-এর বেস, কিন্তু বাইনারি-লেভেল নিশ্চিতকরণ (manifest merge, dex hash, signature) করা যায়নি। নিচের সব ফলাফল = **কোড-লেভেল প্রমাণ**, runtime-এ যা ঘটবে তা সোর্স থেকেই অনুমেয়।

---

## ২. 🔴 সবচেয়ে গুরুত্বপূর্ণ: আপডেট সিস্টেম আজ ভাঙা

`AppUpdateManager.kt` + GitHub API থেকে সরাসরি যাচাই করা তথ্য:

```
GitHub /releases/latest  →  tag = "Cricfyv2", প্রকাশিত 2026-11-15, APK = MukulPlus-Cricfyv2.apk
app-update.json          →  version_name = "Cricfyv2", version_code = 43
```

অ্যাপের `parseVersionFromTag("Cricfyv2")` = `("Cricfy2", 1)`, পরে `"Cricfy2".toIntOrNull()` ফেলে দেয় ⇒ **remote_version_code = 0**।

`isVersionMatched()`-এর লজিক simulat করে পেলাম:

| GitHub-এ নতুন রিলিজ | আপডেট ডায়ালগ আসবে? |
|---|---|
| `v1.0.35` | ✅ হ্যাঁ |
| `v2.0.0` | ✅ হ্যাঁ |
| **`Cricfyv2` (এখন যেটা live)** | ❌ **না — ভাঙা** |
| `Sports` / `Vricfy` / `MukulAndroid` / `Mcp` | ❌ না |
| `v1.1` (দুই-অংশের ভার্সন) | ❌ না (`code=1` থেকে যায়) |

**পরিণতি:** ৩৪ কমিট/তারিখ পরে ৩০+ রিলিজ হয়েছে, কিন্তু সব ৩০.৪ MB-র একই APK; কেউ অটো-আপডেট পাবে না। "আপডেট চেক করুন" চাপলে "আপনার অ্যাপ্লিকেশন কারেন্ট হয়েছে" দেখাবে।

**গৌণ সমস্যা একই ফাইলে:**
- `fileSizeBytes` hardcode fallback `30293657L` / `30293657` — আসল APK ৩০,৩৯২,০২৪ ⇒ প্রোগ্রেস বার ~৯৯% এ আটকে থাকবে।
- `downloadApk()`-এ **SSL/TLS বা SHA-256 যাচাই নেই**; ডাউনলোড শেষে সরাসরি `installApk()` (`ACTION_VIEW` + `REQUEST_INSTALL_PACKAGES` + `usesCleartextTraffic=true`)। GitHub অ্যাকাউন্ট/রিলিজ compromise হলে = silent RCE।
- `DIRECT_LATEST_APK_URL` / `RELEASE_METADATA_JSON_URL` = `mdmukul120/Mukul-plusbd`, কিন্তু `ApiClient`/`AuthRepository`-এ পুরোনো `https://github.com/mdmukulahmed01/MukulPlus/...` (৬ জায়গায়) — দুটি ভিন্ন অ্যাকাউন্টের মধ্যে কনফিউশন।

---

## ৩. 🔴 নিরাপত্তা ও গোপনীয়তা

### ৩.১ কোডে হার্ডকোড করা ক্রেডেনশিয়াল (সব APK-তে plaintext)
| ফাইল | কী |
|---|---|
| `data/api/ApiClient.kt:16` | TMDB API key `0b3d17a4fe3dd52593a48d9a0dad4bd6` |
| `data/api/YouTubeApiService.kt:58` | Google API key `AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8` |
| `data/repository/AuthRepository.kt:35` | Firebase apiKey `AIzaSyD-mukulPlusApiKeyForFirebaseAuth2026` (licit format নয়) |
| `AuthRepository.kt:31-32, 187` | Firebase appId `1:210932767601:android:9d45e2a7b8c310f52b6d19`, project `mukul-plus-ott`, OAuth client `210932767601-mukulplusotthub.apps.googleusercontent.com` |

`minifyEnabled = false` ⇒ key গুলো strings/bytecode-এ সোজা পড়া যায়। `.env.example`-এ `GEMINI_API_KEY` প্লেসহোল্ডার আছে, কিন্তু আসল key কোথাও নেই — অর্থাৎ secrets plugin আসলে ব্যবহার হচ্ছে না, সব key কোডে।

### ৩.২ 🔴 যেকোনো ইমেইল + যেকোনো পাসওয়ার্ড = লগইন সফল
`AuthRepository.loginWithEmail()`: Firebase ব্যর্থ হলে (বা `firebaseAuth == null`) — যেকোনো credentials-এ:
```kotlin
val profile = UserProfile(uid = "usr_" + email.hashCode().toString(), …)
saveLocalSession(profile); Result.success(profile)
```
**পাসওয়ার্ড কোথাও যাচাই হয় না।** `registerWithEmail()`-ও তাই। `sendPasswordResetEmail()` এমনকি Firebase ছাড়াই "sent successfully" রিটার্ন করে।
⇒ যেহেতু `canRequestPackageInstalls`/ডাউনলোড/প্রিমিয়াম কোনো গেট নেই (সার্চ করে ০টি access-gate পাওয়া গেছে), আজ এটা exploit-যোগ্য নয়, কিন্তু **নকল অথেনটিকেশন** — ইউজার ভাববে "একাউন্ট আছে", আসলে নেই।

### ৩.৩ 🔴 Google সাইন-ইন সবসময় মালিকের একাউন্টে পড়ে
```kotlin
// AuthRepository.kt:232, 239
catch (e: Exception) { loginWithGoogle("mdmukulahmed01@gmail.com", "Mukul Ahmed (Google)") }
```
Google Credential Manager ব্যর্থ হলেই (বা credential অপ্রত্যাশিত টাইপ হলে) ডিফল্ট = **ডেভেলপারের নিজের Gmail**।
- প্রোফাইলে মালিকের নাম/ইমেইল দেখাবে
- Google Play "Data safety" লঙ্ঘন (real user identity ছাড়াই অ্যাকাউন্ট ধরে নেওয়া)

### ৩.৪ 🔴 CS3 প্লাগইন "ইনস্টলার" — ব্যাকএন্ডহীন ধোঁকা, কিন্তু RCE-র পথ তৈরি
`data/extension/ExtensionManager.kt` (৪৯০ লাইন) `Download/` এবং `invoke` করার জন্য তৈরি:
- যেকোনো URL থেকে `.cs3` (zip) নামায়, ভেতরের `manifest.json` + **`classes.dex`** ডিস্কে লেখে — **কোনো signature/hash/issuer যাচাই নেই**
- `ai.studio` implementation_plan-এ পুরো "DexClassLoader + ProviderEngine (getMainPage/search/load/loadLinks)" ডিজাইন লেখা আছে, কোডে সেটি **নেই**
- `ExtensionManager`-কে **কোনো UI ফাইল reference-ই করে না** ⇒ এক্সটেনশন স্ক্রিন/প্লাগইন কার্ড আজ অদৃশ্য
⇒ ব্যবহারকারী "প্লাগইন ইনস্টল" করতে পারেন না, কিন্তু কোডটি যেকোনো দিন UI-তে জোড়া লাগলে = arbitrary DEX execution — এখনকার অবস্থায় ডেড কোড, ভবিষ্যতে বোমা।

### ৩.৫ অন্যান্য নেটওয়ার্ক/গোপনীয়তা
- `android:usesCleartextTraffic="true"` পুরো অ্যাপে; `VideoPlayerView`-এ `setAllowCrossProtocolRedirects(true)`; `YouTubeScreen`-এ `MIXED_CONTENT_ALWAYS_ALLOW` + `allowFileAccess = true` — MITM/স্পুফিং ঝুঁকি।
- `ImageLoader`/Coil দিয়ে থার্ড-পার্টি পোস্টার (unsplash, tmdb, saavncdn, gstatic…) লোড হয় — কোনো প্রাইভেসি পলিসি/consent নেই।
- `file_paths.xml`-এ `external-path path="."` — পুরো বাহ্যিক স্টোরেজ FileProvider-এ এক্সপোজড (প্রয়োজনীয়তার চেয়ে অনেক বেশি)।
- তিনটি বেস URL (`mukul-ott.ai.studio`, `mukul-sports.ai.studio`) = **AI Studio-র অস্থায়ী হোস্টিং**। এই ইনফ্রা কাজ করা বন্ধ করলেই মুভি/IV/স্পোর্টস/স্ট্রিম-প্রক্সি সব বন্ধ।

---

## ৪. 🟠 ফাংশনাল বাগ (কোড থেকে প্রমাণিত)

1. **🔴 মুভি ডিটেইল পেজ ইউজার কখনো দেখতে পাবে না**
   `MainActivity.kt:102` এ `selectedMovieId` state আছে এবং Home/Movies/Profile থেকে সেট হয় (লাইন 498, 526, 564), কিন্তু **কোথাও পড়া হয় না, `MovieDetailScreen` (১,৪৭০ লাইন) কোথাও কল হয় না** ⇒ মুভি/হোম কার্ডে ট্যাপ করলে কিছুই ঘটে না।
2. **🔴 "স্পোর্টস" পুরো ফিচার অদৃশ্য** — `SportsRepository` (৪৪২ লাইন) + `SportsModels` কোথাও ব্যবহৃত হয় না; কোনো ScreenTab-এ Sports নেই (`HOME, MOVIES, LIVE_TV, MUSIC, YOUTUBE, WEATHER, EXTRACTOR, PROFILE`)।
3. **🔴 রিলস ফিচার অচল** — `ReelsScreen` (১,২২৩ লাইন, WebView-ভিত্তিক) কোথাও কল হয় না।
4. **🔴 Mukul OTT আলাদা স্ক্রিন ডেড** — `MukulOttScreen` (২,১৬৫ লাইন) dead; এর bespoke detail/player UI বাদ দিয়ে `MoviesScreen`-এ একই কাজ ডুপ্লিকেট করা হয়েছে।
5. **🔴 মোট ~৯,৯০০ লাইন (৩৩%) রিচেবল নয়** — ExtensionManager, AppStoreRepository (৪৪৩), LocalMediaManager (৩১৭), BanglaMovieApiClient+Scraper (৫০৫), MovieUpdateNotificationManager, AuthScreen (১,১৮২), BanglaOttScreen (১,০৯৫), FilterBottomSheet, MukulSplashScreen (splash কোনোদিন দেখাবে না), SportsRepository, PluginModels/BanglaMovieModels।
6. **🔴 মিউজিক ব্যাকগ্রাউন্ডে চলে না** — `MusicPlayerManager` = bare `ExoPlayer`; `MediaSessionService`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, audio-focus, lock-screen/wear notification কিছুই নেই; আবার manifest-এ `FOREGROUND_SERVICE` permission আছে অথচ **কোনো Service ক্লাসই নেই**। স্ক্রিন বন্ধ → গান থামে/সিস্টেম kill করে।
7. **🟠 ফাইল ডাউনলোড বিরতিহীন** — `InAppDownloader.startDownload()` শুধু; কোনো `Range` request / `resume` নেই (grep-এ `bytes=` শুধু YouTube extract-এ)। ৩০ MB+ মুভি মাঝপথে বন্ধ হলে শূন্য থেকে শুরু।
8. **🟠 ভাষা নির্বাচন কাজ করে না** — `LanguageManager.get()` পুরো কোডে **০ বার** ব্যবহার হয়েছে; সব UI টেক্সট হার্ডকোড বাংলা। ১০টি ভাষার ড্রপডাউন দেখায়, কিন্তু ৪টি লাইনে শুধু `currentLanguage` পড়ে ⇒ langue বদলালে কিছুই বদলায় না।
9. **🟠 ডায়ালগের ভেতরে টাচ/ফোকাস সম্ভাব্য ভাঙা** — `MainActivity.kt:473`-এ `ModalNavigationDrawer`-ই app root; auto-update ডায়ালগ, মিউজিক ফুল-প্লেয়ার, language ডায়ালগ সব ওই drawer-এর **ভেতরে** compose হয় ⇒ Dialog-এর modal layer-এ ভেতরের `ModalNavigationDrawer`-এর scrim/gesture конфликт করতে পারে (ভবিষ্যৎ ইনপুট ব্লকিং)।
10. **🟠 নোটিফিকেশন ব্যবহারকারী কখনো দেখবে না** — `targetSdk = 36` ⇒ `POST_NOTIFICATIONS` runtime permission লাগে, কিন্তু কোডে `POST_NOTIFICATIONS` **শূন্য বার** request হয় (`areNotificationsEnabled` check-ও নেই)। ডাউনলোড/নতুন মুভি নোটিফিকেশন চুপচাপ বাদ যাবে।
11. **🟠 per-app language asset** — `android:localeConfig` নেই, `supportsRtl=true` ঠিক আছে।
12. **🟡 হারের inability** — `contentDescription = null` ১৭২টি বনাম নামযুক্ত ১২৪টি; `imePadding()` ০টি ⇒ কী-বোর্ড ওপরে উঠলে সার্চ/লগইন ফিল্ড ঢাকা পড়ে।
13. **🟡 ভাষা অনুযায়ী সংখ্যা/তারিখ নেই** — সব `SimpleDateFormat(...Locale.US)`।
14. **🟡 API ৩৬-এ `enableEdgeToEdge()`** আছে কিন্তু `Theme.MyApplication` = প্লেইন `DeviceDefault.NoActionBar` → স্ট্যাটাস/নেভ বার কন্ট্রাস্ট সমস্যা।

---

## ৫. 🟠 আর্কিটেকচার ও কোয়ালিটি

| বিষয় | প্রমাণ |
|---|---|
| **God Activity** | `MainActivity.kt` = ৬৭৮ লাইন: nav, drawer, ৭টি ডায়ালগ, ৮টি ট্যাব রাউটিং, আপডেট স্ক্যান — সব একসাথে। `ViewModel`/`NavHost` একটিও নেই (navigation-compose dependency আছে, ব্যবহার নেই) |
| **God screen** | `MukulOttScreen` ২,১৬৫ / `YouTubeScreen` ১,৬৫০ / `MovieDetailScreen` ১,৪৭০ / `WeatherScreen` ১,৪৪১ / `MoviesScreen` ১,৪২৬ লাইন একই ফাইলে |
| **বিরাট ডুপ্লিকেশন** | `ApiClient`-এ ৫০+ hardcoded fallback মুভি অবজেক্ট (TMDB URL সহ), `SportsRepository`-এ ৮টি হুবহু একই unsplash image, `AppStoreRepository`-তে ৫টি নিজের অ্যাপের হার্ডকোড মক ডেটা |
| **৩০+ catch ব্লক নীরব** | `catch (_: Exception) {}` = ৬৮টি; `catch (e: Exception)` = ১০২টি (বেশিরভাগ শুধু log) → বাগ রিপোর্টে কিছুই পাওয়া যাবে না |
| **একই ডেটা টোকা-টোকা duplication** | `selectedMovieId` vs `activeMukulSlug` vs `activeBongoMovie` — তিনটি আলাদা "কোন মুভি" state |
| **টেস্ট কভারেজ ≈ ০** | ৩টি ডেমো টেস্ট (`2+2==4`, string check, logo screenshot) — ২৯,৬১৯ লাইনের বিপরীতে কোনো রিয়েল টেস্ট নেই |
| **বিল্ড** | `assembleDebug` publish (`versionCode 34`), `minifyEnabled=false`, `isCrunchPngs=false`, `AAB` নেই, signing config `storePassword = System.getenv(...)` ⇒ CI-তে keystore না থাকলে APK unsigned/debug |
| **CI** | `.github/workflows/android-build.yml` `--stacktrace` build; প্রতিটি পুশে auto-release ট্যাগ `v1.0.<run_number>`; `continue-on-error: true` থাকায় **বিল্ড ফেল করেও রিলিজ হয়ে যেতে পারে** |
| **ট্যাগ অরাজকতা** | `Cricfyv2, Sports, Mcp, Boxmovie, Areana, MukulAndroid, Mukul, Vricfy` — সব একই কমিটে, সেম সাইজ APK শুধু নাম বদলানো ⇒ ভার্সনিং অর্থহীন |

---

## ৬. ⚪ গুণমান/নীতি ঝুঁকি (ব্লকিং নয়, তবে জানা দরকার)

- **সোর্সের নীতিগত অবস্থান:** "Mukul OTT" endpoint নিজেই বাংলা মুভি/সিরিজের স্ট্রিম + download link দেয় (সম্ভবত লাইসেন্সবিহীন রিপোজিটরি), পাশে `pantyflix.com`, `embed.st`, `livestreamcricket.cc`, `music45-api.vercel.app` (JioSaavn পুনঃহোস্ট) — **কপিরাইট-সংবেদনশীল উৎস**। Play Store নীতি/ডানাওয়ার স্ক্যানে ব্লক হওয়ার সম্ভাবনা বেশি।
- **ইউজার ডেটা:** কোনো প্রাইভেসি পলিসি/consent নেই; ভুল Google একাউন্টে লগইন (৩.৩) "Data safety" ফর্মে মিথ্যা ঘোষণা হবে।
- **Weather AQI বানানো:** `calculateEstimatedAqi()` = temp/humidity/wind দিয়ে **আনুমানিক AQI** (real AQI API নেই) — ব্যবহারকারীকে ভুল বায়ু-মান দেখানো হয়।
- **মৃত/অস্থায়ী এন্ডপয়েন্ট:** `https://github.com/mdmukulahmed01/MukulPlus/...` (ভিন্ন অ্যাকাউন্ট), `banglamoveapi.opaalooman.com`, `mukul-sports.ai.studio`, `downclip.vercel.app` (personal Vercel) — বিল্ড হয়েও runtime-এ ৪০৪/৫xx আসবে।

---

## ৭. অগ্রাধিকার অনুযায়ী করণীয় (Impact → Fix)

### P0 — এখনই (সপ্তাহ ১)
1. **ভার্সন স্কিম ঠিক করুন:** শুধু `vX.Y.Z` ট্যাগ publish করুন; `parseVersionFromTag()`-এ non-semver ট্যাগে `code=0` হলে installed `versionCode`-ই ব্যবহার করুন, আর `isVersionMatched()`-এ "unknown remote version" কে *update available* ধরে নিন। `fileSizeBytes` hardcode বাদ দিন (Content-Length ব্যবহার করুন)।
2. **APK ডাউনলোডে integrity:** GitHub release asset-এর SHA-256 `app-update.json`/checksum ফাইলে রাখুন, ইনস্টল করার **আগে** যাচাই করুন।
3. **Auth ঠিক করুন:** `loginWithEmail`-এ যেকোনো fail হলে `Result.failure`; Google fail-এ ডেভেলপারের Gmail fallback সম্পূর্ণ সরান (guest profile-এ যান)।
4. **`assembleRelease` + minify + signed AAB/APK:** debug APK কখনো public রিলিজে দেবেন না।
5. **রিলিজের সাথে অপ্রাসঙ্গিক third-party APK (Cricfy/Moviebox/SportzX) বান্ডল করা বন্ধ করুন।**

### P1 — ২–৩ সপ্তাহ
6. `MovieDetailScreen` রাউটিং বা `selectedMovieId`-এর ভিত্তিতে নেভিগেশন জোড়া লাগান (নাহলে মুভি ট্যাপ = কিছুই না)।
7. Sports + Reels-কে নেভিগেশনে আনুন, অথবা কোড মুছুন (৯,৯০০ লাইন dead code কমপক্ষে compile time/AAB সাইজ ও audit surface কমাবে)।
8. মিউজিকের জন্য `MediaSessionService` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + audio focus + notification।
9. `POST_NOTIFICATIONS` runtime request + `areNotificationsEnabled` গার্ড।
10. ডাউনলোডে `Range`/resume + pause/resume UI (বর্তমানে শুধু cancel)।
11. keys সরিয়ে `.env` + CI secret; Google keys-এ package/SHA-1 restriction; পুরোনো `mdmukulahmed01` URL বাদ দিন।
12. `usesCleartextTraffic=false`, `MIXED_CONTENT_NEVER_ALLOW`, `file_paths` সংকুচিত করুন।

### P2 — ১ মাস
13. `LanguageManager.get()` ব্যবহার করে সব স্ট্রিং `strings.xml`/translation map-এ নিন (১০ ভাষা দাবি বাস্তব করতে), `imePadding()` যোগ করুন, contentDescription পূরণ করুন।
14. `MainActivity` ভেঙে NavHost + ViewModel; `selectedMovieId` একক সোর্স।
15. রিয়েল টেস্ট (repository parser, update-version logic, downloader) — কমপক্ষে ২০–৩০% কভারেজ।
16. `ExtensionManager` সিদ্ধান্ত: হয় signer-verification সহ DexClassLoader + UI, নাহয় পুরো ডিলিট।
17. Weather AQI-র জন্য আসল API (open-meteo air-quality endpoint) বা "estimated" লেবেল।
18. `.github/workflows`-এ `continue-on-error` সরান, `lint`/`test` বাধ্যতামূলক করুন।

---

## ৮. দুই লাইনে সারমর্ম

অ্যাপটি **দ্রুত বেড়ে ওঠা একটি prototyp** — ৩৩% কোড (≈৯,৯০০ লাইন) অ্যাপে পৌঁছায়ই না, মুভি ডিটেইল পেজ ও স্পোর্টস অদৃশ্য, কোনোCredentials ছাড়াই লগইন "সফল" হয়, আর তার নিজের আপডেট সিস্টেম এই মুহূর্তে **কাজ করছে না** (`Cricfyv2` ট্যাগ পড়তে পারে না)। সবচেয়ে বড় ঝুঁকি সোর্সের নয় — **রিলিজ প্রসেস**: debug APK, অচেক-করা auto-install, এবং রিলিজে অসম্পর্কিত তৃতীয় পক্ষের APK।
