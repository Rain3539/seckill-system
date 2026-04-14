package com.seckill.stock.controller;

import com.seckill.stock.mapper.ProductMapper;
import com.seckill.stock.model.vo.ResultVO;
import com.seckill.stock.tcc.TccStockParticipant;
import com.seckill.stock.utils.RedisUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stock")
public class StockController {

    private final TccStockParticipant stockParticipant;
    private final ProductMapper productMapper;
    private final RedisUtils redisUtils;

    public StockController(TccStockParticipant stockParticipant,
                           ProductMapper productMapper,
                           RedisUtils redisUtils) {
        this.stockParticipant = stockParticipant;
        this.productMapper = productMapper;
        this.redisUtils = redisUtils;
    }

    @PostMapping("/decrease")
    public ResultVO<Boolean> decreaseStock(@RequestParam Long productId, @RequestParam int quantity) {
        int updated = productMapper.decreaseStock(productId, quantity);
        return updated > 0 ? ResultVO.success(true) : ResultVO.fail("库存不足");
    }

    @PostMapping("/increase")
    public ResultVO<Boolean> increaseStock(@RequestParam Long productId, @RequestParam int quantity) {
        productMapper.increaseStock(productId, quantity);
        return ResultVO.success(true);
    }

    @PostMapping("/reserve")
    public ResultVO<Boolean> reserve(@RequestParam Long productId, @RequestParam int quantity) {
        boolean ok = stockParticipant.tryReserve(productId, quantity);
        return ok ? ResultVO.success(true) : ResultVO.fail("库存预留失败");
    }

    @PostMapping("/confirm")
    public ResultVO<Boolean> confirm(@RequestParam Long productId, @RequestParam int quantity) {
        boolean ok = stockParticipant.confirm(productId, quantity);
        return ok ? ResultVO.success(true) : ResultVO.fail("库存确认失败");
    }

    @PostMapping("/cancel")
    public ResultVO<Boolean> cancel(@RequestParam Long productId, @RequestParam int quantity) {
        boolean ok = stockParticipant.cancel(productId, quantity);
        if (ok) {
            redisUtils.increment("seckill:stock:" + productId, quantity);
        }
        return ok ? ResultVO.success(true) : ResultVO.fail("库存取消失败");
    }
}
