local slots_key = KEYS[1]
local reservation_key = KEYS[2]
local token = ARGV[1]
local ttl_ms = tonumber(ARGV[2])

local count = redis.call('GET', slots_key)
if count and tonumber(count) > 0 then
    redis.call('DECR', slots_key)
    redis.call('SET', reservation_key, token)
    if ttl_ms > 0 then
        redis.call('PEXPIRE', reservation_key, ttl_ms)
    end
    return 1
else
    return -1
end
