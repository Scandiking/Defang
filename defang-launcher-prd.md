# Defang — Android Launcher with Anti-Dark-Pattern Layer
**PRD v0.3 | 2026-07-07 | Personal project, Android, no root**
*See also: [defang-android-setup.md](defang-android-setup.md) — project structure, folder layout, dependencies*

---

## Table of contents

| # | Section |
|---|---|
| 1 | [Problem statement](#problem-statement) |
| 2 | [Design philosophy: speed bumps, not walls](#design-philosophy-speed-bumps-not-walls) |
| 3 | [Goals](#goals) |
| 4 | [Non-goals](#non-goals) |
| 5 | [App classification model](#app-classification-model) |
| 6 | [User stories](#user-stories) |
| 7 | [Requirements — P0 must-have](#must-have-p0) |
| 7.1 | → Onboarding tour (5 screens) |
| 7.2 | → App tier configuration |
| 7.3 | → Pre-open intent gate |
| 7.4 | → Session timer overlay + bypass design |
| 7.5 | → Per-app grayscale |
| 7.6 | → Notification batching |
| 7.7 | → Awareness content ("Did you know") |
| 7.8 | → Offline activity prompts |
| 8 | [Requirements — P1 nice-to-have](#nice-to-have-p1) |
| 8.1 | → Usage dashboard widget |
| 8.2 | → Intent honesty score |
| 8.3 | → Scheduled app windows |
| 8.4 | → One-tap focus mode |
| 8.5 | → Bedtime friction escalation (via system DnD) |
| 9 | [Requirements — P2 future](#future-considerations-p2) |
| 10 | [Technical notes](#technical-notes) — permissions, i18n, constraints |
| 11 | [Success metrics](#success-metrics) |
| 12 | [Open questions](#open-questions) |
| 13 | [Timeline and phasing](#timeline-considerations) |
| A1 | [Appendix: awareness content library](#appendix-awareness-content-library-draft) |
| A2 | [Appendix: offline activity prompts](#appendix-offline-activity-prompts-draft) |

---

## Problem statement

Social media apps — Instagram in particular — are engineered to exploit the cue-routine-reward loop: notifications create the cue, the app opens instantly, and algorithmic feeds with infinite scroll deliver unpredictable rewards that keep users scrolling. Existing OS-level countermeasures (system grayscale, Digital Wellbeing timers, minimalist launchers) only address the *cue* phase. They do nothing once the app is open. Worse, Instagram's own built-in time limit ships with a trivially-clickable "15 more minutes" button that trains users to dismiss the friction rather than respect it — the countermeasure becomes part of the habit loop. The result is that a user who is aware of the manipulation and wants to resist it still has no tool that meets them inside the app with friction proportional to the design aggression being used against them.

---

## Design philosophy: speed bumps, not walls

This is not a parental control app and it is not a kill-switch. The user is an adult who wants to make deliberate choices rather than compulsive ones. Every mechanism should be understood as raising the cost of unconscious behavior, not preventing conscious choice. A wall breeds circumvention; a speed bump breeds pause. The goal is to insert enough friction that the user has to *want* to proceed — and sometimes they will, and that's fine.

---

## Goals

1. Reduce unintentional Instagram/Reels session starts to near-zero — the user opens the app only with declared intent.
2. Cap actual in-app time at the user's own preset limit without a trivially-bypassable "ignore" button.
3. Eliminate the notification vector as an autonomous trigger — social pings arrive on the user's schedule, not the app's.
4. Make the phone *feel* less rewarding to pick up without degrading utility apps (camera, calculator, maps, messaging with real contacts).
5. Give the user legible data on their own patterns so behavior change is grounded in evidence, not guilt.

---

## Non-goals

1. **Modifying app internals.** Without a third-party API or rooted device, the launcher cannot remove the Reels tab, disable pull-to-refresh, or alter the feed algorithm. This is a hard platform constraint.
2. **iOS support.** iOS does not permit custom launchers or Accessibility Service overlays. Out of scope entirely.
3. **Blocking specific content or accounts.** Content moderation is out of scope; this spec covers temporal and friction controls only.
4. **Social accountability / sharing.** Leaderboards, friend challenges, or social streaks are excluded — they reproduce the attention mechanics being dismantled.
5. **Real-life habit logging / feat tracking.** Potentially valuable as a companion product; deliberately excluded here to keep scope tight. It does not belong in a launcher.
6. **Managing the underlying emotion driving phone use.** The app creates friction and visibility. It does not treat root causes.

---

## App classification model

Not every app on the device is a problem. The launcher uses a three-tier model:

| Tier | Examples | Behavior |
|---|---|---|
| **Utility** (default) | Camera, Calculator, Phone, Maps, Clock | Zero friction — opens instantly, no overlay, no timer |
| **Watched** (user-designated) | Instagram, Snapchat, TikTok, Reddit | Full friction stack: intent gate → grayscale → session timer → cool-down |
| **Browser — social domain** | reddit.com, twitter.com, youtube.com | Per-domain friction; social media content track |
| **Browser — adult domain** | (user-configurable; default list ships with the app) | Per-domain friction; adult content track with neurological awareness content |
| **Browser — utility domain** | google.com, wikipedia.org | Zero friction |

The launcher ships with a sensible default watched list (major social apps) and a sensible utility whitelist. The user can move any app between tiers. When in doubt, default to Utility — the launcher should never get in the way of the user's life.

---

## User stories

### Primary persona: aware but losing — knows the manipulation exists, keeps losing anyway

- As an aware user, I want to be asked *why* I'm opening a social app before it launches, so that reflexive, intention-free opens are interrupted before they become sessions.
- As an aware user, I want an in-app countdown that cannot be casually dismissed, so that when time is up, it is actually up — not "15 more minutes" four times in a row.
- As an aware user, I want specific apps to render in grayscale *without* putting my whole phone in grayscale, so that the dopamine-coded colors that make feeds feel rewarding are removed while my camera, photos, and maps remain usable.
- As an aware user, I want social media notifications batched and delivered at a time I choose, so that I'm not continuously interrupted and pulled back in.
- As an aware user, I want a cool-down lockout after a session ends, so that closing Instagram and immediately reopening it is blocked.
- As an aware user, I want to see a weekly time-in-app summary, so I can calibrate my own limits with real data rather than guilt.

### Edge case: legitimate intent

- As a user with a genuine reason to open a watched app (posting, replying to a DM from a real person), I want to declare that intent before entry so the friction gate is satisfied without interrupting the task itself.

### Edge case: utility apps

- As a user, I want to open my camera, calculator, and phone without any friction, so the launcher never slows down non-problematic behavior.

---

## Requirements

### Must-have (P0)

**First-launch onboarding tour**

Shown once, on first launch, before any configuration. Five screens. The user can tap "Skip" at any time to jump to configuration — but the tour should be short enough that skipping feels unnecessary. No animations, no gamification, no progress bar with percentage. Just text and a Next button.

The tone throughout is direct and slightly sardonic. This is not a wellness app and it should not read like one.

---

*Screen 1 — What this is*

Heading: **This is a speed bump. Not a wall.**

Body: Some apps will block your phone completely. Some will go full grayscale through developer settings. This is not that. You can still open Instagram. You can still override the timer. Nothing here is impossible to bypass.

That is deliberate. A wall creates pressure. Pressure demands release. You've seen this: Instagram's own time limit ships with a "15 more minutes" button that you tap without thinking. The limit exists, but the exit is so frictionless that it trains you to ignore the limit, not respect it.

This app puts a speed bump in front of the reflex. It makes the unconscious open a conscious one. What you do after that is up to you.

---

*Screen 2 — What this is not*

Heading: **Not a willpower app. Not a parental control.**

Body: You probably already know that Instagram is engineered to keep you scrolling. You probably already know that pull-to-refresh mimics a slot machine. Knowing this has not been enough, because the systems working against you are not aimed at your knowledge — they are aimed at the part of your brain that acts before you think.

This app is not asking you to try harder. It is inserting a small pause between the impulse and the action. That pause is where your prefrontal cortex lives.

It also does not touch your developer settings, does not require a rooted phone, and does not apply system-wide grayscale. Your camera, your maps, and your calculator work exactly as before.

---

*Screen 3 — The loop*

Heading: **Cue → routine → reward. They own the cue.**

Body: Every habit runs on a three-part loop: a cue triggers a routine that delivers a reward. Apps have industrialized the cue. The red badge, the notification at exactly the right moment, the muscle memory of unlocking your phone when you're bored — all of it is a designed cue that launches the routine before you've decided anything.

This app targets two points in the loop. Before you open: it interrupts the cue with a pause and asks what you actually want. After you close: it tries to hand you a different reward — something real, physical, and yours — before the loop resets.

It cannot touch what's inside the app. But the cue and the landing are up for grabs.

---

*Screen 4 — Why small tasks*

Heading: **"Tidy the coffee table" is not a joke.**

Body: When you reach for your phone out of boredom or restlessness, you are in a low-energy, low-motivation state. A suggestion like "clean the bathroom" in that state does not create action. It creates a second reason to open Instagram.

Small tasks work because they are completable. Completing something — anything — produces real dopamine: the kind that persists, not the kind that fades in three seconds. "Put one thing back where it belongs" is achievable in 20 seconds. Once you've done it, you're standing up, you've moved, and the loop has been interrupted by something that required your body, not just your thumb.

The tasks are not there to make you productive. They are there to give the reward system something honest to work with.

---

*Screen 5 — Setup*

Heading: **Let's set it up. Two minutes.**

Body: Pick which apps get the speed bump. Set how long you want to spend in them before the timer kicks in. Choose when you want notifications from those apps to arrive.

You can change everything later. The defaults are reasonable starting points, not recommendations.

[Continue to setup →]

---

Acceptance criteria: the tour appears exactly once, on the first launch after install; "Skip" at any point advances to the configuration screen; all tour text is in translatable string resources; the tour is re-accessible from settings under "About this app."

---

**App tier configuration**
- On first launch, the launcher presents a categorization flow: "These apps are watched by default — adjust if needed."
- The user can move any installed app between Utility, Watched, and (for browsers) Browser tiers.
- Tier assignments persist and can be edited in settings at any time.
- Acceptance criteria: utility apps open with zero perceptible delay and no overlay; watched apps always trigger the intent gate; changes take effect without restart.

**Pre-open intent gate**
- When the user taps a Watched app, a full-screen interstitial appears *before* the app launches.
- The interstitial displays a single plain-text question ("What are you opening this for?") with a configurable delay (default: 8 seconds) before the "Open anyway" button becomes active. The countdown is visible and non-dismissible.
- Alternatively, the user can tap a predeclared intent card ("Post something", "Check a DM", "Look up something specific") to skip the countdown. Declared intents are logged.
- The interstitial can always be dismissed (return to home screen) — it is a speed bump, not a trap.
- During the countdown, the interstitial displays a rotating "Did you know" tidbit (see *Awareness content* below). The tidbit fills the dead time and primes the user with concrete knowledge about the mechanism being used against them.
- Acceptance criteria: tapping a watched app icon never goes directly to the app; the "Open anyway" button is visually inactive and non-tappable during the countdown; the countdown cannot be skipped by any gesture or back-press; a tidbit is always visible during the countdown.

**In-app session timer overlay and the bypass problem**

The design of the session-end state is the most important UX decision in this spec. Instagram's own time limit fails precisely because the "15 more minutes" button is one tap, zero friction, and infinitely repeatable. This implementation must not reproduce that failure.

- After passing the intent gate, a minimal HUD (time remaining) is drawn over the target app via `SYSTEM_ALERT_WINDOW`.
- At session limit: the overlay expands to a full-screen end-card. The app is moved to background. The end-card displays:
  - Total time used in this session
  - A summary of the user's own stated intent vs. time spent
  - A "I need more time" affordance — but with friction:
    - The button is not visible for the first 30 seconds of the end-card (prevents reflex tap)
    - Tapping it shows a secondary prompt: "Why?" with a free-text field (minimum 10 characters required)
    - Extending is limited to **once per day total** across all watched apps — after the daily extension is used, there is no more time
    - The extension adds a fixed amount of time (default: 10 minutes, not configurable mid-session) and cannot be chained
- After the extended session ends, the cool-down begins regardless. There is no second extension.
- The end-card displays a tidbit framed around what just happened ("You just spent 23 minutes in Instagram. Pull-to-refresh was modelled on slot machine mechanics — the unpredictability of what appears is the point.").
- The end-card and the cool-down lockout screen both display an **offline activity prompt** — a small, concrete, no-screen suggestion for what to do right now (see *Offline activity prompts* below). The prompt is not a lecture; it is a one-line action like "Tidy the coffee table" or "Look out the window for 60 seconds." The user can tap to get a different one.
- Cool-down period (default: 30 minutes) blocks reopening the app. Attempting to open it shows a countdown to next available open.
- Cool-down persists across app restarts and device reboots.
- Acceptance criteria: "I need more time" button is invisible for 30 seconds post-limit; free-text entry is required and validated for length; one extension per day is enforced across all watched apps; cool-down survives process kill; the extended session timer is identical in behavior to the original.

**Per-app grayscale**
- For watched apps, a grayscale filter is applied via Accessibility Service rendering overlay.
- System-wide grayscale is not enabled; utility apps and the launcher itself render in color.
- Acceptance criteria: grayscale is active within 500ms of the watched app becoming foreground; color restores within 500ms when the user leaves the app; the filter cannot be toggled off during a session from within the app.

**Notification batching**
- The user designates one or two daily delivery windows per watched app (e.g. 12:00 only, or 12:00 + 18:00).
- Notifications from watched apps are intercepted via Notification Listener Service and suppressed until the delivery window, at which point they are re-posted as a single batched summary notification.
- Hard bypass: calls and notifications from contacts in the user's address book are never batched.
- Acceptance criteria: zero watched-app notifications appear outside the delivery window; batched summary count is accurate; address-book contacts are correctly excluded from batching.

**Awareness content ("Did you know")**

The app ships with a curated library of short, specific, factual tidbits about how attention engineering works. These appear in three locations: during the intent gate countdown (captive, 8 seconds), on the session end-card, and on the cool-down lockout screen.

Content principles:
- One to two sentences maximum. If it can't fit in the space a fortune cookie takes up, it's too long.
- Specific and concrete, not moral. "Instagram's notification badge is red because red triggers faster attention capture than any other color — it was chosen deliberately" lands. "Social media is bad for you" does not.
- Framed as "they did this to you" not "you should be better." The user is not the problem; the design is.
- Mix of categories: variable reward schedules, color psychology, notification timing optimization, autoplay mechanics, social validation loops, the business model behind free apps, and the neuroscience of delayed vs. instant gratification.
- Delayed gratification angle should appear but not dominate — one in five or six tidbits, framed positively ("dopamine from completing a hard task lasts longer than dopamine from a scroll") not prescriptively.
- Rotate pseudo-randomly, weighted so the same tidbit doesn't appear twice in a row or within the same day.

Content is a static library bundled with the app (no server required, no dynamic updates in v1). The library should ship with at least 40 tidbits per content track to avoid repetition fatigue within a week of daily use.

**No "learn more" links. Ever.**

Tidbits will provoke curiosity — this is intentional. A user reading about escalation patterns will naturally wonder where lines are drawn. A user reading about DeltaFosB will want to know more. The app does not resolve this curiosity. It plants the question and leaves it unanswered.

This is the correct outcome. A question that lives in the user's head until they choose to pursue it in a browser they deliberately opened is doing more cognitive work than a "learn more" button that launches a tab from within a friction screen. A "learn more" button on an intent gate is a hole in the boat — it sends the user to an open browser from the exact screen designed to create pause.

No clickable links appear anywhere on the intent gate, end-card, or cool-down screen. No "learn more." No URLs. No footnotes.

**Citations: text-only, in settings**

Tidbits are more credible when they are attributable, but citations on friction screens create the same problem as "learn more" links. The solution:

- Friction screens (intent gate, end-card, cool-down) show tidbits with no attribution at all. The text must stand on its own.
- The settings section "What we know about them" contains the full content library in readable list form, with slightly expanded versions and text-only attribution (Author, Year — e.g. "Aza Raskin, 2006" or "Hebb, 1949"). No clickable links in settings either. If a user wants to verify or read the source, they open a browser themselves. That deliberate friction is appropriate.

**Writing discipline for the content library**

Tidbits that describe mechanisms without drawing categorical lines avoid a specific failure mode: the user who reads "what starts with mainstream content can end somewhere unthinkable" does not immediately ask "but is X mainstream?" and get pulled into a definitional debate. The mechanism is the point; the taxonomy is not. Every tidbit about escalation, extremity, or comparison to norms should be written to describe *what happens to the brain* rather than *what content crosses a threshold*. The question "is this normal?" should be left for the user to carry, not answered in two sentences.

The app selects the content track based on context:
- **Social media track** — shown when the intent gate or end-card is for a Watched social app (Instagram, Snapchat, TikTok) or a social browser domain.
- **Adult content track** — shown when the intent gate or end-card is for a browser adult domain. This track covers the neuroscience of pornography specifically: the Coolidge effect, superstimuli, absence of natural stop signals, DeltaFosB accumulation, and porn-induced erectile dysfunction. Framing is identical to the social track — factual, specific, "they engineered this," not moralistic.
- **General track** — shown in all other contexts; covers themes applicable to all compulsive digital behavior (dopamine mechanics, prefrontal impairment, FOMO, Hebbian learning).

Tracks can share tidbits where content applies to both (e.g. variable reward schedules, prefrontal circuit effects).

Acceptance criteria: a tidbit is always shown during the countdown; the correct content track is selected based on the app/domain being opened; tidbits are never repeated within the same calendar day; the library is readable as a standalone list in settings ("What we know about them").

**Offline activity prompts**

When a session ends or a cool-down begins, the end-card and lockout screen display a single suggested offline micro-task. Requirements:
- Small, physical, achievable in under 5 minutes, no screen required.
- Not aspirational or self-improvement-coded. "Tidy the coffee table" not "Clean the apartment." "Take a walk around the block" not "Exercise."
- The user can tap "Give me another one" to cycle through alternatives — the prompt is a suggestion, not an assignment.
- Prompts are categorized (movement, space, sensory, social analog, creative) and the selection algorithm balances across categories across the day — don't suggest five walks in a row.
- The library ships with at least 30 prompts. See appendix for the draft set.
- Acceptance criteria: a prompt always appears on the end-card and lockout screen; "another one" cycles without repeating within the same session-end; prompts are translatable strings (not hardcoded).

Sample tidbits (non-exhaustive — the full library is in the appendix):
- *Pull-to-refresh was deliberately designed to mimic a slot machine lever. The slight delay before new content appears is not a loading lag — it's engineered suspense.*
- *Instagram tests the exact shade of red used for notification badges. Warmer reds cause faster involuntary attention than cooler ones. The choice was not aesthetic.*
- *The "likes" counter was hidden from public view in 2019 after internal research showed it was damaging mental health. It was restored after engagement dropped.*
- *Infinite scroll was invented by Aza Raskin in 2006. He has since publicly apologized for it.*
- *Apps send push notifications at statistically optimal times for your personal re-engagement — not when something happened, but when you're most likely to open the app.*
- *Dopamine released by completing something difficult — finishing a project, learning a skill — persists for hours. Dopamine released by a new notification peaks in seconds and drops below baseline when it fades.*

---

### Nice-to-have (P1)

**Usage dashboard home screen widget**
- A 2×2 or 4×1 widget showing today's time in each watched app vs. user-set limit, as a simple bar.
- Updates every 5 minutes via UsageStatsManager.
- Tapping a bar opens that app's session history.

**Intent honesty score**
- Weekly summary: for each session where intent was declared, compare declared intent type to actual session duration. Surface a "drift" figure — how often did a "check a DM" turn into a 20-minute scroll.
- No ML; pure heuristic (declared intent = quick task, session duration > 8 minutes = drift).

**Scheduled app windows**
- Instead of per-session limits, allow watched apps to only be openable during configured time windows (e.g. Instagram only 17:00–18:00).
- Attempts outside the window show next available time.

**One-tap focus mode**
- Home screen toggle that activates maximum friction on all watched apps simultaneously (intent gate at maximum delay, grayscale forced on, session limits halved) for a configurable duration.

**Bedtime friction escalation**
- During the user's system-configured Bedtime or Do Not Disturb schedule, the app automatically escalates friction on all watched apps: intent gate delay doubled, session limits halved, cool-down duration doubled.
- The app reads the active schedule via Android's `NotificationManager` / `AutomaticZenRule` API. No additional configuration in this app is required or offered — the user's existing system schedule is the source of truth.
- If the user has not configured a system Bedtime or DnD schedule, this feature is silently inactive. The app does not apply a fallback default window and does not guess working hours.
- The onboarding tour setup screen mentions this in one line: *"If you've set a Bedtime or Do Not Disturb schedule on your phone, this app will automatically increase friction during those hours."*
- Dark mode follows the system setting. A harsh bright UI at night does not reduce phone use — it just makes this app worse than the alternatives. Escalated friction does the actual work.
- Acceptance criteria: friction escalation activates and deactivates in sync with the system DnD window; users with no system schedule see no change in behavior; the feature requires no configuration UI in this app.

---

### Future considerations (P2)

**Browser per-domain rules**
- Allow the user to designate specific domains within the browser as "watched" (e.g. reddit.com gets the intent gate and timer; google.com does not).
- Requires reading the browser's URL bar via Accessibility Service — feasible but fragile, browser-specific, and higher complexity than app-level rules.

**Third-party client routing**
- If a watched app develops an open API or third-party client (e.g. a feed reader without Reels), route opens to that client instead.
- No major social apps support this currently.

**VPN-based API blocking**
- Block specific API endpoints (e.g. the Reels feed endpoint) at the network level via local VPN.
- Nuclear option; useful if overlay-based friction is insufficient or if Instagram begins detecting and blocking overlays.

**Accessibility Service content detection**
- Detect specific on-screen UI patterns (e.g. vertical full-screen video) and apply additional friction (e.g. "You've been watching Reels for 5 minutes. Continue?").
- High complexity, fragile against app updates.

---

## Technical notes

The full feature set is achievable on unrooted Android using four permissions:

| Permission | Use |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Intent gate interstitial, session timer HUD |
| `BIND_ACCESSIBILITY_SERVICE` | Per-app grayscale, foreground app detection |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Notification interception and batching |
| `PACKAGE_USAGE_STATS` | Usage dashboard, session history |

All four require explicit user grant via Android settings. None require root.

**Distribution:** GitHub APK as primary, F-Droid as secondary. Google Play is off the table — see below.

As of September 2026 (approximately 85 days from the date of this document), Google will block installation of all apps from developers who have not registered centrally, paid a fee, and surrendered government ID. Defang will not comply. This makes GitHub + F-Droid the only viable distribution channels. Users who install before the lockdown are unaffected; users installing after will need to complete Google's 9-step "escape hatch" flow.

The app includes the **FreeDroidWarn** library (`github.com/woheller69/FreeDroidWarn`) to warn users about this in advance, with enough lead time to understand and prepare. This warning is shown clearly, not buried. See `defang-android-setup.md §9` for full implementation details.

**Internationalization (i18n):**

All code is written in English. All user-facing strings — UI labels, intent gate text, end-card copy, tidbit content, offline prompts, settings labels — are externalized from day one into Android's standard `res/values/strings.xml` structure. No hardcoded strings anywhere in the codebase.

The content libraries (tidbits and offline prompts) are stored in translatable resource files (strings.xml or equivalent JSON/YAML) rather than in code, so adding a language requires only a translation file, not a code change. English ships as the source language and the only language in v1. The architecture must not require a second language to be added in order to validate that i18n works — a Norwegian `strings-nb.xml` can serve as a smoke test even if it ships incomplete.

**Key constraints:**
- Android battery optimization will kill background services on many OEMs (Samsung, Xiaomi, OnePlus especially). The app must request battery optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and walk the user through granting it — this is non-negotiable for the timer and grayscale to work reliably.
- Accessibility Services must declare their purpose in the service description XML. Vague descriptions cause Play rejection and user distrust.
- The grayscale overlay approach (translucent gray `View` drawn over the target) is not a true color filter and can be partially defeated by apps rendering at the surface level. Android 14+ supports `ColorFilter` at the window level without root — this is the preferred approach on supported OS versions, with the overlay as fallback.
- The "once per day extension" limit must be stored in a way that survives app kills and reboots (SharedPreferences with a date key is sufficient; no server required).

---

## Success metrics

All metrics are measured locally on-device. No telemetry without explicit opt-in.

### Leading (days to weeks post-launch)

| Metric | Target |
|---|---|
| % of watched-app opens that complete the intent gate | >90% |
| Daily extension used | ≤1× per day (by definition — measuring whether users try to circumvent the 1× limit) |
| Avg. daily time in watched apps | Reduction of ≥40% from baseline week |
| Notifications received outside batch window | 0 |

### Lagging (4–8 weeks)

| Metric | Target |
|---|---|
| Self-reported compulsive open feeling (weekly self-rating 1–5) | Down ≥1 point from baseline |
| Sessions with declared intent | >70% |
| Intent drift rate | Trending down week-over-week |
| User-initiated limit increases | <20% of sessions — sticky limits indicate the friction is working |

---

## Open questions

| Question | Owner | Blocking? |
|---|---|---|
| Does Android 14/15 restrict `SYSTEM_ALERT_WINDOW` overlays over flagged apps? Instagram may actively detect and block overlays. | Engineering | Yes — determines feasibility of HUD |
| Does the Notification Listener Service reliably intercept Instagram notifications, or does Instagram use a channel that bypasses it? | Engineering | Yes — determines batching viability |
| Does Samsung OneUI / Xiaomi MIUI kill Accessibility Services under normal battery conditions in a way that breaks the overlay? | Engineering | Yes — major distribution risk |
| Browser handling: is per-domain Accessibility Service URL parsing reliable enough to be P1, or should it stay P2? | Engineering | No |
| What free-text length threshold on the extension prompt is enough to create genuine friction without feeling punitive? 10 characters? A full sentence? | Design / self-experiment | No |
| Should the daily extension limit be per-app or global across all watched apps? (Global is stricter; per-app is more forgiving.) | Design | No |

---

## Timeline considerations

No external deadline. Suggested phasing:

**Phase 1 (MVP):** Intent gate + session timer with the extension-friction design. These two together break the two most powerful dark pattern mechanics: the reflexive open and the infinite session. No grayscale, no notification batching. Ship when these are stable and the extension-bypass design has been self-tested for at least 2 weeks.

**Phase 2:** Per-app grayscale + notification batching. Higher permission complexity; validate against Phase 1 behavior data before building.

**Phase 3:** Usage dashboard widget + intent honesty score. Data layer is built in Phase 1; visualization is the only addition.

**Phase 4 (if needed):** VPN-based API blocking or browser per-domain rules, if Phase 1–3 friction proves insufficient or Instagram actively defeats the overlays.

---

## Reference sources

For expanding the awareness content library, onboarding copy, and future tidbit additions. Not linked in-app — for development use only.

| Source | Notes |
|---|---|
| [deceptive.design](https://deceptive.design) | Harry Brignull's original dark patterns taxonomy. The canonical reference; most industry terminology traces back here. |
| [forbrukerradet.no/manipulerende-design](https://www.forbrukerradet.no/manipulerende-design/) | Norwegian Consumer Council — manipulative design. Norwegian-language. Directly relevant for the `values-nb` translation and for legally-grounded categorizations. |
| [forbrukerradet.no/dark-patterns](https://www.forbrukerradet.no/dark-patterns/) | Norwegian Consumer Council — dark patterns enforcement reports. Forbrukerrådet has been active in EEA enforcement actions (notably against Grindr, Facebook), making their categorizations legally anchored, not just academic. |
| [yourbrainonporn.com](https://yourbrainonporn.com) | Primary source for adult content track tidbits. Covers neuroscience, counter-research, and the PIED evidence base. Note the male-skew acknowledged in the PRD. |

---

## Appendix: awareness content library (draft)

These are bundled with the app. Goal is 40+ at launch. This is a working draft — cut anything that reads as preachy, add anything that is specific and surprising.

**Variable reward / slot machine mechanics**
- Pull-to-refresh was deliberately modelled on a slot machine lever. The brief delay before content loads is engineered suspense, not a loading lag.
- Infinite scroll was invented by Aza Raskin in 2006. He has publicly called it a mistake and estimates it costs humanity 200,000 hours of attention per day.
- The unpredictability of what appears in your feed is the feature, not a side effect. Fixed rewards (always the same thing) cause habituation. Variable rewards cause compulsion.
- "Suggested posts" appear after you exhaust your actual social feed because an empty feed would end the session. There is no bottom.

**Color and visual design as manipulation**
- Instagram's notification badge is red because red triggers faster involuntary attention capture than any other color on the visible spectrum. This was tested and chosen deliberately.
- The high saturation and contrast of social media UIs is calibrated to feel more vivid and rewarding than the physical world around you. This is why your phone looks more interesting than the room you're sitting in.
- Grayscale mode reduces compulsive phone use in controlled studies — not because gray is boring, but because the color coding in social UIs carries emotional signals that gray removes.
- Autoplay video starts moving before you consciously decide to watch it. Motion is a pre-conscious attention trigger; the decision to watch happens after your attention is already captured.

**Notifications as a mechanism**
- Apps do not send notifications when things happen. They send notifications when their models predict you are most likely to open the app. The timing is optimized for re-engagement, not for your information.
- The red badge on an app icon creates an open loop in working memory — an unresolved task. Humans are wired to resolve open loops. The badge exploits this at zero cost to the app.
- Turning off notifications reduces passive phone pickup by around 30% in behavioural studies — not because the notifications were informative, but because they were triggers.

**Business model**
- Instagram, TikTok, and Snapchat are not social apps. They are advertising platforms. Every minute you spend in the app is inventory sold to brands. Your attention is the product.
- The "likes" counter was hidden from public view in 2019 after internal research showed it was damaging mental health. It was restored after engagement metrics dropped. The research didn't change; the business decision did.
- Social apps are free because making you pay would cap their revenue at what you're willing to pay. Selling your attention to advertisers has no such cap.

**FOMO and social anxiety engineering**
- FOMO (fear of missing out) is not a personality trait. It is a response to a system designed to make you feel like the most important things are always happening somewhere you are not currently looking.
- Instagram Stories expire after 24 hours. This is not a technical limitation. It is a designed urgency mechanism — a countdown clock on social participation.
- Snapchat streaks weaponize FOMO by attaching a daily social obligation to friendship. Missing a day feels like failing the relationship. The relationship is with an app metric.
- The phrase "X people reacted to your post" is phrased in terms of other people's behavior, not your content. This is deliberate — other people's opinions are a stronger re-engagement trigger than your own actions.

**Neurological effects of compulsive use**
- Repeated activation of the dopamine reward pathway by the same stimulus causes sensitization (the craving gets stronger) while simultaneously causing desensitization (the pleasure from the reward gets weaker). You need more to feel the same, but you also feel less when you get it.
- The prefrontal cortex is responsible for impulse control, long-term planning, and the ability to say no. Chronic compulsive phone use is associated with measurable reductions in prefrontal grey matter density — the same region implicated in addiction.
- The stress system and the reward system are linked. When a habitual reward is unavailable — the phone is in another room, the app is blocked — the stress system activates. This is withdrawal, not boredom. The discomfort of not checking is physiological, not a character flaw.
- "Neurons that fire together wire together" (Hebb, 1949). Every time you open Instagram out of boredom, the neural pathway linking boredom → phone → Instagram gets stronger. The behavior becomes more automatic over time, not less, without deliberate interruption.
- DeltaFosB is a transcription factor that accumulates in the nucleus accumbens with repeated rewarding behavior. Unlike other neurological markers it does not clear quickly — it persists for weeks. Its presence is a molecular signature of behavioral compulsion and is found in both substance addiction and behavioral addiction research.

**Dopamine and delayed gratification**
- Dopamine is not a pleasure chemical. It is an anticipation chemical — it peaks before the reward, not during it. This is why scrolling feels compelling even when the content is not good.
- Dopamine from completing something difficult — finishing a project, learning a skill, solving a problem — persists for hours and is associated with mood stability. Dopamine from a notification peaks in seconds and drops below baseline when it fades.
- The brain's reward system does not distinguish between a "like" on a photo and a piece of food. Both trigger the same pathway. The apps are feeding you in very small, unpredictable portions on purpose.
- Deliberately delaying a reward — waiting before checking a message, finishing a task before a break — strengthens the prefrontal cortex's ability to override impulse. The friction in this app is practice, not punishment.

**Epistemic honesty — what the research actually says**

*These tidbits exist because users who have encountered counter-arguments will dismiss the entire content library if it pretends those arguments don't exist. Acknowledging genuine uncertainty is not weakness — it is what separates honest information from propaganda.*

- The classification of compulsive social media use as a clinical "addiction" is genuinely contested in academic literature. Some researchers argue it does not meet the diagnostic threshold. What is not contested: that many users experience loss of control, escalation of use, and impaired functioning they did not choose. Whether to call it addiction is a definitional question. Whether it is a problem is not.
- Several large studies claiming to show no meaningful harm from social media use were conducted in partnership with, or funded by, social media companies. This does not automatically make them wrong. It is worth knowing when evaluating the weight of the evidence.
- Studies on social media and mental health often rely on self-reported screen time, which users consistently underestimate by 30–50%. Research using objective usage data tends to find stronger effects than research using self-reports.
- The research on social media and adolescent mental health is stronger and more consistent than the research on adult mental health effects. "The evidence is mixed" is more accurate for adults; for teenagers it is less so.

**Reality check — what you are actually looking at**

- The average height of a man in Western Europe is 5'10". Fewer than 15% of men are 6 feet or taller. The male body presented as normal on Instagram and in pornography requires a height the overwhelming majority of men will never have, regardless of anything they do.
- The median individual income in most Western countries is between $30,000 and $45,000 per year. The lifestyle presented as normal on social media — the travel, the apartment, the clothing — requires multiples of that, typically sustained by either generational wealth, debt, or a career built around performing the lifestyle for an audience.
- Social media algorithms show you people selected for visual impact, at their most prepared, in their best light, filtered, retouched, and often surgically altered. Your reference point for what an ordinary attractive person looks like has been recalibrated by years of exposure to the top fraction of a percent. This affects how you see yourself and how you see everyone you meet in person.
- The beauty standards presented to women on social media require a combination of genetics, money, time, professional assistance, and image editing that is not available to most people and is not honestly disclosed. The body you are comparing yourself to often does not exist outside of that image.
- This applies symmetrically. The male physique presented as attainable through fitness content frequently involves pharmacological assistance that is not mentioned. The female lifestyle content frequently involves financial arrangements that are not disclosed. Both are a performance with a hidden budget.

**Gender conflict and algorithmic radicalization**

*These tidbits point at the mechanism — how platforms profit from gender antagonism — without endorsing either direction of it. The goal is to make the machinery visible, not to adjudicate who is right.*

- Content that makes men angry at women and content that makes women angry at men generate identical engagement signals. Outrage about the other sex travels faster than almost any other content category on every major platform. The platform is paid the same either way. The conflict is the product.
- The algorithm does not show you representative men or representative women. It shows you the most extreme, most provocative examples because those generate the most reaction. The men being presented to women as typical of men are not typical. The women being presented to men as typical of women are not typical. Both groups are being shown a caricature of the other and forming policy positions about real people based on it.
- "Men are trash" content and "women are inferior" content are not opposites fighting each other. They are the same business model running in parallel, each making the other's audience more extreme, each generating revenue for the same companies.
- The gap in trust between men and women, particularly among younger cohorts, has grown significantly over the past decade. This coincides exactly with the period of social media maturity. Researchers studying the data note the correlation. The platforms note the engagement.
- A person who genuinely hates the other sex is a committed, emotional, high-engagement user. Platforms do not cause this outcome, but they do profit from it and they do optimize toward it.

**Sleep and late-night use**
- Blue light from screens suppresses melatonin production and delays sleep onset by up to 90 minutes. This is not a metaphor — it is a measured hormonal effect. The apps you use before bed are literally borrowing time from your sleep.
- Social media companies know their peak engagement hours. Evening and late-night sessions are the most monetizable. The content served to you at 23:00 is optimized to keep you up, not to wrap things up.
- Sleep deprivation impairs the prefrontal cortex — the same region responsible for impulse control and the ability to resist opening the app in the first place. Late-night use makes the next day's use harder to resist.

**Adult content track — neuroscience of pornography**

*This content track is shown only on browser adult domain intent gates and end-cards. Tone is identical to all other tracks: factual, specific, no moralizing.*

- The Coolidge effect is a real neurological phenomenon: sexual arousal is dampened by the same stimulus but restored by novelty. Internet pornography provides infinite novelty at zero effort — a stimulus the brain did not evolve to encounter and has no natural defense against.
- Pornography is a superstimulus — a stimulus that exceeds the parameters the human reward system evolved to respond to. Like food engineered to hit fat, salt, and sugar simultaneously, it triggers a response far stronger than anything it evolved alongside. The brain is not built to regulate a superstimulus; it is built to pursue one.
- Food has a stop signal: you become full. Alcohol has a stop signal: you pass out or feel sick. Pornography has no equivalent physiological stop signal. Satiety from a meal is a feature; there is no equivalent architecture for visual sexual stimulation. Continued use is the path of least resistance by design.
- DeltaFosB accumulates in the brain's reward circuitry with repeated pornography use, just as it does with other compulsive behaviors. Its presence persists for weeks after behavior stops. It drives the sensation of craving even when conscious motivation to stop is present.
- PIED (porn-induced erectile dysfunction) is a documented clinical phenomenon in men without other risk factors, including young men. The mechanism is desensitization: repeated exposure to superstimuli reduces the reward system's sensitivity to normal stimuli. Real sexual partners are less stimulating than an infinite novelty feed.
- The escalation pattern in pornography consumption is the same mechanism as tolerance in substance use: the brain habituates to a stimulus and requires novelty to generate the same response. Content that was once sufficient stops being sufficient. This is not a preference developing; it is a neurological response to repeated overstimulation. What the end point looks like is different for everyone; that the direction of travel exists is not.
- The prefrontal cortex normally applies a brake to impulse. Chronic hyperstimulation of the reward system impairs this brake — researchers call it hypofrontality. This is why people describe watching pornography feeling compulsive even when they consciously want to stop.

*Counter-research honesty — same principle as the social media track:*
- Whether compulsive pornography use meets the clinical definition of addiction is genuinely debated. Some researchers, including those behind the ICD-11 classification of "compulsive sexual behaviour disorder," chose a framing that sidesteps the addiction label. The subjective experience of loss of control, escalation, and impaired real-world relationships is not debated. The label is.
- Some studies find no causal link between pornography use and erectile dysfunction in laboratory settings. These studies typically measure acute arousal response, not long-term habituation. Users reporting PIED describe a pattern that develops over years, not a response measurable in a one-hour experiment. The methodological gap is real and worth knowing about.
- Much of the research on pornography effects has been conducted on male subjects. This is a genuine gap in the literature, not a settled question about female users.

*Effects specific to women:*
- The majority of mainstream pornography is produced for a presumed male viewer and depicts sex from a perspective, and with a script, that prioritizes male pleasure. Women who use this as a reference point for what sex is supposed to look like are learning from a document written for someone else.
- Research into compulsive pornography use in women is substantially less developed than research on male users, but the studies that exist show similar escalation patterns, similar desensitization effects, and similar reports of impaired real-world sexual satisfaction.
- Pornography shapes the sexual expectations that male partners bring into relationships, regardless of whether women themselves watch it. Women are affected by their partners' pornography use whether or not they are users themselves. This indirect effect is largely absent from public discussion of the topic.
- The body standards in mainstream pornography for female performers involve surgery, grooming norms, and physical configurations that are not representative of the range of real bodies. This affects female viewers' relationship with their own bodies and male viewers' expectations of real partners — simultaneously, in opposite directions, from the same content.

**Social validation engineering**
- The "like" was originally called the "awesome" button at Facebook. It was renamed because "like" is more ambiguous — people give likes to things they don't endorse, which increases the signal volume and keeps you checking.
- Instagram shows you who viewed your story in a specific order, optimized to keep you checking back to see if a particular person has viewed it yet.
- Snapchat streaks have no value. They are an artificial social obligation — a daily compulsory action dressed as a friendship feature.

---

## Appendix: offline activity prompts (draft)

Shown on the end-card and cool-down lockout screen. One is displayed at a time; the user can cycle. Categories balance across the day. All prompts are translatable strings.

**Movement**
- Take a walk around the block.
- Stand up and stretch for 30 seconds.
- Do 10 slow squats.
- Walk to the kitchen and drink a glass of water.
- Step outside for 2 minutes. Just stand there.
- Walk to a different room for no reason.

**Space**
- Tidy the coffee table.
- Put one thing back where it belongs.
- Throw away one piece of rubbish you've been ignoring.
- Make your bed (or straighten it).
- Wipe the nearest surface.
- Put your phone face-down on a different surface than where you usually leave it.

**Sensory**
- Look out the window. Name three things you can see.
- Close your eyes and name five things you can hear right now.
- Open a window and notice what the air smells like.
- Make a cup of something hot and hold it with both hands.
- Notice the temperature of the room. Is it comfortable?

**Social analog (no screen)**
- Think of one person you haven't talked to in a while. You don't have to contact them — just think of them.
- Write one sentence by hand. Anything.
- If someone else is nearby, say something to them that isn't about logistics.

**Micro-creative**
- Pick up a book — any book — and read one page.
- Draw something badly. It doesn't matter what.
- Hum or whistle something.
- Rearrange one object on a shelf until it looks better to you.
