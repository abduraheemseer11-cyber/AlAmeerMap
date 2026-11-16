-- =====================================================================
-- NetGuard Pro - Supabase schema
-- Run once in: Supabase Dashboard > SQL Editor > New query > Run
-- Every table is protected by Row Level Security: a signed-in user can
-- only read and write rows whose user_id equals their own auth.uid().
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) installations  (Module B: Installation Tracker, synced from the app)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS installations (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    network_number            TEXT NOT NULL,
    router_type               TEXT,
    router_ip                 TEXT,
    admin_username            TEXT,
    -- AES-256-GCM ciphertext produced on the device ("v1:<base64>"), never plain text.
    admin_password_encrypted  TEXT NOT NULL,
    client_name               TEXT,
    client_phone              TEXT,
    latitude                  DOUBLE PRECISION,
    longitude                 DOUBLE PRECISION,
    installation_date         BIGINT,
    notes                     TEXT,
    user_id                   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at                TIMESTAMPTZ DEFAULT now(),
    updated_at                TIMESTAMPTZ DEFAULT now()
);

-- 2) Row Level Security
ALTER TABLE installations ENABLE ROW LEVEL SECURITY;

-- 3) Policy: each user sees and changes only their own rows
CREATE POLICY "users_own_installations" ON installations
    FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- 4) Index for the per-user query used by the app
CREATE INDEX IF NOT EXISTS idx_installations_user_id ON installations(user_id);

-- Keep updated_at fresh on every update
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_installations_updated_at
    BEFORE UPDATE ON installations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- 5) speed_test_results  (prepared for optional cloud backup of tests)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS speed_test_results (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    download_mbps  REAL,
    upload_mbps    REAL,
    ping_ms        INT,
    tested_at      BIGINT,
    device_label   TEXT
);

-- 6) Row Level Security
ALTER TABLE speed_test_results ENABLE ROW LEVEL SECURITY;

-- 7) Policy
CREATE POLICY "users_own_speedtests" ON speed_test_results
    FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_speed_test_results_user_id ON speed_test_results(user_id);

-- ---------------------------------------------------------------------
-- 8) presence_profiles  (prepared for optional cloud backup of presence list)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS presence_profiles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_mac        TEXT,
    display_name      TEXT,
    role              TEXT,
    notify_on_online  BOOLEAN DEFAULT true
);

-- 9) Row Level Security
ALTER TABLE presence_profiles ENABLE ROW LEVEL SECURITY;

-- 10) Policy
CREATE POLICY "users_own_presence" ON presence_profiles
    FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_presence_profiles_user_id ON presence_profiles(user_id);
