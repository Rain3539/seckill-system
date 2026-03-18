package com.seckill.controller;

import com.seckill.model.vo.ResultVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 实例信息接口
 * 用于负载均衡验证：调用 GET /api/instance 可查看当前请求被哪个后端处理
 * JMeter 压测时在响应数据中可观察 instanceId 字段的分布情况
 */
@RestController
@RequestMapping("/instance")
public class InstanceController {

    @Value("${INSTANCE_ID:local}")
    private String instanceId;

    /**
     * 返回当前处理请求的实例ID
     * GET /api/instance
     */
    @GetMapping
    public ResultVO<Map<String, String>> info() {
        return ResultVO.success(Map.of(
                "instanceId", instanceId,
                "message", "Request handled by: " + instanceId
        ));
    }
}
