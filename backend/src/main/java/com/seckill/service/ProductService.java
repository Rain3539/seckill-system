package com.seckill.service;

import com.seckill.model.entity.Product;
import java.util.List;

public interface ProductService {
    /** 获取所有上架普通商品列表（带Redis缓存） */
    List<Product> listOnSale();
    /** 获取单个普通商品详情（带缓存穿透/击穿/雪崩保护） */
    Product getById(Long id);
}
