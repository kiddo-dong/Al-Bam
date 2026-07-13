package com.example.albam.domain.user.service;

import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.user.dto.UpdateUserRequest;
import com.example.albam.domain.user.dto.UserResponse;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.ConflictException;
import com.example.albam.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final StoreMemberRepository storeMemberRepository;

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

    @Transactional
    public void withdraw(Long userId) {
        if (storeMemberRepository.existsByUserId(userId)) {
            throw new ConflictException("소속된 매장이 있으면 탈퇴할 수 없습니다. 매장을 먼저 나가거나 삭제해 주세요.");
        }
        userRepository.deleteById(userId);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
    }
}
