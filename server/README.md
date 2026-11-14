# Server

Backend for Pebbles.

`waitlist/` is a Cloudflare Worker. It accepts a waitlist email and adds the address to a MailerLite group. See `waitlist/README.md` for configuration. It does not store touches.
