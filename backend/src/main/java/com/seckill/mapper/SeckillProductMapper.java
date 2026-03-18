package com.seckill.mapper;

import com.seckill.model.entity.SeckillProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SeckillProductMapper {
    SeckillProduct findById(@Param("id") Long id);
    List<SeckillProduct> findAllActive();

    /** 乐观锁扣减库存，防超卖核心 */
    int decreaseStock(@Param("id") Long id,
                      @Param("quantity") Integer quantity,
                      @Param("version") Integer version);
}
