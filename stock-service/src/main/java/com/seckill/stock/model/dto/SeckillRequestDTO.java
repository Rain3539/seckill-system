package com.seckill.stock.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SeckillRequestDTO {
    @NotNull(message = "秒杀商品ID不能为空")
    private Long seckillProductId;
    @Min(value = 1, message = "数量至少为1")
    private Integer quantity = 1;
}
