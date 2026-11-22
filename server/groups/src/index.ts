import {
  inviteExpiry,
  inviteIsOpen,
  joinUrl,
  MAX_MEMBERS,
  randomCode,
  sha256Hex,
  validateColourName,
  validateName,
  validToken,
} from "./logic";

export interface Env {
  DB: D1Database;
}

type MemberRow = {
  id: string;
  name: string;
  colour_name: string;
  colour: number;
};

type InviteRow = {
  code: string;
  group_id: string;
  expires_at: number;
  revoked_at: number | null;
};

const WINDOW_MS = 60_000;
const MAX_HITS = 40;
const hits = new Map<string, number[]>();

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";
    if (!path.startsWith("/v1")) return json({ ok: false, error: "Not found." }, 404);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors() });

    const ip = request.headers.get("cf-connecting-ip") ?? "unknown";
    if (rateLimited(ip)) {
      return json({ ok: false, error: "Too many requests. Please wait a minute and try again." }, 429);
    }

    try {
      if (request.method === "GET" && path.startsWith("/v1/invites/")) {
        return previewInvite(env, path.slice("/v1/invites/".length));
      }
      const token = bearer(request);
      if (!token) return json({ ok: false, error: "Missing token." }, 401);
      const member = await memberByToken(env, token);

      if (request.method === "POST" && path === "/v1/me") {
        return saveMe(request, env, token, member);
      }
      if (!member) return json({ ok: false, error: "Unknown token." }, 401);
      if (request.method === "GET" && path === "/v1/group") return json(await groupPayload(env, member), 200);
      if (request.method === "POST" && path === "/v1/invites") return createInvite(env, member);
      if (request.method === "DELETE" && path.startsWith("/v1/invites/")) {
        return revokeInvite(env, member, path.slice("/v1/invites/".length));
      }
      if (request.method === "POST" && /^\/v1\/invites\/[^/]+\/accept$/.test(path)) {
        const code = path.slice("/v1/invites/".length, -"/accept".length);
        return acceptInvite(env, member, code);
      }
      return json({ ok: false, error: "Not found." }, 404);
    } catch (error) {
      console.error("groups", error instanceof Error ? error.name : "error");
      return json({ ok: false, error: "The group service failed." }, 500);
    }
  },
} satisfies ExportedHandler<Env>;

async function saveMe(request: Request, env: Env, token: string, existing: MemberRow | null): Promise<Response> {
  const body = await readJson(request);
  if (!body) return json({ ok: false, error: "Please send your name and colour." }, 400);
  const name = validateName(body.name);
  const colourName = validateColourName(body.colourName);
  const colour = typeof body.colour === "number" && Number.isFinite(body.colour) ? Math.trunc(body.colour) : null;
  if (!name || !colourName || colour == null) {
    return json({ ok: false, error: "Please send your name and colour." }, 400);
  }
  const now = Date.now();
  const hash = await sha256Hex(token);
  if (!existing) {
    const id = crypto.randomUUID();
    await env.DB.prepare(
      "INSERT INTO members (id, token_hash, name, colour_name, colour, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
    ).bind(id, hash, name, colourName, colour, now, now).run();
  } else {
    await env.DB.prepare(
      "UPDATE members SET name = ?, colour_name = ?, colour = ?, updated_at = ? WHERE id = ?",
    ).bind(name, colourName, colour, now, existing.id).run();
  }
  const saved = await memberByToken(env, token);
  if (!saved) return json({ ok: false, error: "The group service failed." }, 500);
  return json({ ok: true, ...await groupPayload(env, saved) }, 200);
}

async function createInvite(env: Env, member: MemberRow): Promise<Response> {
  let groupId = await groupIdFor(env, member.id);
  const now = Date.now();
  if (!groupId) {
    groupId = crypto.randomUUID();
    await env.DB.batch([
      env.DB.prepare("INSERT INTO groups (id, owner_id, created_at) VALUES (?, ?, ?)").bind(groupId, member.id, now),
      env.DB.prepare("INSERT INTO memberships (group_id, member_id, joined_at) VALUES (?, ?, ?)").bind(groupId, member.id, now),
    ]);
  }
  const count = await memberCount(env, groupId);
  if (count >= MAX_MEMBERS) return json({ ok: false, error: "This group is full." }, 409);
  const owner = await ownerId(env, groupId);
  if (owner !== member.id) return json({ ok: false, error: "Only the person who started the group can invite." }, 403);
  const bytes = new Uint8Array(10);
  crypto.getRandomValues(bytes);
  const code = randomCode(bytes);
  const expires = inviteExpiry(now);
  await env.DB.prepare(
    "INSERT INTO invites (code, group_id, created_by, created_at, expires_at, revoked_at) VALUES (?, ?, ?, ?, ?, NULL)",
  ).bind(code, groupId, member.id, now, expires).run();
  return json({ ok: true, code, url: joinUrl(code), expiresAt: expires }, 201);
}

async function revokeInvite(env: Env, member: MemberRow, code: string): Promise<Response> {
  const invite = await loadInvite(env, code);
  if (!invite) return json({ ok: false, error: "Invite not found." }, 404);
  const owner = await ownerId(env, invite.group_id);
  if (owner !== member.id) return json({ ok: false, error: "Only the person who started the group can revoke an invite." }, 403);
  await env.DB.prepare("UPDATE invites SET revoked_at = ? WHERE code = ?").bind(Date.now(), code).run();
  return json({ ok: true }, 200);
}

async function acceptInvite(env: Env, member: MemberRow, code: string): Promise<Response> {
  const invite = await loadInvite(env, code);
  if (!invite || !inviteIsOpen(invite.expires_at, invite.revoked_at, Date.now())) {
    return json({ ok: false, error: "This invite has expired." }, 410);
  }
  const current = await groupIdFor(env, member.id);
  if (current && current !== invite.group_id) {
    return json({ ok: false, error: "You're already in a group." }, 409);
  }
  if (!current) {
    const count = await memberCount(env, invite.group_id);
    if (count >= MAX_MEMBERS) return json({ ok: false, error: "This group is full." }, 409);
    await env.DB.prepare(
      "INSERT INTO memberships (group_id, member_id, joined_at) VALUES (?, ?, ?)",
    ).bind(invite.group_id, member.id, Date.now()).run();
  }
  return json({ ok: true, ...await groupPayload(env, member) }, 200);
}

async function previewInvite(env: Env, code: string): Promise<Response> {
  const invite = await loadInvite(env, code);
  if (!invite || !inviteIsOpen(invite.expires_at, invite.revoked_at, Date.now())) {
    return json({ ok: false, error: "This invite has expired." }, 410);
  }
  return json({
    ok: true,
    expiresAt: invite.expires_at,
    memberCount: await memberCount(env, invite.group_id),
  }, 200);
}

async function groupPayload(env: Env, member: MemberRow) {
  const groupId = await groupIdFor(env, member.id);
  if (!groupId) return { member: publicMember(member, true), group: null };
  const owner = await ownerId(env, groupId);
  const members = await env.DB.prepare(
    `SELECT m.id, m.name, m.colour_name, m.colour
     FROM memberships ms JOIN members m ON m.id = ms.member_id
     WHERE ms.group_id = ? ORDER BY ms.joined_at ASC`,
  ).bind(groupId).all<MemberRow>();
  const now = Date.now();
  const invites = await env.DB.prepare(
    "SELECT code, expires_at, revoked_at FROM invites WHERE group_id = ? ORDER BY created_at DESC",
  ).bind(groupId).all<{ code: string; expires_at: number; revoked_at: number | null }>();
  return {
    member: publicMember(member, true),
    group: {
      id: groupId,
      youAreOwner: owner === member.id,
      members: (members.results ?? []).map((row) => publicMember(row, row.id === member.id)),
      invites: (invites.results ?? [])
        .filter((row) => inviteIsOpen(row.expires_at, row.revoked_at, now))
        .map((row) => ({ code: row.code, url: joinUrl(row.code), expiresAt: row.expires_at })),
    },
  };
}

function publicMember(row: MemberRow, you: boolean) {
  return { id: row.id, name: row.name, colourName: row.colour_name, colour: row.colour, you };
}

async function memberByToken(env: Env, token: string): Promise<MemberRow | null> {
  const hash = await sha256Hex(token);
  return env.DB.prepare(
    "SELECT id, name, colour_name, colour FROM members WHERE token_hash = ?",
  ).bind(hash).first<MemberRow>();
}

async function groupIdFor(env: Env, memberId: string): Promise<string | null> {
  const row = await env.DB.prepare(
    "SELECT group_id FROM memberships WHERE member_id = ?",
  ).bind(memberId).first<{ group_id: string }>();
  return row?.group_id ?? null;
}

async function ownerId(env: Env, groupId: string): Promise<string | null> {
  const row = await env.DB.prepare("SELECT owner_id FROM groups WHERE id = ?").bind(groupId).first<{ owner_id: string }>();
  return row?.owner_id ?? null;
}

async function memberCount(env: Env, groupId: string): Promise<number> {
  const row = await env.DB.prepare(
    "SELECT COUNT(*) AS n FROM memberships WHERE group_id = ?",
  ).bind(groupId).first<{ n: number }>();
  return row?.n ?? 0;
}

async function loadInvite(env: Env, code: string): Promise<InviteRow | null> {
  if (!/^[a-z0-9]{10}$/.test(code)) return null;
  return env.DB.prepare(
    "SELECT code, group_id, expires_at, revoked_at FROM invites WHERE code = ?",
  ).bind(code).first<InviteRow>();
}

function bearer(request: Request): string | null {
  const header = request.headers.get("authorization") ?? "";
  const match = /^Bearer\s+([a-f0-9]{64})$/.exec(header);
  return match && validToken(match[1]) ? match[1] : null;
}

async function readJson(request: Request): Promise<Record<string, unknown> | null> {
  const raw = await request.text();
  if (raw.length > 4000) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return null;
    return parsed as Record<string, unknown>;
  } catch {
    return null;
  }
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
  return false;
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...cors() },
  });
}

function cors(): Record<string, string> {
  return {
    "access-control-allow-origin": "https://hellenicdev.eu",
    "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
    "access-control-allow-headers": "authorization, content-type",
    "cache-control": "no-store",
  };
}
