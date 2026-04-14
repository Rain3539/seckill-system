package com.seckill.stock.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.stock.datasource.DS;
import com.seckill.stock.datasource.DataSourceType;
import com.seckill.stock.mapper.ProductMapper;
import com.seckill.stock.model.entity.Product;
import com.seckill.stock.service.ProductService;
import com.seckill.stock.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);
    private static final String PRODUCT_LIST_KEY = "product:list";
    private static final String PRODUCT_KEY_PREFIX = "product:detail:";
    private static final String LOCK_KEY_PREFIX = "lock:product:";
    private static final String NULL_VALUE = "NULL";
    private static final long LIST_TTL = 300;
    private static final long DETAIL_TTL = 1800;
    private static final long NULL_TTL = 300;
    private static final long LOCK_TTL = 10;

    private final ProductMapper productMapper;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ProductServiceImpl(ProductMapper productMapper, RedisUtils redisUtils) {
        this.productMapper = productMapper;
        this.redisUtils = redisUtils;
    }

    @Override
    @DS(DataSourceType.SLAVE)
    public List<Product> listOnSale() {
        String cached = redisUtils.get(PRODUCT_LIST_KEY);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<Product>>() {});
            } catch (Exception e) {
                log.warn("商品列表缓存反序列化失败，重新查DB", e);
            }
        }
        log.info("[RW-Split] listOnSale -> SLAVE DB");
        List<Product> list = productMapper.findAllOnSale();
        try {
            long ttl = LIST_TTL + new Random().nextInt(120) - 60;
            redisUtils.set(PRODUCT_LIST_KEY, objectMapper.writeValueAsString(list), ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("商品列表写入缓存失败", e);
        }
        return list;
    }

    @Override
    @DS(DataSourceType.SLAVE)
    public Product getById(Long id) {
        String key = PRODUCT_KEY_PREFIX + id;
        String cached = redisUtils.get(key);
        if (cached != null) {
            if (NULL_VALUE.equals(cached)) return null;
            try {
                return objectMapper.readValue(cached, Product.class);
            } catch (Exception e) {
                log.warn("商品详情缓存反序列化失败 id={}", id, e);
            }
        }
        String lockKey = LOCK_KEY_PREFIX + id;
        boolean locked = Boolean.TRUE.equals(redisUtils.setIfAbsent(lockKey, "1", LOCK_TTL, TimeUnit.SECONDS));
        if (!locked) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            return getById(id);
        }
        try {
            cached = redisUtils.get(key);
            if (cached != null) {
                return NULL_VALUE.equals(cached) ? null : objectMapper.readValue(cached, Product.class);
            }
            Product product = productMapper.findById(id);
            if (product == null) {
                redisUtils.set(key, NULL_VALUE, NULL_TTL, TimeUnit.SECONDS);
                return null;
            }
            long ttl = DETAIL_TTL + new Random().nextInt(600) - 300;
            redisUtils.set(key, objectMapper.writeValueAsString(product), ttl, TimeUnit.SECONDS);
            return product;
        } catch (Exception e) {
            log.error("查询商品详情异常 id={}", id, e);
            return productMapper.findById(id);
        } finally {
            redisUtils.delete(lockKey);
        }
    }
}
