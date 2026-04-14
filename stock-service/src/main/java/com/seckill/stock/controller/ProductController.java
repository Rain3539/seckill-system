package com.seckill.stock.controller;

import com.seckill.stock.model.entity.Product;
import com.seckill.stock.model.vo.ResultVO;
import com.seckill.stock.service.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/list")
    public ResultVO<List<Product>> list() {
        return ResultVO.success(productService.listOnSale());
    }

    @GetMapping("/{id}")
    public ResultVO<Product> detail(@PathVariable Long id) {
        Product p = productService.getById(id);
        if (p == null) return ResultVO.fail(404, "商品不存在");
        return ResultVO.success(p);
    }
}
