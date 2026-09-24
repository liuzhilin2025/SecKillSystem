# 07 · Redis Lua 脚本原子预扣缓存

> 建议用时：5~6 天 ｜ 前置：[06-消息队列削峰填谷](./06-消息队列削峰填谷.md) ｜ 对应专栏第七章
> 阶段目标：把库存校验与扣减从 MySQL 挪到 Redis，用 **Lua 保证原子性**，彻底解决 DB 行锁瓶颈

## 学习目标

1. 说清楚"MQ 削峰后 MySQL 行锁仍然是瓶颈"的原因。
2. 用 Lua 脚本实现**库存原子扣减 + 一人一单校验**。
3. 处理 MQ 发送失败时的库存回补，保证数据最终一致。
4. 实现售罄标记快速失败、商品查询接口合并实时库存。
5. 用压测验收本阶段成果。

---

## 一、章节拆解

| 小节 | 主题 | 核心知识点 | 产出 |
|---|---|---|---|
| 7.1 | 为什么行锁仍是瓶颈 | 同一行库存 → 所有请求串行化 | 瓶颈分析 |
| 7.2 | 库存预热到 Redis | 活动开始前灌库存、预热时机与幂等 | 预热任务 |
| 7.3 | Lua 实现库存原子扣减 | `DECRBY` 原子性、Lua 在 Redis 单线程执行 | `seckill_stock.lua` |
| 7.4 | Lua 实现一人一单 | Set 结构 + 是否已购判断 | 限购 |
| 7.5 | Lua 接入下单主链路 | 校验 → Lua 扣减 → 发 MQ | 改造后的下单接口 |
| 7.6 | MQ 发送失败库存回补（1） | 本地异常捕获回补 | 一致性保障 |
| 7.7 | MQ 发送失败库存回补（2） | 回补的原子性与并发问题 | — |
| 7.8 | 消费者链路优化 | Redis 已扣减，DB 只做落库 | 消费提速 |
| 7.9 | 售罄标记与快速失败 | `sold_out` 标志位，避免无效请求打 Redis | QPS 提升 |
| 7.10 | 查询接口升级 | 展示库存 = Redis 实时库存 | 前端体验 |
| 7.11 | JMeter 验收 | 全链路压测 | 验收报告 |

---

## 二、为什么 Redis Lua 是关键一步

### 阶段三之后的瓶颈

即使 MQ 削峰，消费者最终还是要执行：

```sql
UPDATE t_seckill_goods SET stock = stock - 1 WHERE id = ? AND stock > 0;
```

**同一行记录的更新会持有排他行锁，所有消费线程串行等待**。即使开了 10 个消费者，
真正能并行执行的只有 1 个（同一商品），消费速率上限被 DB 单行锁死。

### 解法：把"判断 + 扣减"整体挪到 Redis

Redis 是**单线程执行命令**的，Lua 脚本在执行期间不会被其他命令打断，
因此"读库存 → 判断 → 扣减 → 记录已购"可以作为一个**原子操作**完成。

```lua
-- KEYS[1] = seckill:stock:{goodsId}     库存
-- KEYS[2] = seckill:bought:{goodsId}    已购用户 Set
-- ARGV[1] = userId
-- ARGV[2] = 每人限购数
local stock = tonumber(redis.call('GET', KEYS[1]))
if not stock or stock <= 0 then
    return -1                                  -- 售罄
end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2                                  -- 重复下单
end
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return stock - 1                               -- 剩余库存
```

> 返回值语义要定义清晰：`>=0` 扣减成功、`-1` 售罄、`-2` 重复下单。

### 与数据库唯一索引的关系

- **Redis Lua**：拦住 99% 的无效请求，性能主力
- **DB 唯一索引 + `stock > 0`**：最后一道防线，应对 Redis 异常/回补失败等极端情况
- 两者是**互补**关系，缺一不可

---

## 三、数据一致性：库存回补

秒杀链路可能出现"Redis 扣减成功，但 MQ 发送失败"，此时必须回补 Redis 库存：

```java
Long result = redisTemplate.execute(seckillScript, keys, userId, limit);
if (result != null && result >= 0) {
    try {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, msg);
    } catch (Exception e) {
        // 发送失败 → 回补库存 + 移除已购记录
        redisTemplate.execute(rollbackScript, keys, userId);
        throw new BizException("秒杀失败，请重试");
    }
}
```

回补脚本同样要用 Lua 保证原子性：

```lua
redis.call('INCR', KEYS[1])
redis.call('SREM', KEYS[2], ARGV[1])
return 1
```

> 更严谨的做法：**本地消息表 + 定时任务补偿**（见第 9 章），
> 因为"回补"本身也可能失败（Redis 宕机）。

---

## 四、售罄标记（快速失败）

```lua
-- 库存为 0 时写一个标记 key，TTL 与活动结束时间一致
redis.call('SET', 'seckill:soldout:' .. goodsId, '1', 'EX', ttl)
```

后续请求先 `EXISTS` 判断，直接返回售罄，**不再执行扣减脚本**，大幅降低 Redis 压力。

---

## 五、动手任务 Checklist

- [ ] 编写库存预热任务：活动开始前把 DB 库存写入 Redis
- [ ] 用 Redisson 分布式锁保证**多实例预热不会重复执行**（预热幂等）
- [ ] 编写 `seckill_stock.lua`（扣减 + 一人一单）
- [ ] Spring Boot 加载并执行 Lua 脚本（`DefaultRedisScript`），注意返回类型 `Long`
- [ ] 改造下单接口：Lua 预扣 → 发 MQ → 返回排队中
- [ ] 实现 MQ 发送失败的库存回补（Lua 原子回补）
- [ ] 消费者链路简化：不再 `stock-1`，DB 只落订单（仍需唯一索引兜底）
- [ ] 实现售罄标记与快速失败
- [ ] 商品查询接口返回 Redis 实时库存
- [ ] JMeter 全链路压测，统计：QPS、Redis QPS、DB QPS、超卖数、重复下单数

---

## 六、验收标准

| 指标 | 要求 |
|---|---|
| 超卖 | 0（Redis 预扣 + DB 兜底双重保障） |
| 重复下单 | 0 |
| 系统 QPS | 相比阶段三有数量级提升 |
| DB 写入量 | 仅等于成功订单数，不再与请求量成正比 |
| 一致性 | Redis 库存扣减数 = DB 订单数（一致） |

---

## 七、常见坑

- **Lua 脚本里用了 `KEYS` 之外动态拼 key** → Redis Cluster 下会报错（所有 key 必须通过 `KEYS[]` 传入）
- Lua 返回值类型转换错误（Redis 返回 Integer，Java 用 `Long.class`）
- 脚本内包含复杂逻辑/循环 → 阻塞 Redis 单线程，影响所有业务
- 预热与活动开始时间不同步 → 活动开始了库存还是 0
- 回补逻辑未做原子性/幂等 → 库存越补越多
- Redis 与 DB 库存双写不一致时没有对账机制
- 忘记给已购 Set 设置 TTL → 内存持续增长

## 八、面试追问

- Redis 为什么能保证 Lua 脚本的原子性？它是事务吗？
- Redis 挂了，秒杀怎么继续？（降级到 DB 条件扣减）
- Redis 扣减成功但 DB 落库失败怎么办？
- 如何做 Redis 与 DB 的库存对账？
- `DECR` 和 `DECRBY` 的区别？库存扣减为什么不用 `GET` + `SET`？
- Lua 脚本太长有什么问题？（阻塞单线程，建议轻量）
