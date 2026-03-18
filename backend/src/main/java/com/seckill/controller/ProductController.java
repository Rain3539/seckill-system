package com.seckill.controller;

import com.seckill.model.entity.Product;
import com.seckill.model.vo.ResultVO;
import com.seckill.service.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 普通商品列表 GET /api/product/list */
    @GetMapping("/list")
    public ResultVO<List<Product>> list() {
        return ResultVO.success(productService.listOnSale());
    }

    /** 普通商品详情 GET /api/product/{id} */
    @GetMapping("/{id}")
    public ResultVO<Product> detail(@PathVariable Long id) {
        Product p = productService.getById(id);
        if (p == null) return ResultVO.fail(404, "商品不存在");
        return ResultVO.success(p);
    }
}
