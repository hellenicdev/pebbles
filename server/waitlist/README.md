# Waitlist worker

Cloudflare Worker for `POST /waitlist`. The body is JSON: `{ "email": "person@example.com", "confirm": "" }`.

`confirm` is a honeypot. If it is not empty, the worker answers success and does not call MailerLite. A valid email is added to MailerLite group `204022254893270494` via `POST https://connect.mailerlite.com/api/subscribers`.

The same address can be submitted again. MailerLite treats that as an update and keeps the existing subscriber.

## Configuration

| Name | Where | Purpose |
| --- | --- | --- |
| `MAILERLITE_GROUP_ID` | `[vars]` in `wrangler.toml` | Group id, already set |
| `MAILERLITE_API_KEY` | Worker secret | MailerLite API token (`Authorization: Bearer`) |
| `CLOUDFLARE_API_TOKEN` | GitHub Actions secret | Used only to deploy. Not read by the Worker |

Route: `api.hellenicdev.eu/waitlist*` on zone `hellenicdev.eu`. Account `327471fb378c74033d037887e0d4cf01`.

This route does not create DNS. `api.hellenicdev.eu` needs a proxied record on the zone before requests arrive. Cloudflare can also refuse to publish the route until that record exists.

```sh
npm ci
npm run check
```

Local dev reads `MAILERLITE_API_KEY` from `.dev.vars` (gitignored). Do not commit that file.
