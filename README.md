# Pebbles

Pebbles is a pocket-sized link between people who love each other: one touch on your Pebble lights up and warms theirs, wherever in the world they are.

A group holds up to five Pebbles. A tap means “thinking of you”. A long hold is a hug. Their Pebble glows in the sender’s colour, warms gently (a heater, capped around 42°C), and answers with a haptic buzz and a soft chime. A touch back means “got it”. Quiet hours keep it still. It charges on a wireless dock with an LED ring. Touches are end-to-end encrypted and are not stored. Firmware, the Android app, and the server are open source (MIT).

Kickstarter is planned for 1 April 2027. The public site is [https://hellenicdev.eu/pebbles/](https://hellenicdev.eu/pebbles/).

## Folder map

| Path | What it is |
| --- | --- |
| `firmware/` | Device firmware. Not in this milestone. |
| `app/` | Android app. Not in this milestone. |
| `server/` | Backend. `server/waitlist/` is the Cloudflare Worker for the waitlist. |
| `website/` | Static site deployed to GitHub Pages. |
| `.github/workflows/` | Pages deploy and waitlist Worker deploy. |

## Licence

MIT. Copyright 2026 HellenicDev. See [LICENSE](LICENSE).
