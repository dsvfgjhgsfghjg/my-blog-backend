package com.leo.myblog.exception;

/**
 * Authentication failure for refresh-token endpoints.
 *
 * <p>This is separate from generic business failures because the browser client
 * must receive an actual HTTP 401 and clear its session.
 */
public class TokenAuthenticationException extends BusinessException {

    public TokenAuthenticationException(String message) {
        super(401, message);
    }
}
