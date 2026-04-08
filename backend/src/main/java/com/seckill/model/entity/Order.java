package com.seckill.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Order {

    /** TCC Try阶段：库存已预留，订单待确认 */
    public static final int STATUS_TRYING    = -1;
    /** 待支付 */
    public static final int STATUS_PENDING   = 0;
    /** 已支付 */
    public static final int STATUS_PAID      = 1;
    /** 已取消 */
    public static final int STATUS_CANCELLED = 2;

    private Long id;
    private String orderNo;
    private Long userId;
    private Long productId;
    private Integer productType;   // 0普通 1秒杀
    private String productName;    // 冗余商品名快照
    private Integer quantity;
    private BigDecimal unitPrice;  // 下单时价格快照
    private BigDecimal amount;
    private Integer status;        // -1TCC预留中 0待支付 1已支付 2已取消
    private LocalDateTime timeoutAt;  // TCC超时时间
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
