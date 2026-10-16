-- KEYS[1]: 秒杀令牌 Key
-- ARGV[1]: 请求携带的秒杀令牌
-- 返回值：1-校验成功并删除令牌，0-令牌不存在或不匹配

-- 1. 读取 Redis 中后端签发的令牌
local cachedToken = redis.call('GET', KEYS[1])

-- 2. Redis 中没有令牌，说明令牌不存在、已过期，或已经被使用过
if not cachedToken then
    return 0
end

-- 3. 请求携带的令牌和 Redis 中保存的令牌不一致，说明不是后端签发的有效令牌
if cachedToken ~= ARGV[1] then
    return 0
end

-- 4. 校验通过后，立即删除令牌，保证同一个 token 只能被成功使用一次
redis.call('DEL', KEYS[1])

return 1