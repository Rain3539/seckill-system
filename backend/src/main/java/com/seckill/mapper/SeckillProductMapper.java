package com.seckill.mapper;

import com.seckill.model.entity.SeckillProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SeckillProductMapper {
    SeckillProduct findById(@Param("id") Long id);
    List<SeckillProduct> findAllActive();

    /** TCC Try: 乐观锁预留库存 (avail->locked)，防超卖核心 */
    int decreaseStock(@Param("id") Long id,
                      @Param("quantity") Integer quantity,
                      @Param("version") Integer version);

    /** TCC Confirm: 确认扣减 (locked_stock减少，永久消费) */
    int confirmStock(@Param("id") Long id,
                     @Param("quantity") Integer quantity,
                     @Param("version") Integer version);

    /** TCC Cancel: 释放预留 (locked_stock回退到avail_stock) */
    int cancelStock(@Param("id") Long id,
                    @Param("quantity") Integer quantity,
                    @Param("version") Integer version);
}
