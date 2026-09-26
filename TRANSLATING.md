# Translating Defang

Translations live on Weblate: https://hosted.weblate.org/engage/defang/

No Git needed. Weblate opens pull requests against this repo; the maintainer merges them.

## Components

| Component | File | What it is |
|---|---|---|
| **App** | `app/src/main/res/values/strings.xml` | UI: settings, dialogs, onboarding, gate, usage report |
| **Awareness** | `app/src/main/res/values/strings_awareness.xml` | The facts shown at the gate, end card and cool-down, plus offline task suggestions |

Start with **App** if you want the app usable in your language quickly. **Awareness** is where tone matters most.

## Tone

Defang doesn't scold, cheer or coach. It states how the apps work and leaves the choice to the user.

- **Flat and factual.** Drop anything that reads as moralising. No exclamation marks.
- **Blunt beats polite.** If English says "The apps are feeding you in very small, unpredictable portions on purpose", don't soften "on purpose".
- **Short.** The gate ladder lines (`tidbits_*_lvl0` … `lvl11`) are one sentence each and must fit on the gate screen. They run mildest (lvl0) to harshest (lvl11). Keep that gradient.
- **Natural beats literal.** Idioms like "speed bump" or "open loop" should use whatever your language says naturally, not a word-for-word copy.

## Hard rules

- **Numbered strings are one fact each.** Awareness texts are keyed `tidbits_general_0`, `tidbits_general_1`, … Translate each key as the same fact as the English one; never move content between numbers. The tidbits are matched to untranslated citations by number, so `tidbits_general_7` in your language must be the same fact as in English, or the Settings library shows the wrong source. An untranslated key falls back to English.
- **Placeholders stay intact.** `%1$s`, `%1$d`, `%2$02d` and `%%` must appear in the translation. You can reorder them (`%2$s … %1$s`).
- **`\n` is a line break.** Keep paragraph breaks where English has them.
- **Don't translate** "Defang", app names (Instagram, TikTok…) or URLs.
- Untranslatable strings (formats, citations) are hidden from Weblate already.

## Adding a new language

On the Weblate project page, pick **Start new translation**. Once a language is reasonably complete (App fully, Awareness at least started), it's merged and shows up in Android 13+'s per-app language setting (Settings ▸ Apps ▸ Defang ▸ Language).

Questions or wording doubts: comment on the string in Weblate, or open an issue.
