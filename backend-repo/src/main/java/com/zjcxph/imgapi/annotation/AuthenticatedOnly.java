package com.zjcxph.imgapi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记只要求具备有效登录会话、不要求额外业务权限的 API。
 *
 * <p>公开接口应进入 {@code ApiAccessPolicy} 的公开路径清单；需要业务权限的接口
 * 应使用 {@link RequirePermissions}。三类策略必须明确且互斥。</p>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthenticatedOnly {
}
