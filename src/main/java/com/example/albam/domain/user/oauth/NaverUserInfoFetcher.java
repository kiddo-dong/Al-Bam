package com.example.albam.domain.user.oauth;

import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.global.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class NaverUserInfoFetcher implements OAuthUserInfoFetcher {

    private static final String USERINFO_URI = "https://openapi.naver.com/v1/nid/me";

    private final OAuthApiClient oAuthApiClient;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.NAVER;
    }

    @Override
    public OAuthUserInfo fetch(String accessToken) {
        JsonNode body = oAuthApiClient.getUserInfo(USERINFO_URI, accessToken);
        if (!"00".equals(body.path("resultcode").asText())) {
            throw new InvalidRequestException("유효하지 않은 액세스 토큰입니다.");
        }
        JsonNode response = body.path("response");
        return new OAuthUserInfo(response.path("id").asText(), response.path("email").asText(),
                response.path("name").asText());
    }
}
