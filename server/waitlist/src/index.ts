export interface Env {
  MAILERLITE_API_KEY: string;
  MAILERLITE_GROUP_ID: string;
}

const ALLOWED_ORIGIN = "https://hellenicdev.eu";
const MAILERLITE_URL = "https://connect.mailerlite.com/api/subscribers";
const WINDOW_MS = 60_000;
const MAX_HITS = 5;
const hits = new Map<string, number[]>();

const EMAIL =
  /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$/;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname !== "/waitlist" && url.pathname !== "/waitlist/") {
      return respond({ ok: false, error: "Not found." }, 404);
    }

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    if (request.method !== "POST") {
      return respond({ ok: false, error: "Method not allowed." }, 405);
    }

    const ip = request.headers.get("cf-connecting-ip") ?? "unknown";
    if (rateLimited(ip)) {
      return respond({ ok: false, error: "Too many requests. Please wait a minute and try again." }, 429, {
        "retry-after": "60",
      });
    }

    const contentType = request.headers.get("content-type") ?? "";
    if (!contentType.toLowerCase().includes("application/json")) {
      return respond({ ok: false, error: "Please enter a valid email address." }, 415);
    }

    const raw = await request.text();
    if (raw.length > 2000) {
      return respond({ ok: false, error: "Request is too large." }, 413);
    }

    let email: string;
    let honeypot: boolean;
    try {
      const parsed: unknown = JSON.parse(raw);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        return respond({ ok: false, error: "Please enter a valid email address." }, 400);
      }
      const body = parsed as { email?: unknown; confirm?: unknown };
      honeypot = typeof body.confirm === "string" && body.confirm.trim() !== "";
      if (typeof body.email !== "string") {
        return respond({ ok: false, error: "Please enter a valid email address." }, 400);
      }
      email = body.email.trim();
    } catch {
      return respond({ ok: false, error: "Please enter a valid email address." }, 400);
    }

    if (honeypot) {
      return respond({ ok: true }, 200);
    }

    if (!validEmail(email)) {
      return respond({ ok: false, error: "Please enter a valid email address." }, 400);
    }

    if (!env.MAILERLITE_API_KEY || !env.MAILERLITE_GROUP_ID) {
      console.error("Waitlist is missing MAILERLITE_API_KEY or MAILERLITE_GROUP_ID");
      return respond({ ok: false, error: "We couldn't add you just now. Please try again." }, 500);
    }

    let upstream: Response;
    try {
      upstream = await fetch(MAILERLITE_URL, {
        method: "POST",
        headers: {
          authorization: `Bearer ${env.MAILERLITE_API_KEY}`,
          "content-type": "application/json",
          accept: "application/json",
        },
        body: JSON.stringify({
          email,
          groups: [env.MAILERLITE_GROUP_ID],
        }),
        signal: AbortSignal.timeout(10_000),
      });
    } catch (error) {
      console.error("MailerLite request failed", error instanceof Error ? error.name : "error");
      return respond({ ok: false, error: "We couldn't add you just now. Please try again." }, 502);
    }

    if (upstream.status === 200 || upstream.status === 201) {
      return respond({ ok: true }, 200);
    }

    console.error("MailerLite responded", upstream.status);
    if (upstream.status === 422) {
      return respond({ ok: false, error: "Please enter a valid email address." }, 400);
    }
    if (upstream.status === 429) {
      return respond({ ok: false, error: "Too many requests. Please wait a minute and try again." }, 429);
    }
    return respond({ ok: false, error: "We couldn't add you just now. Please try again." }, 502);
  },
} satisfies ExportedHandler<Env>;

function validEmail(email: string): boolean {
  return email.length >= 3 && email.length <= 254 && EMAIL.test(email);
}

function rateLimited(ip: string): boolean {
  const now = Date.now();
  const recent = (hits.get(ip) ?? []).filter((at) => now - at < WINDOW_MS);
  if (recent.length >= MAX_HITS) {
    hits.set(ip, recent);
    return true;
  }
  recent.push(now);
  hits.set(ip, recent);
  if (hits.size > 5000) {
    const oldest = hits.keys().next().value;
    if (oldest) hits.delete(oldest);
  }
  return false;
}

function corsHeaders(): Headers {
  const headers = new Headers();
  headers.set("access-control-allow-origin", ALLOWED_ORIGIN);
  headers.set("access-control-allow-methods", "POST, OPTIONS");
  headers.set("access-control-allow-headers", "content-type");
  headers.set("access-control-max-age", "86400");
  headers.set("vary", "Origin");
  return headers;
}

function respond(body: { ok: boolean; error?: string }, status: number, extra?: Record<string, string>): Response {
  const headers = corsHeaders();
  headers.set("content-type", "application/json; charset=utf-8");
  headers.set("cache-control", "no-store");
  headers.set("x-content-type-options", "nosniff");
  if (extra) {
    for (const [key, value] of Object.entries(extra)) headers.set(key, value);
  }
  return new Response(JSON.stringify(body), { status, headers });
}
