package com.seckill.mapper;

import com.seckill.model.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductMapper {
    Product findById(@Param("id") Long id);
    List<Product> findAllOnSale();
    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);
    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
