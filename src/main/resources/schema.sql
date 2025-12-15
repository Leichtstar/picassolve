-- reset tables
-- DROP TABLE IF EXISTS users CASCADE;
-- DROP TABLE IF EXISTS words CASCADE;

-- users table
CREATE TABLE IF NOT EXISTS users (
    id    UUID PRIMARY KEY,
    name  VARCHAR(50)  NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    team  INTEGER      NOT NULL DEFAULT 0,
    score INTEGER      NOT NULL DEFAULT 0,
    role  VARCHAR(16)  NOT NULL CHECK (role IN ('PARTICIPANT','DRAWER','ADMIN'))
);

-- words table
CREATE TABLE IF NOT EXISTS words (
    id   BIGSERIAL PRIMARY KEY,
    text VARCHAR(100) NOT NULL UNIQUE
);

-- score snapshots table
CREATE TABLE IF NOT EXISTS score_snapshots (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    username VARCHAR(50) NOT NULL,
    team INTEGER NOT NULL,
    score INTEGER NOT NULL,
    snapshot_date DATE NOT NULL,
    period VARCHAR(16) NOT NULL CHECK (period IN ('DAILY','WEEKLY','MONTHLY')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
