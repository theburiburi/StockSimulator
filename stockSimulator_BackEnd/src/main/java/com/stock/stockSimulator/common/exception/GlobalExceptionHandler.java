package com.stock.stockSimulator.common.exception;


import com.stock.stockSimulator.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
//@@@
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //비지니스적 에러
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBusinessException(BusinessException e){
        log.error("Business Error: {}", e.getMessage());
        return  ApiResponse.fail(e.getMessage());
    }

    //파라미터 등 에러
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e){
        log.error("Runtime Error: ", e);
        return ApiResponse.fail(e.getMessage());
    }

    //그 이외 알 수 없는 거
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e){
        log.error("Unknown Error: ", e);
        return ApiResponse.fail("예상치 못한 에러");
    }
}
