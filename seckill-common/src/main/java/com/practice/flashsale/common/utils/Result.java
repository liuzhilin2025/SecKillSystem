package com.practice.flashsale.common.utils;

import com.practice.flashsale.common.exception.BaseExceptionInterface;
import com.practice.flashsale.common.exception.BizException;
import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    // 是否成功，默认为 true
    private boolean success = true;
    // 响应消息
    private String message;
    // 异常码
    private String errorCode;
    // 响应数据
    private T data;

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(String errorMessage) {
        Result<T> Result = new Result<>();
        Result.setSuccess(false);
        Result.setMessage(errorMessage);
        return Result;
    }

    public static <T> Result<T> fail(String errorCode, String errorMessage) {
        Result<T> Result = new Result<>();
        Result.setSuccess(false);
        Result.setErrorCode(errorCode);
        Result.setMessage(errorMessage);
        return Result;
    }

    public static <T> Result<T> fail(BizException bizException) {
        Result<T> Result = new Result<>();
        Result.setSuccess(false);
        Result.setErrorCode(bizException.getErrorCode());
        Result.setMessage(bizException.getErrorMessage());
        return Result;
    }

    public static <T> Result<T> fail(BaseExceptionInterface baseExceptionInterface) {
        Result<T> Result = new Result<>();
        Result.setSuccess(false);
        Result.setErrorCode(baseExceptionInterface.getErrorCode());
        Result.setMessage(baseExceptionInterface.getErrorMessage());
        return Result;
    }
}
