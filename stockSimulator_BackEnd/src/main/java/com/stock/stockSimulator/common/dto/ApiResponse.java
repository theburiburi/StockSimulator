package com.stock.stockSimulator.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private String status;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data){
        return new ApiResponse<>("SUCCESS", "요청이 정상 처리되었습니다.", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data){
        return new ApiResponse<>("SUCCESS", message, data);
    }

    public static ApiResponse<Void> fail(String message){
        return new ApiResponse<>("FAIL", message, null);
    }
}
