package com.seckill.stock.tcc;

import com.seckill.stock.mapper.SeckillProductMapper;
import com.seckill.stock.model.entity.SeckillProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TccStockParticipant {

    private static final Logger log = LoggerFactory.getLogger(TccStockParticipant.class);

    private final SeckillProductMapper seckillProductMapper;

    public TccStockParticipant(SeckillProductMapper seckillProductMapper) {
        this.seckillProductMapper = seckillProductMapper;
    }

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
