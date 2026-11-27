# Changelog

All notable changes to **Musiqa** are documented in this file.

## [1.1.0] - 2026-11-27

### Added
- **Self-Learning Recommendation Engine (`RecommendationEngine`)**:
  - Tracks playback completion ratio (`>= 80%` liked, `< 25%` skipped), search intent signals, and per-artist affinity scoring (`likes * 3 - skips * 2`).
  - Anti-mood-fatigue variation (`COLD_INJECT_RATE = 0.20`) breaks out of consecutive sad/slow streaks with contrasting discovery tracks, and prevents repeating the last 50 played tracks.
- **Expanded Global & India Onboarding**:
  - Focused languages on English (US), English (India), Hindi, Punjabi, Urdu, and Indian regional options.
  - Expanded genres (19 options) and curated Global + Indian artists (47 singers), plus an inline **"Filter or add any singer"** input and **"Skip for now (Surprise me)"**.
- **Working Explore Section (4 KPI Destinations)**:
  - **New Releases**: Live newest India & Global releases.
  - **Charts & Mood Trends**: Real-time 4 KPI summary cards (Total Plays, Completion Likes %, Top Artist, Dominant Mood), Listener Profile Summary, Mood & Genre Trend bars, Recent Mood History timeline, Artist Affinity Profile, Complete Listening History, and Top India & Global Charts.
  - **Moods & Genres**: Interactive 2-column mood/genre browser.
  - **Podcasts**: Live music podcasts, artist interviews, and studio talks.
- **5 Splash-Inspired Animated Player Cover Styles**:
  - Upgraded **Crimson Neon Vinyl**, **Crimson Sonar Waves**, **Radial Audio Halo**, **Orbital Neon Arcs**, and **Album Sleeve & Vinyl** with live preview in Settings.
- **Top 5 Search History**:
  - Displays the 5 most recent searches with one-tap re-search, `↖` query append, and remove/clear controls.
- **Automated Tests**:
  - Added JVM unit tests (`RecommendationEngineTest`) and Compose UI instrumentation tests (`OnlineAndSearchUiTest`).

### Fixed
- **Dynamic Launch & Home Feed**: Eliminated identical static launch feeds across devices; replaced with parallel dynamic preference/history shelves (*"Similar to..."*, *"Picked for you"*, and middle-placed multi-genre **Song mixes**).
- **Time-Rotating Category Filters**: Category chips (*Trending, Bollywood, Nostalgia, Sad, Sufi & Qawwali*, etc.) now rotate queries on 10-minute intervals, filter skipped tracks, and refresh sequence.
- **Search UX & Original Ranking**:
  - Fixed search cursor jumping to the middle when appending suggestions (`TextFieldValue` with cursor-at-end).
  - Fixed `"No matches found"` / `"No suggestions found"` flickering during async loading.
  - Prioritized original full-length recordings (`>= 10 min`, e.g. Qawwalis/Ghazals) over short 3–4 minute clips.
- **Tab & MiniPlayerBar Persistence**: Restores the last active tab (**My Music** vs **Online Music**) and keeps `MiniPlayerBar` & queue synced to the last played online track across app restarts.
- **App Icon & Splash Screen**: Updated adaptive launcher icon and Android 12+ splash screen to the slanted beamed sixteenth note (`♬`) on Musiqa's Crimson Red (`#E32E27`).

## [1.0.0] - 2026-11-19

### Added
- Initial release with Jetpack Compose AMOLED dark UI, Media3 (ExoPlayer) local & online playback, YouTube Music `WEB_REMIX` + `NewPipeExtractor` streaming, offline downloads, Equalizer/BassBoost, and lyrics support.
