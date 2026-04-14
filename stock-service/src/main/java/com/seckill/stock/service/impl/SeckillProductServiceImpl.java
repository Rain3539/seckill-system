package com.seckill.stock.service.impl;

import com.seckill.stock.datasource.DS;
import com.seckill.stock.datasource.DataSourceType;
import com.seckill.stock.mapper.SeckillProductMapper;
import com.seckill.stock.model.entity.SeckillProduct;
import com.seckill.stock.service.SeckillProductService;
import com.seckill.stock.utils.RedisUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillProductServiceImpl implements SeckillProductService {

    private static final Logger log = LoggerFactory.getLogger(SeckillProductServiceImpl.class);
    public static final String STOCK_KEY_PREFIX = "seckill:stock:";

    private final SeckillProductMapper seckillProductMapper;
    private final RedisUtils redisUtils;

    public SeckillProductServiceImpl(SeckillProductMapper seckillProductMapper, RedisUtils redisUtils) {
        this.seckillProductMapper = seckillProductMapper;
        this.redisUtils = redisUtils;
    }

    @PostConstruct
    @Override
    @DS(DataSourceType.MASTER)
    public void warmUpAllStock() {
        log.info("warmUpAllStock -> MASTER DB");
        List<SeckillProduct> list = seckillProductMapper.findAllActive();
        for (SeckillProduct sp : list) {
            String key = STOCK_KEY_PREFIX + sp.getId();
            redisUtils.set(key, String.valueOf(sp.getAvailStock()), 24, TimeUnit.HOURS);
            log.info("库存预热: seckillProductId={} stock={}", sp.getId(), sp.getAvailStock());
        }
        log.info(">>> 秒杀库存预热完成，共预热 {} 个商品", list.size());
    }

    @Override
    @DS(DataSourceType.MASTER)
    public void warmUpStock(Long seckillProductId) {
        SeckillProduct sp = seckillProductMapper.findById(seckillProductId);
        if (sp != null) {
            String key = STOCK_KEY_PREFIX + seckillProductId;
            redisUtils.set(key, String.valueOf(sp.getAvailStock()), 24, TimeUnit.HOURS);
            log.info("库存预热: seckillProductId={} stock={}", seckillProductId, sp.getAvailStock());
        }
    }

    @Override
    @DS(DataSourceType.SLAVE)
    public List<SeckillProduct> listAll() {
        return seckillProductMapper.findAllActive();
    }

    @Override
    @DS(DataSourceType.SLAVE)
    public SeckillProduct getById(Long id) {
        return seckillProductMapper.findById(id);
    }
}
