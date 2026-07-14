package com.example.albam.domain.user.repository;

import com.example.albam.domain.user.entity.EmailToken;
import com.example.albam.domain.user.entity.EmailTokenType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenAndType(String token, EmailTokenType type);
}
