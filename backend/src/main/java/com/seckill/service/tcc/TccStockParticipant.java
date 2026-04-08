package com.seckill.service.tcc;

import com.seckill.mapper.SeckillProductMapper;
import com.seckill.model.entity.SeckillProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TCC 库存参与者
 * <ul>
 *   <li>Try:     decreaseStock — avail_stock -= quantity, locked_stock += quantity（已有）</li>
 *   <li>Confirm: confirmStock  — locked_stock -= quantity（永久消费）</li>
 *   <li>Cancel:  cancelStock   — locked_stock -= quantity, avail_stock += quantity（释放预留）</li>
 * </ul>
 */
@Component
public class TccStockParticipant {

    private static final Logger log = LoggerFactory.getLogger(TccStockParticipant.class);

    private final SeckillProductMapper seckillProductMapper;

    public TccStockParticipant(SeckillProductMapper seckillProductMapper) {
        this.seckillProductMapper = seckillProductMapper;
    }

    /**
     * Try: 预留库存（复用已有的 decreaseStock）
     *
     * @return true 表示预留成功
     */
    public boolean tryReserve(Long productId, int quantity) {
        SeckillProduct sp = seckillProductMapper.findById(productId);
        if (sp == null) {
            log.warn("库存预留失败：商品不存在 productId={}", productId);
            return false;
        }
        int updated = seckillProductMapper.decreaseStock(productId, quantity, sp.getVersion());
        if (updated == 0) {
            log.warn("库存预留失败（乐观锁冲突或库存不足）productId={} version={}", productId, sp.getVersion());
            return false;
        }
        log.info("库存预留成功 productId={} quantity={} version={}", productId, quantity, sp.getVersion());
        return true;
    }

    /**
     * Confirm: 确认扣减（locked_stock 永久消费）
     *
     * @return true 表示确认成功
     */
    public boolean confirm(Long productId, int quantity) {
        SeckillProduct sp = seckillProductMapper.findById(productId);
        if (sp == null) {
            log.error("Confirm库存失败：商品不存在 productId={}", productId);
            return false;
        }
        int updated = seckillProductMapper.confirmStock(productId, quantity, sp.getVersion());
        if (updated == 0) {
            log.error("Confirm库存失败（乐观锁冲突）productId={} version={}", productId, sp.getVersion());
            return false;
        }
        log.info("Confirm库存成功 productId={} quantity={}", productId, quantity);
        return true;
    }

    /**
     * Cancel: 释放预留库存（locked_stock -> avail_stock）
     *
     * @return true 表示释放成功
     */
    public boolean cancel(Long productId, int quantity) {
        SeckillProduct sp = seckillProductMapper.findById(productId);
        if (sp == null) {
            log.error("Cancel库存失败：商品不存在 productId={}", productId);
            return false;
        }
        int updated = seckillProductMapper.cancelStock(productId, quantity, sp.getVersion());
        if (updated == 0) {
            log.error("Cancel库存失败（乐观锁冲突）productId={} version={}", productId, sp.getVersion());
            return false;
        }
        log.info("Cancel库存成功 productId={} quantity={}", productId, quantity);
        return true;
    }
}
