package com.example.albam.domain.user.service;

import com.example.albam.domain.invite.repository.JoinRequestRepository;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.user.dto.UpdateUserRequest;
import com.example.albam.domain.user.dto.UserResponse;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.EmailTokenRepository;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.ConflictException;
import com.example.albam.global.exception.NotFoundException;
import com.example.albam.global.file.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String PROFILE_IMAGE_DIRECTORY = "profile-images";

    private final UserRepository userRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final S3Uploader s3Uploader;

    public UserResponse getMe(Long userId) {
        return UserResponse.from(getUser(userId));
    }

    @Transactional
    public UserResponse updateMe(Long userId, UpdateUserRequest request) {
        if (userRepository.existsByPhoneAndIdNot(request.phone(), userId)) {
            throw new ConflictException("이미 사용 중인 전화번호입니다.");
        }
        User user = getUser(userId);
        user.updateProfile(request.name(), request.phone());
        return UserResponse.from(user);
    }

    /**
     * 탈퇴: 근무 이력(근태·급여)이 StoreMember를 통해 유저를 참조하므로 행을 지우지 않고
     * 개인정보를 익명화한다 (soft delete). 활동 중인 매장이 있으면 먼저 나가야 한다.
     */
    @Transactional
    public void withdraw(Long userId) {
        if (storeMemberRepository.existsByUserIdAndStatus(userId, MemberStatus.ACTIVE)) {
            throw new ConflictException("소속된 매장이 있으면 탈퇴할 수 없습니다. 매장을 먼저 나가거나 삭제해 주세요.");
        }
        User user = getUser(userId);
        s3Uploader.delete(user.getProfileImageUrl());
        emailTokenRepository.deleteByUserId(userId);
        joinRequestRepository.deleteByUserId(userId);
        user.anonymizeForWithdrawal();
    }

    @Transactional
    public UserResponse updateProfileImage(Long userId, MultipartFile image) {
        User user = getUser(userId);
        String previousImageUrl = user.getProfileImageUrl();
        String uploadedUrl = s3Uploader.upload(image, PROFILE_IMAGE_DIRECTORY);
        user.changeProfileImageUrl(uploadedUrl);
        s3Uploader.delete(previousImageUrl);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse deleteProfileImage(Long userId) {
        User user = getUser(userId);
        s3Uploader.delete(user.getProfileImageUrl());
        user.changeProfileImageUrl(null);
        return UserResponse.from(user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
    }
}
