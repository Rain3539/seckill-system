# Kafka 消息队列设计与实现

## 1. 背景问题

秒杀场景瞬时并发极高，如果同步执行"Redis 预减库存 → 写 MySQL → 返回用户"，数据库连接池会迅速耗尽，导致全部请求超时。引入 Kafka 做**异步削峰填谷**，将同步的 DB 写操作转为异步消费，使前端能快速得到响应。

---

## 2. 整体架构

```
用户点击秒杀
    │
    ▼
┌─────────────────────────────────────┐
│  SeckillController.doSeckill()      │
│  POST /api/seckill/do               │
└──────────────┬──────────────────────┘
               │
    ┌──────────▼──────────┐
    │ SeckillServiceImpl  │   （同步，响应快）
    │                     │
    │ 1. 校验商品状态      │   从库查询，仅读
    │ 2. Redis DECR 预减   │   原子操作，拦截99%无效请求
    │ 3. Redis SETNX 幂等  │   每人每商品限购1次
    │ 4. Kafka 发送消息    │   非阻塞，立即返回
    └──────────┬──────────┘
               │
    返回 {messageId, status:"PROCESSING"}
               │
               │  异步
    ┌──────────▼──────────┐
    │ Kafka (seckill-order│
    │       Topic)        │   3分区，缓冲请求
    └──────────┬──────────┘
               │
    ┌──────────▼──────────────────────┐
    │ KafkaOrderConsumerService       │  （异步消费，削峰填谷）
    │                                 │
    │ 1. DB 幂等校验                   │  countSeckillOrder
    │ 2. MySQL 乐观锁扣减库存           │  UPDATE ... WHERE version=?
    │ 3. 生成订单（雪花算法 orderNo）   │  INSERT INTO order
    │ 4. 失败则回滚 Redis 库存          │  INCRBY seckill:stock:{id}
    │ 5. 手动 ACK                      │
    └─────────────────────────────────┘
               │
    ┌──────────▼──────────┐
    │  前端轮询查询订单     │
    │ GET /seckill/order/ │   每秒1次，最多30秒
    │  {seckillProductId} │
    └─────────────────────┘
```

---

## 3. Kafka 基础设施

采用 **KRaft 模式**（无 ZooKeeper），Docker 部署：

```yaml
# docker-compose.yml
kafka:
  image: confluentinc/cp-kafka:7.5.0
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller     # 同时担任 broker + controller
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093
    CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"
  healthcheck:
    test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
```

Topic 配置：

| Topic | 分区数 | 副本数 | 说明 |
|-------|--------|--------|------|
| seckill-order | 3 | 1 | 3分区支持并行消费，单节点1副本 |

---

## 4. 消息体设计

```java
public class SeckillOrderMessage implements Serializable {
    private Long messageId;          // 雪花算法，全局唯一
    private Long userId;
    private Long seckillProductId;
    private Integer quantity;
    private String productName;      // 快照，不依赖消费时查DB
    private BigDecimal seckillPrice; // 快照，不依赖消费时查DB
}
```

**快照设计的考虑**：消费者处理时商品可能已下架，提前保存必要字段避免查不到数据。

---

## 5. 生产者（Producer）

`SeckillServiceImpl.doSeckill()` 在 Redis 预减库存和幂等校验通过后，发送消息到 Kafka：

```java
// 构造消息（快照商品名称和秒杀价，不依赖后续DB查询）
SeckillOrderMessage msg = new SeckillOrderMessage(
    snowflakeIdGenerator.nextId(),  // 唯一消息ID
    userId, spId, quantity,
    sp.getName(), sp.getSeckillPrice()
);
kafkaProducerService.sendSeckillOrder(msg);
```

**消息 Key 策略**：`userId:seckillProductId`，保证同一用户同一商品的消息落到同一分区，消费时天然有序。

生产者配置保证可靠投递：
```yaml
producer:
  acks: all                    # 所有副本确认
  retries: 3                   # 失败重试3次
  enable.idempotence: true     # 幂等生产者，防重复发送
```

---

## 6. 消费者（Consumer）

`KafkaOrderConsumerService` 监听 `seckill-order` Topic，手动提交 offset：

```java
@KafkaListener(topics = "seckill-order", groupId = "seckill-order-group")
@DS(DataSourceType.MASTER)                          // 强制走主库
@Transactional(rollbackFor = Exception.class)       // 事务保证
public void handleSeckillOrder(ConsumerRecord<String, SeckillOrderMessage> record,
                               Acknowledgment acknowledgment) { ... }
```

消费者每条消息的处理流程：

| 步骤 | 操作 | 失败处理 |
|------|------|----------|
| 1 | `countSeckillOrder` 查 DB 幂等 | 已存在 → ACK 跳过 |
| 2 | `decreaseStock` MySQL 乐观锁 | version 冲突 → 回滚 Redis，不ACK等重试 |
| 3 | `orderMapper.insert` 创建订单 | 异常 → 不ACK，Kafka 自动重试 |
| 4 | `acknowledgment.acknowledge()` | — |

消费者配置：

```yaml
consumer:
  enable-auto-commit: false           # 关闭自动提交
  auto-offset-reset: earliest         # 首次消费从头开始
  spring.json.trusted.packages: com.seckill.*   # 信任反序列化包
listener:
  ack-mode: manual_immediate          # 手动立即提交 offset
```

---

## 7. 三重防超卖保障

```
┌──────────────────────────────────────────────────────────┐
│  第1层：Redis DECR 原子预减库存                            │
│  → 在内存中完成，响应时间 < 1ms，拦截 99% 无效请求          │
│  → 库存 <= 0 时立即返回"已售罄"，不产生 Kafka 消息         │
├──────────────────────────────────────────────────────────┤
│  第2层：Redis SETNX 防重复下单                             │
│  → key: seckill:user:{uid}:sp:{sid}，24h 过期             │
│  → 同一用户同一商品只能成功 SETNX 一次                      │
│  → 失败时回滚第1层的 DECR                                  │
├──────────────────────────────────────────────────────────┤
│  第3层：MySQL 乐观锁 + 幂等校验                             │
│  → WHERE version = #{version} AND avail_stock >= quantity  │
│  → version 不匹配则更新失败，回滚 Redis 库存并重试           │
│  → countSeckillOrder 做 DB 层幂等兜底                      │
└──────────────────────────────────────────────────────────┘
```

---

## 8. Redis 库存回滚策略

消费者处理失败时，必须回滚 Redis 预扣的库存，否则会出现"库存丢失"：

| 失败场景 | 处理方式 |
|----------|----------|
| 商品不存在 / 库存不足 | `INCRBY seckill:stock:{id} quantity` 回滚 + ACK |
| MySQL 乐观锁冲突 | 回滚 Redis + 不ACK（Kafka 自动重试） |
| 其他异常 | 不ACK（Kafka 自动重试） |

---

## 9. 前端异步适配

秒杀下单改为异步后，前端不再直接拿到订单号，而是通过轮询获取：

```
POST /api/seckill/do  →  返回 {messageId, status: "PROCESSING"}
                                │
                     前端每秒轮询 │
                                ▼
GET /api/seckill/order/{spId}  →  订单创建完成后返回 Order 对象
                                │
                                ▼
                        弹窗显示订单号 + 金额
```

轮询逻辑（前端）：
```javascript
// 提交秒杀后显示"处理中"弹窗
orderProcessing.value = true
// 每秒查询一次，最多30秒
for (let i = 0; i < 30; i++) {
  await sleep(1000)
  const res = await getSeckillOrderApi(seckillProductId)
  if (res.data) {           // 订单已创建
    createdOrder.value = res.data
    orderProcessing.value = false
    return
  }
}
// 超时提示用户在"我的订单"中查看
```

---

## 10. 文件清单

| 文件 | 职责 |
|------|------|
| `config/KafkaConfig.java` | Topic 创建（3分区1副本） |
| `service/KafkaProducerService.java` | 生产者接口 |
| `service/impl/KafkaProducerServiceImpl.java` | 生产者实现，异步发送 + 回调日志 |
| `service/impl/KafkaOrderConsumerService.java` | 消费者实现，幂等 + 乐观锁 + 事务 |
| `model/dto/SeckillOrderMessage.java` | 消息体 DTO |
| `utils/SnowflakeIdGenerator.java` | 雪花算法 ID 生成器（订单号 + 消息ID） |
| `application.yml` | Kafka 生产者/消费者配置 |
| `docker-compose.yml` | Kafka KRaft 容器编排 |
