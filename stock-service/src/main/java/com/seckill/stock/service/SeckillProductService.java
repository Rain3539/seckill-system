package com.seckill.stock.service;

import com.seckill.stock.model.entity.SeckillProduct;
import java.util.List;

public interface SeckillProductService {
    List<SeckillProduct> listAll();
    SeckillProduct getById(Long id);
    void warmUpAllStock();
    void warmUpStock(Long seckillProductId);
}
