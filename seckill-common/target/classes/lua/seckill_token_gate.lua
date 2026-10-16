-- KEYS[1]: 秒杀令牌大闸 Key
-- 返回值：1-获取大闸令牌成功，0-大闸令牌不足，-1-大闸 Key 不存在

-- 1. 获取当前活动商品剩余的大闸令牌数
local tokenCount = redis.call('GET', KEYS[1])

-- 2. 大闸 Key 不存在，说明活动尚未预热，或者 Key 已经过期
if not tokenCount then
    return -1
end

-- 3. 大闸令牌已经发完，直接拒绝进入后续获取用户级令牌流程
if tonumber(tokenCount) <= 0 then
    return 0
end

-- 4. 大闸令牌充足，原子扣减 1 个名额
redis.call('DECR', KEYS[1])
return 1