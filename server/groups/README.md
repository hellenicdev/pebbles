# Pebbles groups

Cloudflare Worker and D1 database for group invites. It does not store touches, Wi-Fi, or emails.

The public API is `https://api.hellenicdev.eu/v1`. A phone sends `Authorization: Bearer` plus a random 64-character hex token that stays on the device. The worker stores only the SHA-256 of that token.

- `POST /v1/me` saves your name and colour, and returns the group.
- `POST /v1/invites` creates a 7-day invite for the caller's group. The first invite also creates the group.
- `DELETE /v1/invites/<code>` revokes an invite. Only the person who started the group can do this.
- `POST /v1/invites/<code>/accept` joins that one group, if it is still open and has fewer than five people.
- `GET /v1/invites/<code>` says whether an invite is still open. It does not list names.

Invite links look like `https://hellenicdev.eu/pebbles/join/<code>`.

## Create the database

This needs a Cloudflare API token that can edit Workers and D1 for the `hellenicdev.eu` account. Do not commit the token.

```sh
cd server/groups
npm ci
npx wrangler login
npx wrangler d1 create pebbles-groups
```

Copy the printed `database_id` into `wrangler.toml`, replacing the all-zero id. Then:

```sh
npx wrangler d1 migrations apply pebbles-groups --remote
npx wrangler deploy
```

The GitHub workflow `.github/workflows/deploy-groups.yml` does the same apply and deploy on `main` when `CLOUDFLARE_API_TOKEN` is set and `database_id` is no longer all zeroes. The token is the same secret the waitlist deploy uses.
