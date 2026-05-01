local count = redis.call('GET', KEYS[1])
if count and tonumber(count) > 0 then
    redis.call('DECR', KEYS[1])
    return 1
else
    return -1
end
