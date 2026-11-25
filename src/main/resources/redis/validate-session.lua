local sessionKey = KEYS[1]
local revokedKey = KEYS[2]
local userId = ARGV[1]
local expectedGen = tonumber(ARGV[2]) or 2
if redis.call('EXISTS', revokedKey) == 1 then
    return cjson.encode({ status = 'REVOKED' })
end
if redis.call('EXISTS', sessionKey) == 0 then
    return cjson.encode({ status = 'NOT_FOUND' })
end
local sessionType = redis.call('TYPE', sessionKey)
if not sessionType or sessionType['ok'] ~= 'string' then
    return cjson.encode({ status = 'INCONSISTENT' })
end
local raw = redis.call('GET', sessionKey)
if not raw then
    return cjson.encode({ status = 'NOT_FOUND' })
end
local sOk, session = pcall(cjson.decode, raw)
if not sOk or type(session) ~= 'table' then
    return cjson.encode({ status = 'INCONSISTENT' })
end
if tonumber(session.schemaVersion) ~= 2 or tonumber(session.authGeneration) ~= expectedGen then
    return cjson.encode({ status = 'INCONSISTENT' })
end
if session.userId ~= userId then
    return cjson.encode({ status = 'NOT_FOUND' })
end
if session.status ~= 'ACTIVE' then
    return cjson.encode({ status = 'REVOKED' })
end
local pttl = redis.call('PTTL', sessionKey)
if pttl <= 0 then
    return cjson.encode({ status = 'EXPIRED' })
end
return cjson.encode({ status = 'ACTIVE' })
