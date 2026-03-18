# 负载均衡与 JMeter 压测指南

## 一、系统架构图

```
                    ┌─────────────────────────────────────┐
  浏览器/JMeter      │           Docker 网络               │
  ─────────────     │                                     │
  HTTP :80     ───► │  Nginx(:80)                         │
                    │    │  upstream backend_cluster       │
                    │    ├──► backend-1(:8080) ──► MySQL   │
                    │    └──► backend-2(:8080) ──► Redis   │
                    └─────────────────────────────────────┘
```

---

## 二、启动命令

```bash
# 首次启动（构建镜像）
docker-compose up -d --build

# 查看各容器状态
docker-compose ps

# 实时查看两个后端日志（观察哪个实例在处理请求）
docker-compose logs -f backend-1 backend-2

# 查看 Nginx 访问日志（含 upstream 字段）
docker-compose logs -f nginx
# 或直接查看宿主机日志文件（已挂载）
type nginx\logs\access.log
```

---

## 三、验证负载均衡

### 方法1：浏览器手动验证
多次访问：http://localhost/api/instance
观察响应中的 `instanceId` 字段，轮询算法下应该交替出现 `backend-1` 和 `backend-2`

### 方法2：PowerShell 快速验证
```powershell
# 连续请求10次，观察分布
1..10 | ForEach-Object {
    $r = Invoke-RestMethod http://localhost/api/instance
    Write-Host $r.data.instanceId
}
```

### 方法3：curl（Git Bash）
```bash
for i in {1..10}; do
  curl -s http://localhost/api/instance | python -c "import sys,json; d=json.load(sys.stdin); print(d['data']['instanceId'])"
done
```

---

## 四、切换负载均衡算法

编辑 `nginx/conf.d/default.conf`，找到 upstream 块：

| 算法 | 操作 |
|------|------|
| **轮询**（默认） | 保持当前配置 |
| **加权轮询** | 注释掉当前 upstream，取消注释 Weighted 那段 |
| **IP Hash** | 注释掉当前 upstream，取消注释 ip_hash 那段 |
| **最少连接** | 注释掉当前 upstream，取消注释 least_conn 那段 |

修改后执行热重载（不停机）：
```bash
docker-compose exec nginx nginx -s reload
```

---

## 五、JMeter 压测步骤

### 5.1 安装 JMeter
下载地址：https://jmeter.apache.org/download_jmeter.cgi
解压后运行 `bin\jmeter.bat`（Windows）

### 5.2 创建测试计划

**步骤：**

1. **新建测试计划**
   右键 `Test Plan` → Add → Threads → `Thread Group`
   - Number of Threads (users): `100`（并发用户数）
   - Ramp-up period: `10`（10秒内启动所有线程）
   - Loop Count: `10`（每用户循环10次，共1000请求）

2. **添加 HTTP 请求**
   右键 `Thread Group` → Add → Sampler → `HTTP Request`
   - Server Name: `localhost`
   - Port: `80`
   - Method: `GET`
   - Path: `/api/instance`

3. **添加响应断言（可选）**
   右键 HTTP Request → Add → Assertions → `Response Assertion`
   - Response Field: Response Body
   - Pattern: `backend-`

4. **添加监听器**
   右键 Thread Group → Add → Listener：
   - `View Results Tree`（查看每条请求详情）
   - `Summary Report`（汇总：平均响应时间、吞吐量、错误率）
   - `Aggregate Report`（聚合报告）

5. **运行测试**
   点击绿色 ▶ 按钮开始，观察 Summary Report 中的：
   - `Average`：平均响应时间（ms）
   - `Throughput`：每秒处理请求数（TPS）
   - `Error %`：错误率（应为 0%）

### 5.3 验证请求均衡分布

**方式A：查看后端日志**
压测期间打开另一个终端：
```bash
# 统计各实例处理的请求数
docker logs seckill-backend-1 2>&1 | grep "GET /api/instance" | wc -l
docker logs seckill-backend-2 2>&1 | grep "GET /api/instance" | wc -l
```
轮询算法下两个数值应大致相等（±5%）

**方式B：分析 Nginx 日志**
```bash
# 统计 access.log 中各 upstream 出现次数
# Windows PowerShell：
Select-String "backend-1" nginx\logs\access.log | Measure-Object | Select Count
Select-String "backend-2" nginx\logs\access.log | Measure-Object | Select Count
```

### 5.4 秒杀接口压测（高并发场景）

添加第二个 HTTP Request（测试秒杀防超卖）：
- Method: `POST`
- Path: `/api/seckill/do`
- Body Data:
```json
{"productId": 4, "quantity": 1}
```
- Headers → Add: `Content-Type: application/json`
- Headers → Add: `Authorization: Bearer <先登录获取token>`

压测后检查：
```sql
-- 连接MySQL验证库存不超卖
SELECT avail_stock, locked_stock, total_stock FROM inventory WHERE product_id = 4;
-- avail_stock + locked_stock 应始终 <= total_stock(100)
```

---

## 六、各算法适用场景对比

| 算法 | 分配方式 | JMeter观察结果 | 适用场景 |
|------|---------|--------------|---------|
| 轮询 | 1-2-1-2 交替 | 请求数基本5:5 | 无状态、同构服务 |
| 加权(3:1) | 每4次3给1、1给2 | 请求数约7.5:2.5 | 服务器性能不均 |
| IP Hash | 同IP固定到一台 | 单机所有请求 | 有Session的服务 |
| 最少连接 | 动态分配给空闲的 | 响应快的拿更多 | 请求耗时差异大 |

---

## 七、常用运维命令

```bash
# 扩容：再增加一个实例（需先在 docker-compose.yml 添加 backend-3）
docker-compose up -d --scale backend-1=1

# 查看容器资源占用
docker stats seckill-backend-1 seckill-backend-2

# 停止单个后端模拟故障，观察 Nginx 是否自动切换
docker-compose stop backend-1
# 此时所有请求应全部转发到 backend-2

# 恢复
docker-compose start backend-1

# 完全清理（含数据卷）
docker-compose down -v
```
