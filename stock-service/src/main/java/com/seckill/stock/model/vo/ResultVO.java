package com.seckill.stock.model.vo;

import lombok.Data;

@Data
public class ResultVO<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ResultVO<T> success(T data) {
        ResultVO<T> vo = new ResultVO<>();
        vo.code = 200;
        vo.message = "success";
        vo.data = data;
        return vo;
    }

    public static <T> ResultVO<T> success() {
        return success(null);
    }

    public static <T> ResultVO<T> fail(int code, String message) {
        ResultVO<T> vo = new ResultVO<>();
        vo.code = code;
        vo.message = message;
        return vo;
    }

    public static <T> ResultVO<T> fail(String message) {
        return fail(500, message);
    }
}
