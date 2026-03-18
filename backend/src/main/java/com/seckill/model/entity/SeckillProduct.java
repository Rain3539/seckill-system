package com.seckill.model.entity;

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

    /** 计算秒杀活动状态（非DB字段）*/
    public String getActivityStatus() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startTime)) return "upcoming";  // 未开始
        if (now.isAfter(endTime))    return "ended";      // 已结束
        if (availStock <= 0)         return "soldout";    // 已售罄
        return "active";                                   // 进行中
    }
}
