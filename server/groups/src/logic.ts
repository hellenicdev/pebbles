export const COLOUR_NAMES = ["Amber", "Rose", "Sage", "Dusk"] as const;
export const INVITE_MS = 7 * 24 * 60 * 60 * 1000;
export const MAX_MEMBERS = 5;
export const CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
export const JOIN_PREFIX = "https://hellenicdev.eu/pebbles/join/";

export function validateName(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const name = raw.trim().replace(/\s+/g, " ");
  if (name.length < 1 || name.length > 32) return null;
  if (/[\u0000-\u001f]/.test(name)) return null;
  return name;
}

export function validateColourName(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const found = COLOUR_NAMES.find((item) => item.toLowerCase() === raw.trim().toLowerCase());
  return found ?? null;
}

export function inviteExpiry(now: number): number {
  return now + INVITE_MS;
}

export function inviteIsOpen(expiresAt: number, revokedAt: number | null, now: number): boolean {
  return revokedAt == null && expiresAt > now;
}

export function randomCode(bytes: Uint8Array): string {
  let out = "";
  for (const byte of bytes) out += CODE_ALPHABET[byte % CODE_ALPHABET.length];
  return out;
}

export function joinUrl(code: string): string {
  return `${JOIN_PREFIX}${code}`;
}

export async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function validToken(token: string): boolean {
  return /^[a-f0-9]{64}$/.test(token);
}
