package com.mall.pro.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class IdCardSecurityUtil {
    private static final String SALT =" MALL_REALNAME_SALT_2026";
    /**
     * 身份证脱敏：保留前6位和后4位，中间8位用星号遮蔽 (符合行业合规标准)
     * 例如: 440102199801011234 -> 440102********1234
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) return "******";
        int len = idCard.length();
        return idCard.substring(0, 6) + "********" + idCard.substring(len - 4);
    }
    /**
     * 姓名脱敏：保留首字，其余字遮蔽
     * 例如: 张三 -> 张*；诸葛孔明 -> 诸**明
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) return "*";
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
    public static String sha256WithSalt(String idCard) {
        if (idCard == null || idCard.trim().isEmpty()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest((idCard.trim() + SALT).getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }
}
