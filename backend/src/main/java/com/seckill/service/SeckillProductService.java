package com.seckill.service;

import com.seckill.model.entity.SeckillProduct;
import java.util.List;

public interface SeckillProductService {
    List<SeckillProduct> listAll();
    SeckillProduct getById(Long id);
    /** 启动时预热所有秒杀商品库存到Redis */
    void warmUpAllStock();
    /** 预热单个商品 */
    void warmUpStock(Long seckillProductId);
}
