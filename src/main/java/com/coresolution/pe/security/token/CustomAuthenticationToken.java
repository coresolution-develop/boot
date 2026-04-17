package com.coresolution.pe.security.token;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

public class CustomAuthenticationToken extends UsernamePasswordAuthenticationToken {
    private final String loginType;

    public CustomAuthenticationToken(Object principal, Object credentials, String loginType) {
        super(principal, credentials);
        this.loginType = loginType;
    }

    public String getLoginType() {
        return loginType;
    }
}