-- KEYS[1]: 用户秒杀令牌 Key
-- KEYS[2]: 秒杀令牌大闸 Key
-- ARGV[1]: 本次请求生成的候选 token
-- ARGV[2]: 用户秒杀令牌过期时间（秒）
-- 返回值格式：code|token|expireSeconds
-- code = 1：新 token 签发成功
-- code = 2：用户已有未过期 token，直接返回旧 token
-- code = 0：大闸名额不足
-- code = -1：大闸 Key 不存在

-- 1. 先判断当前用户是否已经有未过期的秒杀令牌
local cachedToken = redis.call('GET', KEYS[1])
if cachedToken then
    -- 查询剩余 TTL
    local ttl = redis.call('TTL', KEYS[1])
    -- .. 是字符串拼接符，这里拼出：2|旧token|剩余过期秒数
    return '2|' .. cachedToken .. '|' .. ttl
end

-- 2. 用户没有令牌时，再检查活动商品维度的大闸名额
local tokenGateCount = redis.call('GET', KEYS[2])
if not tokenGateCount then
    return '-1||0'
end

-- 3. 大闸名额已经发完，拒绝继续签发新的用户级令牌
if tonumber(tokenGateCount) <= 0 then
    return '0||0'
end

-- 4. 扣减 1 个大闸名额
redis.call('DECR', KEYS[2])

-- 5. 写入当前用户的秒杀令牌
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])

return '1|' .. ARGV[1] .. '|' .. ARGV[2]
