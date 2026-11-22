import assert from "node:assert/strict";
import test from "node:test";
import {
  inviteExpiry,
  inviteIsOpen,
  joinUrl,
  randomCode,
  sha256Hex,
  validateColourName,
  validateName,
  validToken,
  INVITE_MS,
} from "./logic.ts";

test("names and colours", () => {
  assert.equal(validateName("  Ada  Lovelace "), "Ada Lovelace");
  assert.equal(validateName(""), null);
  assert.equal(validateName("x".repeat(33)), null);
  assert.equal(validateColourName("rose"), "Rose");
  assert.equal(validateColourName("Blue"), null);
});

test("invites expire after seven days and can be revoked", () => {
  const now = 1_700_000_000_000;
  assert.equal(inviteExpiry(now) - now, INVITE_MS);
  assert.equal(inviteIsOpen(now + 1000, null, now), true);
  assert.equal(inviteIsOpen(now, null, now), false);
  assert.equal(inviteIsOpen(now + 1000, now, now), false);
});

test("codes are random and single-use shaped", () => {
  const first = randomCode(new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]));
  const second = randomCode(new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8, 9, 11]));
  assert.equal(first.length, 10);
  assert.notEqual(first, second);
  assert.equal(joinUrl(first), `https://hellenicdev.eu/pebbles/join/${first}`);
});

test("tokens are hashed and checked", async () => {
  assert.equal(validToken("a".repeat(64)), true);
  assert.equal(validToken("nope"), false);
  const hash = await sha256Hex("a".repeat(64));
  assert.equal(hash.length, 64);
  assert.notEqual(hash, "a".repeat(64));
});
