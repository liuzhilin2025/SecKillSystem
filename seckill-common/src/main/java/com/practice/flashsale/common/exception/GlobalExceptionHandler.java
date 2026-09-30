package com.practice.flashsale.common.exception;

import com.practice.flashsale.common.enums.ResultCodeEnum;
import com.practice.flashsale.common.utils.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DuplicateKeyException;


import java.util.Optional;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获自定义业务异常
     *
     * @return
     */
    @ExceptionHandler({BizException.class})
    @ResponseBody
    public Result<Object> handleBizException(HttpServletRequest request, BizException e) {
        log.warn("{} request fail, errorCode: {}, errorMessage: {}", request.getRequestURI(), e.getErrorCode(), e.getErrorMessage());
        return Result.fail(e);
    }

    /**
     * 捕获参数校验异常
     *
     * @return
     */
    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseBody
    public Result<Object> handleMethodArgumentNotValidException(HttpServletRequest request, MethodArgumentNotValidException e) {
        // 参数错误异常码
        String errorCode = ResultCodeEnum.PARAM_NOT_VALID.getErrorCode();

        // 获取 BindingResult
        BindingResult bindingResult = e.getBindingResult();

        StringBuilder sb = new StringBuilder();

        // 获取校验不通过的字段，并组合错误信息，格式为： email 邮箱格式不正确, 当前值: '123124qq.com';
        Optional.ofNullable(bindingResult.getFieldErrors()).ifPresent(errors -> {
            errors.forEach(error ->
                    sb.append(error.getField())
                            .append(" ")
                            .append(error.getDefaultMessage())
                            .append(", 当前值: '")
                            .append(error.getRejectedValue())
                            .append("'; ")
            );
        });

        // 错误信息
        String errorMessage = sb.toString();

        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);

        return Result.fail(errorCode, errorMessage);

    }

    /**
     * 其他类型异常
     *
     * @param request
     * @param e
     * @return
     */
    @ExceptionHandler({Exception.class})
    @ResponseBody
    public Result<Object> handleOtherException(HttpServletRequest request, Exception e) {
        log.error("{} request error, ", request.getRequestURI(), e);
        return Result.fail(ResultCodeEnum.SYSTEM_ERROR);
    }

    /**
     * 唯一索引冲突兜底（并发注册/重复提交）
     */
    @ExceptionHandler({DuplicateKeyException.class})
    @ResponseBody
    public Result<Object> handleDuplicateKeyException(HttpServletRequest request, DuplicateKeyException e) {
        log.warn("{} request fail, 唯一索引冲突: {}", request.getRequestURI(), e.getMessage());
        return Result.fail(ResultCodeEnum.PARAM_NOT_VALID.getErrorCode(), "数据已存在，请勿重复提交");
    }

}