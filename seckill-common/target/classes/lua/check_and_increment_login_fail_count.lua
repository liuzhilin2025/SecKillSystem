-- 原子性地检查登录失败次数并累加
-- 如果失败次数已达上限，返回 -1；
-- 如果未达上限，累加失败次数，并返回累加后的值
--
-- KEYS[1]: 登录失败计数的 Redis Key（如 login_fail_count:13800138001）
-- ARGV[1]: 登录失败次数上限（如 5）
-- ARGV[2]: 锁定时间，单位秒（如 1800，即 30 分钟）

local currentCount = tonumber(redis.call('GET', KEYS[1]) or '0')

-- 如果失败次数已达上限，直接返回 -1，不累加
if currentCount >= tonumber(ARGV[1]) then
    return -1
end

-- 累加失败次数
local newCount = redis.call('INCR', KEYS[1])

-- 首次失败时，设置过期时间（锁定窗口）
if newCount == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end

return newCount