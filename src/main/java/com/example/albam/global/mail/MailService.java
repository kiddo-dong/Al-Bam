package com.example.albam.global.mail;

import com.example.albam.global.exception.BusinessException;
import com.example.albam.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender javaMailSender;

    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "메일 발송에 실패했습니다. SMTP 설정(GMAIL_USERNAME/GMAIL_APP_PASSWORD)을 확인해 주세요.");
        }
    }
}
