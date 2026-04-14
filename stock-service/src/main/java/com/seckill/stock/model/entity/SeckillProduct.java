package com.seckill.stock.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillProduct {
    private Long id;
    private String name;
    private String description;
    private BigDecimal originPrice;
    private BigDecimal seckillPrice;
    private Integer totalStock;
    private Integer availStock;
    private Integer lockedStock;
    private Integer version;
    private String imageUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getActivityStatus() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startTime)) return "upcoming";
        if (now.isAfter(endTime)) return "ended";
        if (availStock <= 0) return "soldout";
        return "active";
    }
}
