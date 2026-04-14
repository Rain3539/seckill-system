package com.seckill.stock.controller;

import com.seckill.stock.model.dto.SeckillRequestDTO;
import com.seckill.stock.model.entity.Order;
import com.seckill.stock.model.entity.SeckillProduct;
import com.seckill.stock.model.vo.ResultVO;
import com.seckill.stock.service.SeckillProductService;
import com.seckill.stock.service.SeckillService;
import com.seckill.stock.utils.JwtUtils;
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

    @GetMapping("/list")
    public ResultVO<List<SeckillProduct>> list() {
        return ResultVO.success(seckillProductService.listAll());
    }

    @GetMapping("/{id}")
    public ResultVO<SeckillProduct> detail(@PathVariable Long id) {
        return ResultVO.success(seckillProductService.getById(id));
    }

    @PostMapping("/do")
    public ResultVO<Map<String, Object>> doSeckill(@Valid @RequestBody SeckillRequestDTO dto,
                                                    HttpServletRequest request) {
        Long userId = extractUserId(request);
        Map<String, Object> result = seckillService.doSeckill(userId, dto);
        return ResultVO.success(result);
    }

    @GetMapping("/order/{seckillProductId}")
    public ResultVO<Order> getSeckillOrder(@PathVariable Long seckillProductId,
                                           HttpServletRequest request) {
        Long userId = extractUserId(request);
        Order order = seckillService.getSeckillOrder(userId, seckillProductId);
        return ResultVO.success(order);
    }

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
