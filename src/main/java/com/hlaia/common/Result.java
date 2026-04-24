package com.hlaia.common;

import lombok.Data;
import java.io.Serializable;

/**
 * 【统一响应包装类】—— 所有 API 接口的返回值都使用这个类包装
 *
 * 什么是泛型 <T>？
 *   Result<T> 中的 T 是类型参数，表示 data 字段可以是任何类型。
 *   例如：Result<User> 的 data 是 User 对象，Result<List<Bookmark>> 的 data 是书签列表。
 *   这样一个类就能适配所有接口的返回值类型。
 *
 * 什么是 Serializable？
 *   标记接口，表示这个类的对象可以被序列化（转成字节流）。
 *   在 Web 应用中，对象需要序列化后才能通过网络传输或存入缓存（如 Redis）。
 *
 * 统一响应格式示例：
 *   成功：{"code": 200, "message": "success", "data": {...}}
 *   失败：{"code": 1002, "message": "Invalid username or password", "data": null}
 *
 * 为什么要统一响应格式？
 *   前端只需要按同一种格式解析所有接口的返回值，代码更简洁、更不容易出错。
 */
@Data   // Lombok 注解：自动生成 getter、setter、toString、equals、hashCode 方法
        // 等价于手写所有字段的 getXxx()、setXxx() 等方法
public class Result<T> implements Serializable {

    // 状态码，200 表示成功，其他表示各种错误
    private int code;

    // 提示信息，成功时为 "success"，失败时为具体的错误描述
    private String message;

    // 响应数据，成功时携带实际数据，失败时为 null
    // 泛型 T 表示可以是任何类型（User、List<Bookmark> 等）
    private T data;

    /**
     * 静态工厂方法：创建无数据的成功响应
     * 用于不需要返回数据的操作，如删除、更新
     *
     * 使用方式：return Result.success();
     * 返回示例：{"code": 200, "message": "success", "data": null}
     */
    public static <T> Result<T> success() {
        return success(null);  // 调用下面的 success(T data) 方法，传入 null
    }

    /**
     * 静态工厂方法：创建带数据的成功响应
     * 用于查询接口，把查询到的数据放入 data 字段
     *
     * 使用方式：return Result.success(user);
     * 返回示例：{"code": 200, "message": "success", "data": {"id": 1, "username": "admin"}}
     *
     * @param data 要返回给前端的数据
     */
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    /**
     * 带自定义信息的成功响应
     * 练习时写的方法
     * @param daga 返回给前端的数据
     * @param message 返回给前端的自定义信息
     */
    public static <T> Result<T> successWithMessage(T daga, String message) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage(message);
        return r;
    }

    /**
     * 静态工厂方法：通过数字错误码和信息创建错误响应
     *
     * 使用方式：return Result.error(400, "参数不能为空");
     * 返回示例：{"code": 400, "message": "参数不能为空", "data": null}
     *
     * @param code    错误码
     * @param message 错误描述
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        // data 字段不设置，默认为 null
        return r;
    }

    /**
     * 静态工厂方法：通过 ErrorCode 枚举创建错误响应
     * 推荐使用这个方法，因为 ErrorCode 是集中管理的，不容易出错
     *
     * 使用方式：return Result.error(ErrorCode.USER_NOT_FOUND);
     * 返回示例：{"code": 2004, "message": "User not found", "data": null}
     *
     * @param errorCode 错误码枚举值
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());  // 从枚举中取出 code 和 message
    }
}
