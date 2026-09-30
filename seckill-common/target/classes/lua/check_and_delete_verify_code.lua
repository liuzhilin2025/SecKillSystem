-- 原子性地比对验证码并删除
-- 如果验证码匹配，删除 Key 并返回 1；否则返回0
--
-- KEY[1]: 验证码的 Redis Key（如 verify_code:register:13800138001）
-- ARGV[1]: 用户输入的验证码

local storedCode = redis.call('GET', KEYS[1])
if storedCode == false then
    return 0
end

if storedCode == ARGV[1] then
    redis.call('DEL', KEYS[1])
    return 1
end

return 0