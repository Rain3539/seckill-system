package com.seckill.stock.service;

import com.seckill.stock.model.entity.Product;
import java.util.List;

public interface ProductService {
    List<Product> listOnSale();
    Product getById(Long id);
}
