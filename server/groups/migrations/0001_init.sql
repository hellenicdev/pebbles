CREATE TABLE members (
  id TEXT PRIMARY KEY,
  token_hash TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  colour_name TEXT NOT NULL,
  colour INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE groups (
  id TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE memberships (
  group_id TEXT NOT NULL,
  member_id TEXT NOT NULL,
  joined_at INTEGER NOT NULL,
  PRIMARY KEY (group_id, member_id)
);

CREATE INDEX memberships_member ON memberships (member_id);

CREATE TABLE invites (
  code TEXT PRIMARY KEY,
  group_id TEXT NOT NULL,
  created_by TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  revoked_at INTEGER
);

CREATE INDEX invites_group ON invites (group_id);
