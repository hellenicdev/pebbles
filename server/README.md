# Server

Backend for Pebbles.

`waitlist/` is a Cloudflare Worker. It accepts a waitlist email and adds the address to a MailerLite group. See `waitlist/README.md` for configuration. It does not store touches.

`groups/` is a Cloudflare Worker with D1. It creates invite links and lists who joined. See `groups/README.md`. It does not store touches, Wi-Fi, or emails.
