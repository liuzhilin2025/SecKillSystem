-- KEYS[1]: 秒杀库存 Key
-- KEYS[2]: 用户购买标记 Key
-- ARGV[1]: 用户购买标记的过期时间（秒）
-- ARGV[2]: 活动开始时间戳（毫秒）
-- ARGV[3]: 活动结束时间戳（毫秒）
-- 返回值：1-预扣成功，0-库存售罄，-1-库存 Key 不存在
--         2-重复参与，3-活动未开始，4-活动已结束

-- 获取当前时间
local redisTime = redis.call('TIME')
-- 转毫秒
local nowMillis = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)

-- 当前时间早于活动开始时间，不允许下单
if nowMillis < tonumber(ARGV[2]) then
    return 3
end

-- 当前时间晚于活动结束时间，不允许下单
if nowMillis >= tonumber(ARGV[3]) then
    return 4
end

-- 1. 用户购买标记已存在，说明该用户已经参与过本场秒杀
if redis.call('EXISTS', KEYS[2]) == 1 then
    return 2
end

-- 2. 获取当前秒杀库存
local stock = redis.call('GET', KEYS[1])

-- 3. 库存 Key 不存在，说明库存尚未预热或 Key 已失效
if not stock then
    return -1
end

-- 4. 库存小于等于 0，直接返回售罄，不再继续扣减
if tonumber(stock) <= 0 then
    return 0
end

-- 5. 写入用户购买标记，过期时间覆盖整个秒杀活动周期
redis.call('SET', KEYS[2], '1', 'EX', ARGV[1])

-- 6. 库存充足，原子递减库存
redis.call('DECR', KEYS[1])
return 1
