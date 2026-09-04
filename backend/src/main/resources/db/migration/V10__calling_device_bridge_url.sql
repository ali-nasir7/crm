-- V10: Per-device bridge routing.
-- Each CallingDevice stores the URL of the bridge app that can dial THIS phone,
-- announced by the bridge itself via the shared-token heartbeat. This is what makes
-- "call with MY phone" genuinely per-user: rep A's call goes to rep A's bridge,
-- never to somebody else's. crm.bridge.base-url remains the global fallback.
ALTER TABLE calling_devices ADD COLUMN bridge_url VARCHAR(500);
