package com.seckill.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Kafka 秒杀订单消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 雪花算法生成的唯一消息 ID */
    private Long messageId;

    private Long userId;
    private Long seckillProductId;
    private Integer quantity;

    /** 商品名称快照 */
    private String productName;
    /** 秒杀单价快照 */
    private BigDecimal seckillPrice;
}
