package com.seckill.order.service.impl;

import com.seckill.order.datasource.DS;
import com.seckill.order.datasource.DataSourceType;
import com.seckill.order.mapper.OrderMapper;
import com.seckill.order.model.dto.PlaceOrderDTO;
import com.seckill.order.model.entity.Order;
import com.seckill.order.model.vo.ResultVO;
import com.seckill.order.service.OrderService;
import com.seckill.order.tcc.TccOrderParticipant;
import com.seckill.order.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final String SECKILL_STOCK_KEY = "seckill:stock:";

    private final OrderMapper orderMapper;
    private final RedisUtils redisUtils;
    private final TccOrderParticipant tccOrderParticipant;
    private final RestTemplate restTemplate;

    @Value("${stock-service.url}")
    private String stockServiceUrl;

    public OrderServiceImpl(OrderMapper orderMapper, RedisUtils redisUtils,
                            TccOrderParticipant tccOrderParticipant, RestTemplate restTemplate) {
        this.orderMapper = orderMapper;
        this.redisUtils = redisUtils;
        this.tccOrderParticipant = tccOrderParticipant;
        this.restTemplate = restTemplate;
    }

    @Override
    @DS(DataSourceType.MASTER)
    public Order placeOrder(Long userId, PlaceOrderDTO dto) {
        // 1. 从 Stock Service 获取商品真实信息（名称、价格）
        String productUrl = stockServiceUrl + "/api/product/" + dto.getProductId();
        ResultVO productResult;
        try {
            productResult = restTemplate.getForObject(productUrl, ResultVO.class);
        } catch (Exception e) {
            throw new RuntimeException("商品服务调用失败: " + e.getMessage());
        }
        if (productResult == null || productResult.getCode() != 200 || productResult.getData() == null) {
            throw new RuntimeException("商品不存在");
        }
        // RestTemplate 反序列化为 LinkedHashMap，提取字段
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> productMap = (java.util.Map<String, Object>) productResult.getData();
        String productName = (String) productMap.get("name");
        BigDecimal unitPrice = new BigDecimal(productMap.get("price").toString());

        // 2. HTTP 调 Stock Service 扣减普通商品库存
        String url = stockServiceUrl + "/api/stock/decrease?productId=" + dto.getProductId() + "&quantity=" + dto.getQuantity();
        try {
            ResultVO result = restTemplate.postForObject(url, null, ResultVO.class);
            if (result == null || result.getCode() != 200) {
                throw new RuntimeException("库存不足");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("库存服务调用失败: " + e.getMessage());
        }

        // 3. 本地创建订单（使用真实商品名称和价格）
        try {
            Order order = new Order();
            order.setOrderNo(genOrderNo());
            order.setUserId(userId);
            order.setProductId(dto.getProductId());
            order.setProductType(0);
            order.setProductName(productName);
            order.setQuantity(dto.getQuantity());
            order.setUnitPrice(unitPrice);
            order.setAmount(unitPrice.multiply(BigDecimal.valueOf(dto.getQuantity())));
            order.setStatus(Order.STATUS_PENDING);
            orderMapper.insert(order);

            log.info("普通下单成功 userId={} productId={} orderNo={}", userId, dto.getProductId(), order.getOrderNo());
            return order;
        } catch (Exception e) {
            // 下单失败，补偿回滚库存
            try {
                String compensateUrl = stockServiceUrl + "/api/stock/increase?productId=" + dto.getProductId() + "&quantity=" + dto.getQuantity();
                restTemplate.postForObject(compensateUrl, null, ResultVO.class);
            } catch (Exception ex) {
                log.error("库存补偿回滚失败 productId={} error={}", dto.getProductId(), ex.getMessage());
            }
            throw new RuntimeException("订单创建失败: " + e.getMessage());
        }
    }

    @Override
    @DS(DataSourceType.MASTER)
    public Order payOrder(Long userId, String orderNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            List<Order> myOrders = orderMapper.findByUserId(userId);
            order = myOrders.stream()
                    .filter(o -> o.getStatus() == Order.STATUS_TRYING || o.getStatus() == Order.STATUS_PENDING)
                    .filter(o -> o.getProductType() == 1)
                    .findFirst().orElse(null);
        }
        if (order == null) throw new RuntimeException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new RuntimeException("无权操作此订单");

        if (order.getProductType() == 1 && order.getStatus() == Order.STATUS_TRYING) {
            // 秒杀订单 TCC Confirm：本地订单确认
            boolean orderConfirmed = tccOrderParticipant.confirm(order.getOrderNo());
            if (!orderConfirmed) throw new RuntimeException("订单确认失败");

            // 远程库存确认
            try {
                String url = stockServiceUrl + "/api/stock/confirm?productId=" + order.getProductId() + "&quantity=" + order.getQuantity();
                restTemplate.postForObject(url, null, ResultVO.class);
            } catch (Exception e) {
                log.error("远程库存确认失败 orderNo={} error={}", orderNo, e.getMessage());
                // 注意：订单已确认，库存确认失败需要人工对账
            }
        } else if (order.getStatus() != Order.STATUS_PENDING) {
            throw new RuntimeException("订单状态不允许支付");
        }

        orderMapper.updateStatus(order.getId(), Order.STATUS_PAID);
        order.setStatus(Order.STATUS_PAID);
        return order;
    }

    @Override
    @DS(DataSourceType.MASTER)
    public Order cancelOrder(Long userId, String orderNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) throw new RuntimeException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new RuntimeException("无权操作此订单");

        if (order.getProductType() == 1 && order.getStatus() == Order.STATUS_TRYING) {
            // 秒杀订单 TCC Cancel
            boolean orderCancelled = tccOrderParticipant.cancel(orderNo);
            if (!orderCancelled) throw new RuntimeException("订单取消失败");

            // 远程库存取消
            try {
                String url = stockServiceUrl + "/api/stock/cancel?productId=" + order.getProductId() + "&quantity=" + order.getQuantity();
                restTemplate.postForObject(url, null, ResultVO.class);
            } catch (Exception e) {
                log.error("远程库存取消失败 orderNo={} error={}", orderNo, e.getMessage());
            }
        } else if (order.getStatus() == Order.STATUS_PENDING) {
            orderMapper.updateStatus(order.getId(), Order.STATUS_CANCELLED);
            if (order.getProductType() == 0) {
                // 普通订单：远程回滚库存
                try {
                    String url = stockServiceUrl + "/api/stock/increase?productId=" + order.getProductId() + "&quantity=" + order.getQuantity();
                    restTemplate.postForObject(url, null, ResultVO.class);
                } catch (Exception e) {
                    log.error("普通订单库存回滚失败 orderNo={} error={}", orderNo, e.getMessage());
                }
            } else if (order.getProductType() == 1) {
                // 已confirm的秒杀订单取消：远程释放库存
                try {
                    String url = stockServiceUrl + "/api/stock/cancel?productId=" + order.getProductId() + "&quantity=" + order.getQuantity();
                    restTemplate.postForObject(url, null, ResultVO.class);
                    redisUtils.increment(SECKILL_STOCK_KEY + order.getProductId(), order.getQuantity());
                } catch (Exception e) {
                    log.error("秒杀订单库存释放失败 orderNo={} error={}", orderNo, e.getMessage());
                }
            }
        } else {
            throw new RuntimeException("只有待支付的订单可以取消");
        }

        order.setStatus(Order.STATUS_CANCELLED);
        return order;
    }

    @Override
    @DS(DataSourceType.MASTER)
    public List<Order> getMyOrders(Long userId) {
        return orderMapper.findByUserId(userId);
    }

    @Override
    @DS(DataSourceType.SLAVE)
    public Order getByOrderNo(String orderNo) {
        Order o = orderMapper.findByOrderNo(orderNo);
        if (o == null) throw new RuntimeException("订单不存在");
        return o;
    }

    private String genOrderNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return ts + rand;
    }
}
