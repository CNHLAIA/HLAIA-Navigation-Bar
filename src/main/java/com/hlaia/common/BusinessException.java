package com.hlaia.common;

import lombok.Getter;

/**
 * 【业务异常类】—— 用于在业务逻辑中主动抛出的异常
 *
 * 什么是 RuntimeException？
 *   Java 异常分两种：
 *   - 受检异常（Checked Exception）：必须用 try-catch 处理或 throws 声明
 *   - 非受检异常（RuntimeException）：不需要强制处理，会自动向上传递
 *   我们继承 RuntimeException，这样在 service 层抛出时不需要每个方法都写 throws
 *
 * 使用场景举例：
 *   if (user == null) {
 *       throw new BusinessException(ErrorCode.USER_NOT_FOUND);
 *   }
 *   抛出后，GlobalExceptionHandler 会捕获它并返回统一的 JSON 错误响应
 *
 * 为什么需要自定义异常而不是直接用 RuntimeException？
 *   1. 可以携带错误码（code），而不仅仅是错误信息
 *   2. GlobalExceptionHandler 可以专门针对 BusinessException 做定制化处理
 *   3. 让代码更清晰：看到 BusinessException 就知道是业务逻辑出了问题
 */
@Getter   // Lombok 注解：自动生成 getter 方法（这里主要是为了生成 getCode()）
public class BusinessException extends RuntimeException {

    // 错误码，和 ErrorCode 枚举中的 code 对应
    private final int code;   // final 表示这个字段一旦赋值就不能修改，保证不可变性

    /**
     * 构造方法1：通过 ErrorCode 枚举创建异常
     * 使用方式：throw new BusinessException(ErrorCode.USER_NOT_FOUND)
     *
     * @param errorCode 错误码枚举值
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());  // 调用父类 RuntimeException 的构造方法，设置异常信息
        this.code = errorCode.getCode();
    }

    /**
     * 构造方法2：直接传入错误码和信息
     * 使用方式：throw new BusinessException(999, "自定义错误")
     *
     * @param code    错误码数字
     * @param message 错误描述信息
     */
    public BusinessException(int code, String message) {
        super(message);  // 调用父类 RuntimeException 的构造方法，设置异常信息
        this.code = code;
    }
}
