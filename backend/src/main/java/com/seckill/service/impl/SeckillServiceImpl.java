package com.seckill.service.impl;

import com.seckill.datasource.DS;
import com.seckill.datasource.DataSourceType;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.SeckillProductMapper;
import com.seckill.model.dto.SeckillRequestDTO;
import com.seckill.model.entity.Order;
import com.seckill.model.entity.SeckillProduct;
import com.seckill.service.SeckillService;
import com.seckill.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillServiceImpl implements SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillServiceImpl.class);

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String USER_SECKILL_KEY = "seckill:user:%d:sp:%d";

    private final RedisUtils             redisUtils;
    private final SeckillProductMapper   seckillProductMapper;
    private final OrderMapper            orderMapper;

    public SeckillServiceImpl(RedisUtils redisUtils,
                               SeckillProductMapper seckillProductMapper,
                               OrderMapper orderMapper) {
        this.redisUtils           = redisUtils;
        this.seckillProductMapper = seckillProductMapper;
        this.orderMapper          = orderMapper;
    }

    // ── 秒杀下单：写操作，走主库 ──────────────────────────────────────
    @Override
    @DS(DataSourceType.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Order doSeckill(Long userId, SeckillRequestDTO dto) {
        Long spId     = dto.getSeckillProductId();
        int  quantity = dto.getQuantity();

        log.info("[RW-Split] doSeckill userId={} spId={} → MASTER DB", userId, spId);

        SeckillProduct sp = seckillProductMapper.findById(spId);
        if (sp == null || sp.getStatus() != 1) throw new RuntimeException("秒杀商品不存在或已下架");

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(sp.getStartTime())) throw new RuntimeException("秒杀活动尚未开始");
        if (now.isAfter(sp.getEndTime()))    throw new RuntimeException("秒杀活动已结束");

        // Step 1: Redis 预减库存
        String stockKey = STOCK_KEY_PREFIX + spId;
        if (Boolean.FALSE.equals(redisUtils.hasKey(stockKey))) {
            redisUtils.set(stockKey, String.valueOf(sp.getAvailStock()), 24, TimeUnit.HOURS);
        }
        Long remaining = redisUtils.decrement(stockKey);
        if (remaining < 0) {
            redisUtils.increment(stockKey);
            throw new RuntimeException("秒杀失败：库存不足");
        }

        // Step 2: 防重复下单
        String  userKey  = String.format(USER_SECKILL_KEY, userId, spId);
        Boolean isFirst  = redisUtils.setIfAbsent(userKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isFirst)) {
            redisUtils.increment(stockKey);
            throw new RuntimeException("每人每件秒杀商品限购1次");
        }

        // Step 3: MySQL 乐观锁扣减（主库）
        int updated = seckillProductMapper.decreaseStock(spId, quantity, sp.getVersion());
        if (updated == 0) {
            redisUtils.increment(stockKey);
            redisUtils.delete(userKey);
            throw new RuntimeException("秒杀冲突，请重试");
        }

        // Step 4: 生成订单（主库）
        Order order = new Order();
        order.setOrderNo(genOrderNo());
        order.setUserId(userId);
        order.setProductId(spId);
        order.setProductType(1);
        order.setProductName(sp.getName());
        order.setQuantity(quantity);
        order.setUnitPrice(sp.getSeckillPrice());
        order.setAmount(sp.getSeckillPrice().multiply(java.math.BigDecimal.valueOf(quantity)));
        order.setStatus(0);
        orderMapper.insert(order);

        log.info("秒杀成功 userId={} spId={} orderNo={}", userId, spId, order.getOrderNo());
        return order;
    }

    private String genOrderNo() {
        String ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "SK" + ts + rand;
    }
}
