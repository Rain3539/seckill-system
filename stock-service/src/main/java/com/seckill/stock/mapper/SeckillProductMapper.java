package com.seckill.stock.mapper;

import com.seckill.stock.model.entity.SeckillProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SeckillProductMapper {
    SeckillProduct findById(@Param("id") Long id);
    List<SeckillProduct> findAllActive();
    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity, @Param("version") Integer version);
    int confirmStock(@Param("id") Long id, @Param("quantity") Integer quantity, @Param("version") Integer version);
    int cancelStock(@Param("id") Long id, @Param("quantity") Integer quantity, @Param("version") Integer version);
}
