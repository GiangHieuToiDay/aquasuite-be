-- Redis Lua script: revoke-session.lua (V2)
-- KEYS[1]: auth:v2:session:{sid}
-- KEYS[2]: auth:v2:session-revoked:{sid}
-- KEYS[3]: auth:v2:user-sessions:{userId}
-- ARGV[1]: userId
-- ARGV[2]: sid
-- ARGV[3]: reason ("LOGOUT" or "REPLAY")
-- ARGV[4]: nowEpochMs
-- ARGV[5]: defaultTtlSeconds (e.g. 1296000)
-- ARGV[6]: expectedGeneration (2)

local sessionKey = KEYS[1]
local revokedKey = KEYS[2]
local userSessionsKey = KEYS[3]

local userId = ARGV[1]
local sid = ARGV[2]
local reason = ARGV[3] or "LOGOUT"
local nowEpochMs = tonumber(ARGV[4])
local defaultTtlSeconds = tonumber(ARGV[5]) or 1296000
local expectedGen = tonumber(ARGV[6]) or 2

local redisTime = redis.call('TIME')
local redisNowMs = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)

-- 1. If revoked marker already exists, ensure sessionKey is deleted and return SUCCESS (idempotent logout)
if redis.call('EXISTS', revokedKey) == 1 then
    if redis.call('EXISTS', sessionKey) == 1 then
        redis.call('DEL', sessionKey)
    end
    redis.call('SREM', userSessionsKey, sid)
    return cjson.encode({ status = "SUCCESS" })
end

-- 2. If session key exists, validate and revoke
if redis.call('EXISTS', sessionKey) == 1 then
    local sessionType = redis.call('TYPE', sessionKey)
    if not sessionType or sessionType['ok'] ~= 'string' then
        return cjson.encode({ status = "INCONSISTENT" })
    end

    local sessionRaw = redis.call('GET', sessionKey)
    if not sessionRaw then
        return cjson.encode({ status = "INCONSISTENT" })
    end

    local sOk, session = pcall(cjson.decode, sessionRaw)
    if not sOk or type(session) ~= 'table' then
        return cjson.encode({ status = "INCONSISTENT" })
    end

    if session.userId ~= userId then
        return cjson.encode({ status = "INCONSISTENT" })
    end

    local absExpMs = tonumber(session.absoluteExpiresAt)
    local currentPttl = redis.call('PTTL', sessionKey)
    local tombstonePttl = defaultTtlSeconds * 1000
    if absExpMs and absExpMs > redisNowMs then
        local remMs = absExpMs - redisNowMs
        tombstonePttl = math.min(remMs, tombstonePttl)
    end
    if currentPttl > 0 then
        tombstonePttl = math.min(currentPttl, tombstonePttl)
    end
    if tombstonePttl <= 0 then
        tombstonePttl = 1000
    end

    local revokedData = {
        schemaVersion = 2,
        authGeneration = expectedGen,
        userId = userId,
        status = "REVOKED",
        reason = reason,
        absoluteExpiresAt = session.absoluteExpiresAt
    }
    -- Write revoked tombstone first
    redis.call('SET', revokedKey, cjson.encode(revokedData), 'PX', tombstonePttl)
    -- Delete session
    redis.call('DEL', sessionKey)
    redis.call('SREM', userSessionsKey, sid)

    return cjson.encode({ status = "SUCCESS" })
end

-- 3. If neither session nor revoked exists
-- Invariant: Session missing and tombstone missing after authentication
return cjson.encode({ status = "UNKNOWN_SESSION" })
