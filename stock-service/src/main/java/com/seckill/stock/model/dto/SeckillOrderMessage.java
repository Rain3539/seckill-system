package com.seckill.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long messageId;
    private Long userId;
    private Long seckillProductId;
    private Integer quantity;
    private String productName;
    private BigDecimal seckillPrice;
}
