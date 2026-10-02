-- 检查用户是否已在该场次的已购集合中，一人一单
if redis.call('SISMEMBER',KEYS[2],ARGV[1])== 1 then
    return -1
 end

--检查库存是否足够
local stock = tonumber(redis.call('GET',KEYS[1])or '0')
local count = tonumber(ARGV[2])

 if stock < count then
     return 0
 end

--原子扣减库存并记录购买用户
redis.call('DECRBY',KEYS[1],count)
redis.call('SADD',KEYS[2],ARGV[1])

return 1



