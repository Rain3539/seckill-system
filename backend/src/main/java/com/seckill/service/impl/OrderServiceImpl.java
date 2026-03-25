package com.seckill.service.impl;

import com.seckill.datasource.DS;
import com.seckill.datasource.DataSourceType;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.ProductMapper;
import com.seckill.model.dto.PlaceOrderDTO;
import com.seckill.model.entity.Order;
import com.seckill.model.entity.Product;
import com.seckill.service.OrderService;
import com.seckill.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final String PRODUCT_LIST_KEY   = "product:list";
    private static final String PRODUCT_KEY_PREFIX = "product:detail:";

    private final OrderMapper   orderMapper;
    private final ProductMapper productMapper;
    private final RedisUtils    redisUtils;

    public OrderServiceImpl(OrderMapper orderMapper, ProductMapper productMapper, RedisUtils redisUtils) {
        this.orderMapper   = orderMapper;
        this.productMapper = productMapper;
        this.redisUtils    = redisUtils;
    }

    // ── 普通商品下单：写操作，走主库 ──────────────────────────────────
    @Override
    @DS(DataSourceType.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Order placeOrder(Long userId, PlaceOrderDTO dto) {
        log.info("[RW-Split] placeOrder userId={} → MASTER DB", userId);
        Product p = productMapper.findById(dto.getProductId());
        if (p == null || p.getStatus() != 1) throw new RuntimeException("商品不存在或已下架");
        if (p.getStock() < dto.getQuantity())  throw new RuntimeException("库存不足");

        int updated = productMapper.decreaseStock(p.getId(), dto.getQuantity());
        if (updated == 0) throw new RuntimeException("库存不足，请重试");

        Order order = new Order();
        order.setOrderNo(genOrderNo());
        order.setUserId(userId);
        order.setProductId(p.getId());
        order.setProductType(0);
        order.setProductName(p.getName());
        order.setQuantity(dto.getQuantity());
        order.setUnitPrice(p.getPrice());
        order.setAmount(p.getPrice().multiply(java.math.BigDecimal.valueOf(dto.getQuantity())));
        order.setStatus(0);
        orderMapper.insert(order);

        log.info("普通下单成功 userId={} productId={} orderNo={}", userId, p.getId(), order.getOrderNo());

        // 清除商品缓存，确保刷新列表时看到最新库存
        redisUtils.delete(PRODUCT_LIST_KEY);
        redisUtils.delete(PRODUCT_KEY_PREFIX + p.getId());

        return order;
    }

    // ── 支付订单：写操作，走主库 ──────────────────────────────────────
    @Override
    @DS(DataSourceType.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Order payOrder(Long userId, String orderNo) {
        log.info("[RW-Split] payOrder orderNo={} → MASTER DB", orderNo);
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) throw new RuntimeException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new RuntimeException("无权操作此订单");
        if (order.getStatus() != 0)            throw new RuntimeException("订单状态不允许支付");
        orderMapper.updateStatus(order.getId(), 1);
        order.setStatus(1);
        return order;
    }

    // ── 取消订单：写操作，走主库 ──────────────────────────────────────
    @Override
    @DS(DataSourceType.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Order cancelOrder(Long userId, String orderNo) {
        log.info("[RW-Split] cancelOrder orderNo={} → MASTER DB", orderNo);
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) throw new RuntimeException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new RuntimeException("无权操作此订单");
        if (order.getStatus() != 0)            throw new RuntimeException("只有待支付的订单可以取消");
        orderMapper.updateStatus(order.getId(), 2);
        if (order.getProductType() == 0) {
            productMapper.increaseStock(order.getProductId(), order.getQuantity());
            // 清除商品缓存，确保库存回滚后刷新可见
            redisUtils.delete(PRODUCT_LIST_KEY);
            redisUtils.delete(PRODUCT_KEY_PREFIX + order.getProductId());
        }
        order.setStatus(2);
        return order;
    }

    // ── 查询我的订单：走主库（下单后立即可见，避免主从延迟）────────
    @Override
    @DS(DataSourceType.MASTER)
    public List<Order> getMyOrders(Long userId) {
        log.info("[RW-Split] getMyOrders userId={} → MASTER DB", userId);
        return orderMapper.findByUserId(userId);
    }

    // ── 按单号查询：读操作，走从库 ──────────────────────────────────
    @Override
    @DS(DataSourceType.SLAVE)
    public Order getByOrderNo(String orderNo) {
        log.info("[RW-Split] getByOrderNo orderNo={} → SLAVE DB", orderNo);
        Order o = orderMapper.findByOrderNo(orderNo);
        if (o == null) throw new RuntimeException("订单不存在");
        return o;
    }

    private String genOrderNo() {
        String ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return ts + rand;
    }
}
