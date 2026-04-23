package com.seckill.order.controller;

import com.seckill.order.model.vo.ResultVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 演示 Nacos 配置动态更新能力。
 * 在 Nacos 控制台修改 seckill-demo.greeting 后，无需重启即可生效。
 */
@RestController
@RequestMapping("/config")
@RefreshScope
public class ConfigController {

    @Value("${seckill-demo.greeting:Hello from order-service}")
    private String greeting;

    @Value("${seckill-demo.rate-limit:100}")
    private int rateLimit;

    @GetMapping("/info")
    public ResultVO<Map<String, Object>> info() {
        Map<String, Object> data = new HashMap<>();
        data.put("greeting", greeting);
        data.put("rateLimit", rateLimit);
        data.put("instanceId", System.getProperty("INSTANCE_ID", "local"));
        return ResultVO.success(data);
    }
}
