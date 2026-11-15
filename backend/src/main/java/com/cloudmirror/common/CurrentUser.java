package com.cloudmirror.common;

/**
 * 当前用户钩子。
 *
 * <p>本轮不做登录：恒返回 {@link #LOCAL_USER}（0 = 本地默认用户）。
 * 将来接 JWT 时只改这一个方法，Service 层所有调用处不用动。
 */
public final class CurrentUser {

    /** 本地默认用户 ID */
    public static final long LOCAL_USER = 0L;

    private CurrentUser() {
    }

    public static long id() {
        return LOCAL_USER;
    }
}
