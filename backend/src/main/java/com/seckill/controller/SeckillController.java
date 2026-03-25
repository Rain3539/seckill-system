package com.seckill.controller;

import com.seckill.model.dto.SeckillRequestDTO;
import com.seckill.model.entity.Order;
import com.seckill.model.entity.SeckillProduct;
import com.seckill.model.vo.ResultVO;
import com.seckill.service.SeckillProductService;
import com.seckill.service.SeckillService;
import com.seckill.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private final SeckillService seckillService;
    private final SeckillProductService seckillProductService;
    private final JwtUtils jwtUtils;

    public SeckillController(SeckillService seckillService,
                             SeckillProductService seckillProductService,
                             JwtUtils jwtUtils) {
        this.seckillService = seckillService;
        this.seckillProductService = seckillProductService;
        this.jwtUtils = jwtUtils;
    }

    /** 秒杀商品列表 GET /api/seckill/list */
    @GetMapping("/list")
    public ResultVO<List<SeckillProduct>> list() {
        return ResultVO.success(seckillProductService.listAll());
    }

    /** 秒杀商品详情 GET /api/seckill/{id} */
    @GetMapping("/{id}")
    public ResultVO<SeckillProduct> detail(@PathVariable Long id) {
        return ResultVO.success(seckillProductService.getById(id));
    }

    /**
     * 执行秒杀（异步）POST /api/seckill/do
     * Redis 预减库存 + Kafka 异步处理，立即返回排队结果
     */
    @PostMapping("/do")
    public ResultVO<Map<String, Object>> doSeckill(@Valid @RequestBody SeckillRequestDTO dto,
                                                    HttpServletRequest request) {
        Long userId = extractUserId(request);
        Map<String, Object> result = seckillService.doSeckill(userId, dto);
        return ResultVO.success(result);
    }

    /**
     * 查询秒杀订单结果 GET /api/seckill/order/{seckillProductId}
     * 前端轮询此接口获取最终订单信息
     */
    @GetMapping("/order/{seckillProductId}")
    public ResultVO<Order> getSeckillOrder(@PathVariable Long seckillProductId,
                                           HttpServletRequest request) {
        Long userId = extractUserId(request);
        Order order = seckillService.getSeckillOrder(userId, seckillProductId);
        return ResultVO.success(order);
    }

    /** 手动预热单个商品库存 POST /api/seckill/warmup/{id} */
    @PostMapping("/warmup/{id}")
    public ResultVO<Void> warmUp(@PathVariable Long id) {
        seckillProductService.warmUpStock(id);
        return ResultVO.success();
    }

    private Long extractUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            if (jwtUtils.validateToken(token)) return jwtUtils.getUserIdFromToken(token);
        }
        throw new RuntimeException("未登录或Token已过期，请重新登录");
    }
}
