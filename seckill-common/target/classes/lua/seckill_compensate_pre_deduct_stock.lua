-- KEYS[1]: 秒杀库存 Key
-- KEYS[2]: 用户购买标记 Key
-- KEYS[3]: 秒杀商品售罄标记 Key
-- ARGV[1]: 当前需要回补的订单号
-- 返回值：1-回补成功，0-用户购买标记不存在或不属于当前订单，-1-库存 Key 不存在

-- 1. 读取用户购买标记
local markedOrderNo = redis.call('GET', KEYS[2])

-- 2. 如果购买标记不存在，说明这笔预扣已经被其他回调回补过，不再重复加库存
if not markedOrderNo then
    return 0
end

-- 3. 如果标记中存储的 orderNo，和当前待回补 orderNo 不一致，说明用户可能已经重新参与，并产生了新的预扣
--    此时，旧回调不能删除新标记，也不能把新预扣的库存加回去
if markedOrderNo ~= ARGV[1] then
    return 0
end

-- 4. 库存 Key 不存在时，不能直接 INCR，避免创建一个没有过期时间的库存 Key
if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1
end

-- 5. 删除当前订单对应的用户购买标记，用户可以重新发起秒杀请求
redis.call('DEL', KEYS[2])

-- 6. 归还 Redis 中已经预扣的库存
redis.call('INCR', KEYS[1])

-- 7. 库存已经回补，清理售罄标记
redis.call('DEL', KEYS[3])

return 1
