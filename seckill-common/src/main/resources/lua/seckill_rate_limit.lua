-- KEYS[1]: 限流 Key
-- ARGV[1]: 窗口内允许的最大请求次数
-- ARGV[2]: 窗口时间，单位：秒
-- 返回值：1-允许通过，0-超过阈值，拒绝请求

-- 当前窗口请求次数加 1
local current = redis.call('INCR', KEYS[1])

-- 第一次访问时，给限流 Key 设置过期时间，形成一个固定时间窗口
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end

-- 如果当前窗口内的请求次数超过岗位，直接拒绝
if current > tonumber(ARGV[1]) then
    return 0
end

return 1