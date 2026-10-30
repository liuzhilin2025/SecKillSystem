package com.practice.flashsale.order.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/19 13:36
 * @Version: v1.0.0
 * @Description: Spring Retry 重试日志监听器
 **/
@Component
@Slf4j
public class RetryLogListener implements RetryListener {

    /**
     * close() 回调方法
     * 整个重试流程结束时回调，无论最终成功还是失败
     */
    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        // 只有在 “经历过失败，但最终重试成功” 时，打印一下日志，一次就成功的正常请求不打
        if (throwable == null && context.getRetryCount() > 0) {
            log.info("==> 发送重试：重试流程结束，最终发送成功，共经历 {} 次失败", context.getRetryCount());
        }
    }

    /**
     * onError() 回调方法
     * 每一次尝试失败后回调
     */
    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        // 已失败的尝试次数（从 1 开始）
        int failedCount = context.getRetryCount();
        // 最大尝试次数
        Object maxAttempts = context.getAttribute(RetryContext.MAX_ATTEMPTS);

        if (maxAttempts instanceof Integer limit && failedCount < limit) {
            // 还没到上限，打印一下进行到第几次重试了
            log.warn("==> 发送重试：第 {} 次尝试失败，即将进行第 {} 次重试（退避等待中）, 异常信息：{}",
                    failedCount, failedCount, throwable.getMessage());
        } else {
            // 已到达上限，重试结束，将异常原样抛给调用方
            log.error("==> 发送重试：第 {} 次尝试失败，已达最大尝试次数 {}（首次 + {} 次重试），停止重试",
                    failedCount, maxAttempts, (Integer) maxAttempts - 1);
        }
    }
}
