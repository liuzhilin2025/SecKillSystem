# CODEBUDDY.md
This file provides guidance to CodeBuddy when working with code in this repository.

## ⚠️ 先读这条：源码在磁盘上是被加密的

工作区的 `.java` 文件被透明加密软件保护（文件头 `%TSD-Header-###%`），直接读取拿到的是乱码。**git 在白名单内，看到的是明文**，所以读写源码必须绕道 git：

- 读已提交的文件：`git --no-pager show HEAD:<path>`
- 读未跟踪 / 已修改的文件：`git --no-pager diff --no-index -- NUL <path>`（输出前 6 行是 diff 头，正文行号 = 输出行号 − 6；配 `Select-Object -Skip N -First M` 分段读）
- 看某文件改了什么：`git --no-pager diff -- <path>`
- 搜索：`git --no-pager grep -n 'pattern' -- '*.java'`。注意 **git grep 不搜未跟踪文件**，先 `git ls-files -o --exclude-standard` 找出新增文件。
- 写源码：先用编辑工具把 unified diff 写成临时 `.patch`（用 LF 存），脚本转成 CRLF 后 `git apply --recount -v`，成功后删掉 patch。**不要**用编辑工具直接覆盖 `.java`，会破坏加密态。
- 补丁上下文匹配不上，九成是中文标点：项目注释用**全角**标点（`，` `：` `（）`），改某行前先按上面的方式把原文读出来逐字对照。
- 始终带 `--recount`，省得手算 hunk 行数。

## 常用命令

```bash
mvn -DskipTests clean package                 # 全量构建
mvn -pl seckill-goods -am -DskipTests compile # 单模块编译（-am 连带编译依赖的 common）
mvn -o -pl seckill-goods -am -DskipTests compile   # 加 -o 走离线仓库，无网络时用
mvn test                                      # 全部测试
mvn -pl seckill-app -Dtest=AppTest test       # 单个测试类
mvn -pl seckill-app -Dtest=AppTest#方法名 test  # 单个测试方法
mvn -pl seckill-app spring-boot:run           # 启动（开发期）
```

- 启动类：`seckill-app` 的 `com.practice.flashsale.app.SeckillApplication`，端口 8080。
- 外部依赖：MySQL `localhost:3306/seckill`、Redis `localhost:6379`（DB 0）。连接参数、连接池、线程池参数都在 `seckill-app/src/main/resources/application*.yml`，本地开发走 `dev` profile。
- 运行日志：`logs/seckill-info.log`（Log4j2 + Disruptor 异步）。排查接口行为优先直接 grep 这个文件，比反复重启快得多。
- 压测：根目录 `HTTP Request.jmx`（JMeter），验收数据记到 `docs/进度追踪表.md`。

## 架构

### 模块与依赖方向

`seckill-app`（唯一可启动模块，只有启动类和配置）→ `seckill-user` / `seckill-goods` / `seckill-order` → `seckill-common`。业务模块之间**不互相依赖**，共用能力全部下沉到 `seckill-common`。

`seckill-common` 是事实上的核心：

- `entity`：数据库实体，命名**无 DO 后缀**（`SeckillActivity`、`SeckillGoods`、`Goods`）。若看到 `SeckillActivityDO`、`Response`、`selectByPrimaryKey` 这类写法，那是外部教程的命名，落到本项目要改成 `SeckillActivity`、`Result`、`selectById`。
- `mapper`：MyBatis-Plus `BaseMapper`，多数场景直接用 `Wrappers.lambdaQuery()` 组装条件，不写 XML。XML 由 `classpath*:/mapper/**/*.xml` 扫描（注意是 `classpath*`，为的是扫到 jar 内的）。
- `constant/RedisKeyConstants`：所有 Redis key 前缀 + 缓存 TTL 计算规则，见下文"缓存"。
- `utils/Result`、`enums/ResultCodeEnum`、`exception/BizException` + `GlobalExceptionHandler`。
- `config`：`RedisConfig`（RedisTemplate）、`SaTokenConfig`、`JacksonConfig`、`ThreadPoolConfig`、`LocalCacheConfig`（Caffeine）。
- `aspect/ApiOperationLog`：接口出入参日志切面。

### 接口层约定

- 统一返回 `Result<T>`；业务失败抛 `BizException(ResultCodeEnum.XXX)`，由 `GlobalExceptionHandler` 转成 `Result`。**Service 不返回 `Result`**（分层倒置），只返回业务数据或抛异常。
- 错误码按模块分段：`1xxxx` 通用、`2xxxx` 用户、`3xxxx` 商品、`4xxxx` 订单。
- 入参 DTO 用 Lombok `@Data + @Builder + @NoArgsConstructor`，配 `@Validated` + jakarta 校验注解。
- **鉴权是白名单式的**：`SaTokenConfig` 只对 `/seckill/order`、`/user/logout` 调 `StpUtil.checkLogin()`，其余路径（含 `/admin/**`）默认免登录。新增需要鉴权的接口必须去 `SaTokenConfig` 里补 `SaRouter.match`。
- 登录态、验证码走 Sa-Token + Redis；验证码发送走 `ThreadPoolConfig` 里的业务线程池异步化。

### 缓存（第 05 章）

读接口（商品列表、商品详情）的链路是 **Caffeine L1 → 布隆过滤器 → Redis L2 → MySQL**：

- L1 是 `LocalCacheConfig` 里两个 `Cache<String,String>` bean，`expireAfterWrite` 30 秒，key 直接复用 Redis key。按字段名 `@Resource` 注入（两个 bean 同类型，只能靠名字区分）。
- 命中 L1/L2 后都要**回源补齐高频字段**：库存从 `seckill_goods` 实时查，`activityStatus` 用缓存里的起止时间实时重算。缓存里存的是组装好的 JSON 字符串，读写走 `JsonUtils`（`parseArray` / `parseObject` / `toJsonString`）。
- 写缓存收在两个私有方法 `cacheGoodsList` / `cacheGoodsDetail` 里（空列表不写、TTL 带抖动），要加降级 try-catch 只改这两处。
- TTL 不是固定值：`RedisKeyConstants.calculateTtlSeconds(endTime)` = 活动剩余时间 + 30 分钟缓冲，`resolveTtlSeconds` 再兜底（活动早已结束则 5 分钟）并叠加 0~300 秒抖动。
- 防穿透：不存在的 key 写 `NULL_CACHE_VALUE` 短 TTL 空值；`SECKILL_ACTIVITY_BLOOM_KEY` / `SECKILL_GOODS_BLOOM_KEY` 两个 Redisson `RBloomFilter`。**布隆过滤器只允许出现假阳性，绝不允许假阴性**——批量重建时必须一次灌全量，不能按单个活动 `delete()` 后重建。
- 预热入口：`AdminGoodsController` 的 `POST /admin/goods/preheat`，Service 方法 `preheatActivityGoods(activityId)`。

注意：项目**尚未开启定时任务**（启动类没有 `@EnableScheduling`），预热目前只能手动触发。

### 文档即路线图

`docs/` 是本项目配套的 8 周学习计划（`README.md` 是总览，`00`~`10` 是各章，`进度追踪表.md` 记录章节打勾与压测数据）。代码是**按章节渐进演进**的，不是一次成型的：想知道某个实现为什么"看起来不够完美"（例如命中缓存仍要查 DB 补库存），先看 `docs/05-缓存层读性能优化.md` 之类的当前章节文档和进度追踪表，里面写明了下游章节会接手哪些问题。
