package com.example.albam.domain.user.service;

import com.example.albam.domain.invite.repository.JoinRequestRepository;
import com.example.albam.domain.laborqa.repository.LaborQaMessageRepository;
import com.example.albam.domain.laborqa.repository.LaborQaSessionRepository;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.user.dto.CompleteProfileRequest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final LaborQaSessionRepository laborQaSessionRepository;
    private final LaborQaMessageRepository laborQaMessageRepository;
    private final S3Uploader s3Uploader;
    private final PlatformTransactionManager transactionManager;

    public UserResponse getMe(Long userId) {
        return UserResponse.from(getUser(userId));
    }

    /**
     * 소셜 가입 후 추가 정보 입력을 완료한다. 입력 도중 창이 닫혔더라도 재로그인 후
     * profileCompleted=false를 보고 이 API로 이어서 완료할 수 있다.
     */
    @Transactional
    public UserResponse completeProfile(Long userId, CompleteProfileRequest request) {
        User user = getUser(userId);
        if (user.isProfileCompleted()) {
            throw new ConflictException("이미 프로필 입력이 완료된 계정입니다. 수정은 프로필 수정 API를 이용해 주세요.");
        }
        if (userRepository.existsByPhoneAndIdNot(request.phone(), userId)) {
            throw new ConflictException("이미 사용 중인 전화번호입니다.");
        }
        user.completeProfile(request.name(), request.phone(), request.birthDate());
        return UserResponse.from(user);
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
    /** S3 삭제는 트랜잭션 밖에서 한다 — 커밋 후에 지워도 되고, 커밋까지 커넥션을 붙잡을 이유가 없다. */
    public void withdraw(Long userId) {
        if (storeMemberRepository.existsByUserIdAndStatus(userId, MemberStatus.ACTIVE)) {
            throw new ConflictException("소속된 매장이 있으면 탈퇴할 수 없습니다. 매장을 먼저 나가거나 삭제해 주세요.");
        }
        String[] profileImageUrlHolder = new String[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = getUser(userId);
            profileImageUrlHolder[0] = user.getProfileImageUrl();
            emailTokenRepository.deleteByUserId(userId);
            joinRequestRepository.deleteByUserId(userId);
            // 개인 질문 이력은 민감할 수 있어 탈퇴 시 함께 삭제한다 (메시지 -> 세션 순서, FK 제약)
            laborQaMessageRepository.deleteBySessionUserId(userId);
            laborQaSessionRepository.deleteAll(laborQaSessionRepository.findAllByUserId(userId));
            user.anonymizeForWithdrawal();
        });
        if (profileImageUrlHolder[0] != null) {
            s3Uploader.delete(profileImageUrlHolder[0]);
        }
    }

    /** S3 업로드/삭제는 네트워크 호출이라 DB 트랜잭션 밖에서 수행한다. */
    public UserResponse updateProfileImage(Long userId, MultipartFile image) {
        String uploadedUrl = s3Uploader.upload(image, PROFILE_IMAGE_DIRECTORY);
        String[] previousImageUrlHolder = new String[1];
        UserResponse response = new TransactionTemplate(transactionManager).execute(status -> {
            User user = getUser(userId);
            previousImageUrlHolder[0] = user.getProfileImageUrl();
            user.changeProfileImageUrl(uploadedUrl);
            return UserResponse.from(user);
        });
        if (previousImageUrlHolder[0] != null) {
            s3Uploader.delete(previousImageUrlHolder[0]);
        }
        return response;
    }

    public UserResponse deleteProfileImage(Long userId) {
        String[] previousImageUrlHolder = new String[1];
        UserResponse response = new TransactionTemplate(transactionManager).execute(status -> {
            User user = getUser(userId);
            previousImageUrlHolder[0] = user.getProfileImageUrl();
            user.changeProfileImageUrl(null);
            return UserResponse.from(user);
        });
        if (previousImageUrlHolder[0] != null) {
            s3Uploader.delete(previousImageUrlHolder[0]);
        }
        return response;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
    }
}
