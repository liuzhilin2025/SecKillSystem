package com.practice.flashsale.common.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 昵称生成工具
 */
public final class NicknameUtils {

    /**
     * 昵称前缀
     */
    private static final String PREFIX = "用户";

    /**
     * 随机数字下界（含）：100000
     */
    private static final int MIN = 100_000;

    /**
     * 随机数字上界（不含）：1000000
     */
    private static final int MAX = 1_000_000;

    private NicknameUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 生成随机昵称，格式：用户 + 6 位随机数字，例如 用户382910
     *
     * @return 昵称
     */
    public static String generate() {
        return PREFIX + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));

    }
}
