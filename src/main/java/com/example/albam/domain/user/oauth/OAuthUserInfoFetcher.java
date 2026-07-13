package com.example.albam.domain.user.oauth;

import com.example.albam.domain.user.entity.AuthProvider;

/** provider가 발급한 access token으로 provider의 userinfo API를 호출해 사용자 정보를 조회한다. */
public interface OAuthUserInfoFetcher {

    AuthProvider getProvider();

    OAuthUserInfo fetch(String accessToken);
}
