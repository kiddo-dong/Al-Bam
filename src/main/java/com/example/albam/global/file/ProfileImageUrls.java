package com.example.albam.global.file;

import com.example.albam.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사용자 프로필 사진의 공개 URL을 만든다.
 *
 * <p>엔티티에는 S3 key만 있고 버킷·리전은 설정값이라, 응답을 만드는 시점에 조립해야 한다. 사람이
 * 등장하는 응답이 여러 곳이라 그 조립을 한 군데로 모아, 나중에 CDN을 앞에 두더라도 여기만 고치면
 * 되게 한다.
 */
@Component
@RequiredArgsConstructor
public class ProfileImageUrls {

    private final S3Uploader s3Uploader;

    /** 사진을 등록하지 않은 사용자면 null. */
    public String of(User user) {
        return s3Uploader.toPublicUrl(user.getProfileImageKey());
    }
}
