package com.seckill.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Order {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long productId;
    private Integer productType;   // 0普通 1秒杀
    private String productName;    // 冗余商品名快照
    private Integer quantity;
    private BigDecimal unitPrice;  // 下单时价格快照
    private BigDecimal amount;
    private Integer status;        // 0待支付 1已支付 2已取消
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
