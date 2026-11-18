# Musiqa Online — reference-driven implementation roadmap

## Goal

Replace the placeholder `OnlineScreen` with a polished online-music experience
whose information architecture and interaction quality are *inspired by* the
supplied YT Music Morphe APK, while retaining Musiqa's own visual identity and
using only an authorised catalogue/playback provider.

This is a product and engineering clone of public UX patterns, **not** a copy
of the target APK, its code, artwork, assets, identity, authentication flow, or
private/undocumented service APIs.

## Evidence and current baseline

### Reference APK: static evidence only

* Package: `app.morphe.android.apps.youtube.music`, version `9.20.53`.
* It is a large multi-DEX Android app with a bundled Music web/component
  resource set. Resource names point to browse shelves, list/grid items,
  search, a media hub, playable-item UI, playback progress, queue-like
  controls, likes/dislikes, playlists, timed lyrics, downloads, and Shorts.
* This establishes the desired *feature surface*, not an implementation
  contract. A REA/JADX pass must verify each selected flow before it is
  implemented.

### Musiqa baseline

| Area | Current state | Implication |
| --- | --- | --- |
| Online UI | `OnlineScreen.kt` is a non-interactive Phase 7 placeholder. | This is a greenfield vertical slice. |
| Navigation | `MainActivity.kt` owns tab and sub-screen state. | Introduce typed navigation before Online grows beyond one screen. |
| Playback | `PlayerController` owns one Media3 `ExoPlayer`; `MusiqaMediaService` owns background/session/notification integration. | Reuse the player service, but decouple it from local-only `Song`. |
| Library model | `Song` is MediaStore-specific (`Long` id, local `Uri`, filesystem path). | Online media needs a provider-neutral model and a stable string key. |
| Data layer | A MediaStore scanner exists; no HTTP, database, online repository, ViewModel, or cache layer exists. | Add these before connecting any live provider. |
| Manifest | Local-media and playback permissions exist; `INTERNET` and network-state permissions do not. | Add only the network permissions required by the approved provider. |

## Product boundary — decision gate 0

Do this before writing provider code.

1. Choose the catalogue/playback source:
   - **Recommended:** a licensed music catalogue or the organisation's own
     audio CDN, with contractual rights to stream, cache, and display artwork.
   - **Discovery-only:** an official public video/catalogue API; send playback
     to the provider's authorised app or web surface when direct playback is
     not licensed.
   - **Do not implement:** stream extraction, signature deciphering,
     circumventing ads/subscriptions, use of undocumented endpoints, or reuse
     of target APK assets/code.
2. Write a one-page provider contract: supported regions, authentication,
   rate/quota rules, stream URL lifetime, artwork/lyrics rights, offline rights,
   account-data retention, and deletion process.
3. Lock an initial MVP: **Home, Search, collection detail, queue, and
   authorised online playback**. Keep Shorts, downloads, social actions, and
   personalised recommendations out of the first release.

**Exit criteria:** product owner, provider owner, and legal/compliance agree on
the source and the exact capabilities Musiqa may expose.

## Target information architecture

```
Online
├── Home (greeting, chips, curated shelves)
├── Search (suggestions → results: tracks / albums / artists / playlists)
├── Collection detail (album, playlist, artist, chart)
├── Now playing (shared Musiqa player + online metadata)
├── Queue and actions (save, share, report/provider hand-off as permitted)
└── Later: Library sync, downloads, lyrics, Shorts
```

The Online tab stays the entry point, but destinations must be independently
back-stackable. The mini-player remains global so local and online queues feel
like one product.

## Delivery roadmap

### Phase 1 — reference inventory and UX specification (1–2 days)

1. Update Node to a REA-supported release, then run REA's Android package,
   class, method, and reference inspection against the supplied APK.
2. Record only observable behaviour in `docs/reference/`: launch/home,
   search, a shelf, collection detail, item actions, player, queue, empty/error
   states, and device rotation.
3. Create a screen-by-screen UX spec with Musiqa names, colours, typography,
   dimensions, gestures, loading states, accessibility labels, and test IDs.
4. Convert the inventory into a prioritised parity matrix: **MVP**, **v1**, and
   **explicitly out of scope**.

**Deliverable:** approved parity matrix and clickable Compose wireframes.

### Phase 2 — application foundation and dependency inversion (2–3 days)

1. Move state out of `MainActivity` into screen-scoped ViewModels and add
   Navigation Compose destinations for the Online flow.
2. Add a `core/model` layer:
   - `PlayableMedia(id: String, source: MediaSource, title, artists, album,
     durationMs, artworkUrl, streamRequest, explicit, metadata)`
   - `MediaSource` (`LOCAL`, `CATALOGUE`, `PROVIDER_HANDOFF`)
   - `Collection`, `Creator`, `Shelf`, `SearchResult`, `PlaybackQueueItem`.
3. Preserve the existing `Song` type for MediaStore scanning. Add mappers from
   `Song` and provider DTOs to `PlayableMedia`; do not force online data into
   `Song.path` or a synthetic numeric ID.
4. Replace the current global-controller input boundary with
   `PlaybackCoordinator.play(items, startIndex)`. Internally it may still use
   the current Media3 player and service.
5. Add immutable UI state/result types: `Loading`, `Content`, `Empty`,
   `Error(retryable)`, and `Offline`.

**Likely files:** refactor `MainActivity.kt` and `PlayerController.kt`; add
`ui/navigation/`, `feature/online/`, `core/model/`, and `core/playback/`.

**Exit criteria:** local playback regression tests pass and a fake Online
repository can drive every screen without a network connection.

### Phase 3 — provider boundary, persistence, and resilience (2–4 days)

1. Define `OnlineMusicRepository` with methods for home shelves, search,
   collection detail, playback resolution, and permitted library mutations.
2. Implement a `FakeOnlineMusicRepository` with fixtures first. It is the
   development and UI-test data source.
3. Add the approved provider adapter behind that interface. Keep credentials,
   request signing, refresh tokens, and stream-resolution details outside
   composables and never log secrets or raw account responses.
4. Add `INTERNET` and `ACCESS_NETWORK_STATE`; add no unrelated target-app
   permissions. Use an HTTP client with timeouts, cancellation, retry/backoff,
   certificate defaults, and redacted diagnostics.
5. Add Room/DataStore as appropriate for query history, user preferences,
   lightweight metadata cache, and the last queue. Cache only content that the
   provider agreement permits.

**Exit criteria:** provider failures are actionable, stale content is labelled,
and no Online screen blocks the main thread.

### Phase 4 — Online home and reusable components (3–4 days)

1. Replace the placeholder with a `LazyColumn` home screen: top app bar,
   search entry, filter chips, hero/continue-listening area (only if data
   exists), and horizontally scrolling shelves.
2. Build reusable Compose components: `Artwork`, `MediaRow`, `MediaCard`,
   `Shelf`, `SectionHeader`, `OverflowMenu`, `LoadingSkeleton`, and
   `InlineError`.
3. Use Coil for remote artwork with fixed aspect ratios, crossfade, placeholders,
   error art, and content descriptions.
4. Implement deterministic fake data previews for phone, tablet, font scale,
   RTL, and dark AMOLED mode.

**Exit criteria:** Home exactly handles loading, empty, error, offline, narrow,
and wide layouts; tapping a playable item queues it through the shared player.

### Phase 5 — Search and collection exploration (4–5 days)

1. Build debounced search with cancellation, recent queries, suggestions, and
   a single results stream split into tracks/albums/artists/playlists.
2. Build collection/artist/album/playlist detail destinations with hero art,
   primary Play/Shuffle actions, metadata, paged media lists, and overflow
   actions permitted by the provider contract.
3. Support stable deep links only after provider IDs and route contracts are
   finalised.
4. Add paging for all unbounded lists; do not load an entire catalogue into
   memory.

**Exit criteria:** search → detail → play and Home → shelf → detail → shuffle
work with process recreation and an interrupted network request.

### Phase 6 — unified playback and queue (3–5 days)

1. Teach the playback coordinator to construct Media3 items from provider-
   authorised streams, refresh expiring URLs safely, and surface meaningful
   errors when resolution fails.
2. Update the mini-player, Now Playing screen, notification, Bluetooth/media
   controls, favourites, recents, and queue UI to read the neutral media model.
3. Define a clear source-switch rule: a new local or online queue replaces the
   active queue; retain previous queue metadata only if product requirements
   call for resume.
4. Verify audio focus, noisy-headphone handling, foreground-service behaviour,
   sleep timer, shuffle/repeat, artwork, and rotation/background recovery.

**Exit criteria:** a provider-authorised online item plays correctly in app,
notification, lock screen, and Bluetooth controls without breaking local music.

### Phase 7 — polish, personalisation, and library actions (3–5 days)

1. Add permitted favourites/saves, recent activity, selected sort/filter chips,
   share, and provider hand-off.
2. Add small, useful motion: shelf loading transitions, mini-player expansion,
   item press/selection feedback, and queue reordering. Respect reduced motion
   and never block controls behind animation.
3. Implement analytics only after consent and with event names that do not
   retain search terms or account identifiers by default.

**Exit criteria:** core actions are discoverable with TalkBack and keyboard/
switch access, and all user data has a defined retention/deletion path.

### Phase 8 — intentionally deferred modules (estimate separately)

* **Downloads/offline:** only with encrypted, provider-authorised media,
  entitlement checks, storage accounting, expiry/renewal, and WorkManager.
* **Timed lyrics:** only with a licensed lyrics provider; preserve Musiqa's
  local LRC path as a separate source.
* **Shorts/video:** a separate vertical-video product surface with an approved
  player/embed, feed pagination, muted autoplay policy, parental controls,
  and data-use limits. Do not bundle it into the music MVP.
* **Account sync/recommendations:** requires authentication, consent,
  server-side policy, and privacy review.

## Test and release plan

| Layer | Required checks |
| --- | --- |
| Unit | mappers, repository result mapping, ViewModels, search debounce/cancellation, queue rules, expiring-stream refresh. |
| UI | Compose semantics and screenshots for all Home/Search/Detail states, phone/tablet, dark theme, font scale, and RTL. |
| Integration | fake provider and approved live sandbox; no raw production credentials in tests. |
| Playback | local and online queues, notification, audio focus, headset/Bluetooth, background, network loss/recovery, process death. |
| Security/privacy | dependency scan, redacted logs, network security config, provider-key storage review, cache purge, consent review. |
| Acceptance | 60fps scrolling on a representative device, no blocked UI while fetching, visible retry/offline state, TalkBack completion of the main play path. |

## Delivery sequence and definition of done

1. **Foundation PR:** Phases 1–3; fake data only, no provider secrets.
2. **Browse PR:** Phase 4; Home UI and shared components.
3. **Discover PR:** Phase 5; Search and details.
4. **Playback PR:** Phase 6; authorised streaming and local-regression suite.
5. **Release PR:** Phase 7 plus security, accessibility, and performance sign-off.

The Online clone is complete when an approved provider can drive Home, Search,
Detail, Queue, and background playback; local playback remains intact; all
failure states are usable; and the finished product contains no copied APK
code/assets or unauthorised playback path.

## Immediate next actions

1. Upgrade Node to at least `24.22.1` (or use a supported Node 22/26 release).
2. Run REA against `YT Music Morphe - v9.20.53.apk` and attach the evidence
   report to `docs/reference/`.
3. Choose and document the authorised provider model.
4. Implement the Phase 2 foundation with the fake repository before any live
   network integration.
