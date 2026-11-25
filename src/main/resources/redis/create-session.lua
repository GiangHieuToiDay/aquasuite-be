local sessionKey = KEYS[1]
local userSessionsKey = KEYS[2]
local value = ARGV[1]
local pttl = tonumber(ARGV[2])
local sessionId = ARGV[3]
if not sessionKey or not userSessionsKey or not value or not pttl or pttl <= 0 then
    return cjson.encode({ status = 'INCONSISTENT' })
end
if redis.call('EXISTS', sessionKey) == 1 then
    return cjson.encode({ status = 'CONFLICT' })
end
local setRes = redis.call('SET', sessionKey, value, 'PX', pttl, 'NX')
if not setRes then
    return cjson.encode({ status = 'CONFLICT' })
end
redis.call('SADD', userSessionsKey, sessionId)
local trackedTtl = redis.call('PTTL', userSessionsKey)
if trackedTtl < pttl then
    redis.call('PEXPIRE', userSessionsKey, pttl)
end
return cjson.encode({ status = 'SUCCESS' })
