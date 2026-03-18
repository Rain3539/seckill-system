package com.seckill.exception;

import com.seckill.model.vo.ResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：统一返回 400 + 中文提示，不打印堆栈 */
    @ExceptionHandler(RuntimeException.class)
    public ResultVO<Void> handleBusiness(RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResultVO.fail(400, e.getMessage());
    }

    /** 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO<Void> handleValidation(MethodArgumentNotValidException e) {
        BindingResult br = e.getBindingResult();
        String msg = br.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("参数错误");
        return ResultVO.fail(422, msg);
    }

    /** 未知异常：打印完整堆栈 */
    @ExceptionHandler(Exception.class)
    public ResultVO<Void> handleUnknown(Exception e) {
        log.error("未知异常", e);
        return ResultVO.fail(500, "服务器内部错误");
    }
}
