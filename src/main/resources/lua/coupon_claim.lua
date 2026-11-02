-- KEYS[1]: 优惠券配额 Key (coupon:quota:{templateId})
-- KEYS[2]: 用户领券防重 Set Key (coupon:users:{templateId})
-- ARGV[1]: 用户 ID (userId)

-- 1. 检查用户是否已领过该券，每人限领 1 张，防止羊毛党脚本
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -1
end

-- 2. 检查优惠券配额是否充足
local quota = tonumber(redis.call('GET', KEYS[1]) or '0')
if quota <= 0 then
    return 0
end

-- 3. 原子扣减配额并记录用户
redis.call('DECRBY', KEYS[1], 1)
redis.call('SADD', KEYS[2], ARGV[1])

return 1
