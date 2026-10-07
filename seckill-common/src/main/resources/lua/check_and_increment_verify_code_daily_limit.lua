-- 原子性地检查每日发送次数并累加
-- 如果已达上限，返回 -1；如果未达上限，累加发送次数，并返回累加后的值
--
-- KEYS[1]: 每日发送次数的 Redis Key（如 verify_code_daily:register:13800138001:2026-04-20）
-- ARGV[1]: 每日发送次数上限（如 10）
-- ARGV[2]: 过期时间，单位秒（如到当天午夜的剩余秒数）

local current = tonumber(redis.call('GET', KEYS[1]) or '0')

-- 如果已达上限，直接返回 -1，不累加
if current >= tonumber(ARGV[1]) then
    return -1
end

-- 累加发送次数
local newCount = redis.call('INCR', KEYS[1])

-- 首次计数时，设置过期时间（到当天午夜自动过期）
if newCount == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end

return newCount
