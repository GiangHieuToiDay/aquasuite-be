-- Redis Lua script: rotate-refresh.lua (V2)
-- KEYS[1]: auth:v2:session:{sid}
-- KEYS[2]: auth:v2:session-revoked:{sid}
-- KEYS[3]: auth:v2:refresh-used:{sid}:{oldJti}
-- KEYS[4]: auth:v2:refresh-used:{sid}:{newJti}
-- ARGV[1]: userId
-- ARGV[2]: sid
-- ARGV[3]: oldJti
-- ARGV[4]: newJti
-- ARGV[5]: oldExpEpochMs
-- ARGV[6]: nowEpochMs
-- ARGV[7]: usedTtlSeconds
-- ARGV[8]: expectedGeneration (2)

local sessionKey = KEYS[1]
local revokedKey = KEYS[2]
local usedOldKey = KEYS[3]
local usedNewKey = KEYS[4]

local userId = ARGV[1]
local sid = ARGV[2]
local oldJti = ARGV[3]
local newJti = ARGV[4]
local oldExpEpochMs = tonumber(ARGV[5])
local nowEpochMs = tonumber(ARGV[6])
local usedTtlSeconds = tonumber(ARGV[7]) or 1296000
local expectedGen = tonumber(ARGV[8]) or 2

-- Get current time from Redis as authoritative clock source
local redisTime = redis.call('TIME')
local redisNowMs = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)

-- 1. Check if session was revoked (valid tombstone always wins over active session)
if redis.call('EXISTS', revokedKey) == 1 then
    return cjson.encode({ status = "REVOKED" })
end

-- 2. Check if session exists
if redis.call('EXISTS', sessionKey) == 0 then
    -- Check if oldJti was marked as used -> REPLAY on already deleted/expired session
    if redis.call('EXISTS', usedOldKey) == 1 then
        local revokedData = {
            schemaVersion = 2,
            authGeneration = expectedGen,
            userId = userId,
            status = "REVOKED",
            reason = "REPLAY"
        }
        redis.call('SET', revokedKey, cjson.encode(revokedData), 'EX', usedTtlSeconds)
        return cjson.encode({ status = "REPLAY" })
    end
    return cjson.encode({ status = "UNKNOWN_TOKEN" })
end

local sessionType = redis.call('TYPE', sessionKey)
if not sessionType or sessionType['ok'] ~= 'string' then
    return cjson.encode({ status = "INCONSISTENT" })
end

local sessionRaw = redis.call('GET', sessionKey)
if not sessionRaw then
    return cjson.encode({ status = "UNKNOWN_TOKEN" })
end

local sOk, session = pcall(cjson.decode, sessionRaw)
if not sOk or type(session) ~= 'table' then
    return cjson.encode({ status = "INCONSISTENT" })
end

-- Validate schema version, generation, user ID, status
if tonumber(session.schemaVersion) ~= 2 or tonumber(session.authGeneration) ~= expectedGen then
    return cjson.encode({ status = "INCONSISTENT" })
end

if session.userId ~= userId then
    return cjson.encode({ status = "UNKNOWN_TOKEN" })
end

if session.status ~= "ACTIVE" then
    return cjson.encode({ status = "REVOKED" })
end

local absExpMs = tonumber(session.absoluteExpiresAt)
if not absExpMs or absExpMs <= redisNowMs then
    return cjson.encode({ status = "EXPIRED" })
end

local sessionPttl = redis.call('PTTL', sessionKey)
if sessionPttl <= 0 then
    return cjson.encode({ status = "EXPIRED" })
end

local remainingAbsoluteMs = absExpMs - redisNowMs
if remainingAbsoluteMs <= 0 then
    return cjson.encode({ status = "EXPIRED" })
end

-- 3. Match oldJti with currentRefreshJti
if session.currentRefreshJti == oldJti then
    if oldJti == newJti then
        return cjson.encode({ status = "INVALID" })
    end

    -- If used marker for currentJti already exists, state is inconsistent
    if redis.call('EXISTS', usedOldKey) == 1 then
        return cjson.encode({ status = "INCONSISTENT" })
    end

    -- Mark oldJti as CONSUMED
    local markerPttl = math.min(usedTtlSeconds * 1000, remainingAbsoluteMs, sessionPttl)
    if markerPttl <= 0 then
        markerPttl = remainingAbsoluteMs
    end
    local usedData = {
        schemaVersion = 2,
        authGeneration = expectedGen,
        userId = userId,
        status = "CONSUMED",
        jti = oldJti
    }
    redis.call('SET', usedOldKey, cjson.encode(usedData), 'PX', markerPttl)

    -- Update session with newJti
    session.currentRefreshJti = newJti
    redis.call('SET', sessionKey, cjson.encode(session), 'KEEPTTL')

    return cjson.encode({ status = "SUCCESS" })
else
    -- oldJti does not match currentRefreshJti
    -- Check if oldJti was already consumed -> REPLAY!
    if redis.call('EXISTS', usedOldKey) == 1 then
        -- Confirmed replay: atomically write tombstone first, then delete session
        local tombstonePttl = math.min(sessionPttl, remainingAbsoluteMs)
        if tombstonePttl <= 0 then
            tombstonePttl = 1000
        end
        local revokedData = {
            schemaVersion = 2,
            authGeneration = expectedGen,
            userId = userId,
            status = "REVOKED",
            reason = "REPLAY",
            absoluteExpiresAt = session.absoluteExpiresAt
        }
        redis.call('SET', revokedKey, cjson.encode(revokedData), 'PX', tombstonePttl)
        redis.call('DEL', sessionKey)
        return cjson.encode({ status = "REPLAY" })
    else
        return cjson.encode({ status = "UNKNOWN_TOKEN" })
    end
end
