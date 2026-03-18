# ⚡ 商品库存与秒杀系统

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2 + MyBatis + Maven |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7（分布式缓存 + 秒杀防超卖） |
| 前端 | Vue 3 + Element Plus + Vite |
| 代理 | Nginx（负载均衡 + 动静分离） |
| 容器 | Docker + Docker Compose |
| 认证 | JWT |

---

## 数据库 ER 设计
![!\[alt text\](数据库er图.png)](说明图片/数据库er图.png)
```
user（用户表）
  id | username | password | email | phone | status

product（普通商品表）
  id | name | description | price | stock | image_url | status

seckill_product（秒杀商品表）
  id | name | description | origin_price | seckill_price
   | total_stock | avail_stock | locked_stock | version
   | start_time | end_time | status

order（统一订单表）
  id | order_no | user_id | product_id | product_type(0普通/1秒杀)
   | product_name | quantity | unit_price | amount | status
```

---

## API 接口文档

### 用户
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/user/register | 注册 |
| POST | /api/user/login    | 登录 |

### 普通商品（只显示在商品列表页）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/product/list  | 商品列表（Redis缓存）|
| GET | /api/product/{id}  | 商品详情（防穿透/击穿/雪崩）|

### 秒杀商品（只显示在秒杀专区）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | /api/seckill/list      | 秒杀商品列表 |
| GET  | /api/seckill/{id}      | 秒杀商品详情 |
| POST | /api/seckill/do        | 执行秒杀（需登录）|
| POST | /api/seckill/warmup/{id} | 手动预热库存 |

### 订单
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/order/my        | 我的订单（需登录）|
| GET | /api/order/{orderNo} | 订单详情 |

---

## 核心设计说明

### 1. 商品分离设计
- `product` 表：普通商品，只在**商品列表页**展示
- `seckill_product` 表：秒杀商品，只在**秒杀专区**展示
  - 含 `start_time` / `end_time`：控制秒杀时间窗口
  - 含 `version` 乐观锁字段：防止并发超卖
  - 每人每件秒杀商品限购1次（Redis SETNX实现）

### 2. 秒杀防超卖三道防线
```
① Redis DECR 原子预减库存  → 拦截99%高并发，不打DB
② Redis SETNX 防重复下单   → 每人每商品限购1次
③ MySQL 乐观锁 version     → 兜底防止并发写入超卖
```

### 3. 分布式缓存（防三大问题）
```
【防穿透】查询不存在的商品 → 缓存空值"NULL"，TTL=5min
【防击穿】热点key过期     → Redis分布式锁，只放一个线程重建缓存
【防雪崩】大量key同时过期  → 过期时间加随机抖动 ±5min
```

### 4. Nginx 动静分离
```
静态资源 (JS/CSS/图片) → Nginx直接服务，强缓存30天（文件名含hash）
动态请求 (/api/*)     → 代理到后端集群，禁止缓存
```

---

## 快速启动

```bash
# 1. 构建前端
cd frontend && npm install && npm run build && cd ..

# 2. 启动所有容器
docker-compose up -d --build

# 3. 查看日志
docker-compose logs -f backend-1 backend-2
```

访问：http://localhost

---

## JMeter 压测指南

### 压测静态资源（动静分离验证）
- URL: `http://localhost/assets/index-xxx.js`（从浏览器F12获取实际文件名）
- 预期：响应时间 < 5ms，响应头含 `Cache-Control: public, max-age=2592000`

### 压测动态 API（负载均衡验证）
- URL: `http://localhost/api/instance`
- 预期：响应中 instanceId 交替出现 backend-1 / backend-2

### 压测秒杀接口（高并发防超卖验证）
- URL: `POST http://localhost/api/seckill/do`
- Body: `{"seckillProductId": 1, "quantity": 1}`
- Header: `Authorization: Bearer <token>`
- 压测后查验：`SELECT avail_stock FROM seckill_product WHERE id=1` 不得为负数

---

## 切换负载均衡算法

编辑 `nginx/conf.d/default.conf` 中的 `upstream` 块，然后：
```bash
docker-compose exec nginx nginx -s reload
```

---

## v3.1 新增功能

### 商品图片（动静分离）
- 图片文件存放于 `nginx/static/images/`，由 Nginx 直接服务，**不经过后端**
- URL 格式：`http://localhost/static/images/product_1.svg`
- Nginx 配置强缓存 90 天，`Cache-Control: public, max-age=7776000, immutable`
- 响应头包含 `X-Static-Type: product-image` 便于 JMeter 验证

### 普通商品下单流程
```
商品列表 → 点击【立即下单】→ 下单成功弹窗 → 点击【立即支付】→ 跳转我的订单
商品列表 → 点击【查看详情】→ 详情页选数量 → 下单 → 支付
```

### 秒杀商品详情页
- 倒计时（实时刷新，精确到秒）
- 库存进度条（颜色随剩余量变化）
- 秒杀成功后弹窗支付

### 我的订单支付/取消
- 【立即支付】按钮：模拟支付，直接改状态为"已支付"
- 【取消订单】：取消后普通商品库存自动回滚
- 待支付数量徽标显示在导航栏

### 新增 API
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/order/place       | 普通商品下单 |
| POST | /api/order/pay/{no}    | 支付订单（模拟）|
| POST | /api/order/cancel/{no} | 取消订单 |

### 操作命令（重要：因挂载新卷需重建容器）
```powershell
docker-compose down -v
cd frontend && npm run build && cd ..
docker-compose up -d --build
```

---


## 前端界面

### 登录界面

![!\[alt text\](登录界面.png)](说明图片/登录界面.png)


### 商品列表
![alt text](说明图片/商品列表.png)

### 秒杀专区
![alt text](说明图片/秒杀特区.png)

### 我的订单
![alt text](说明图片/订单.png)