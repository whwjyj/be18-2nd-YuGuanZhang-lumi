package com.yuguanzhang.lumi.email.service;

import com.yuguanzhang.lumi.common.exception.GlobalException;
import com.yuguanzhang.lumi.common.exception.message.ExceptionMessage;
import com.yuguanzhang.lumi.email.entity.EmailVerification;
import com.yuguanzhang.lumi.email.enums.VerificationStatus;
import com.yuguanzhang.lumi.email.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;   // ✅ 비동기 실행용
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final JavaMailSender mailSender;
    private final EmailVerificationRepository emailVerificationRepository;

    @Override
    @Transactional
    @Async("mailExecutor")   // ✅ AsyncConfig에서 만든 mailExecutor 쓰레드 풀 사용
    public void sendVerificationEmail(String email) {
        Optional<EmailVerification> existingVerification =
                emailVerificationRepository.findByEmail(email);

        String token = UUID.randomUUID()
                           .toString();
        LocalDateTime expirationTime = LocalDateTime.now()
                                                    .plusMinutes(10);
        LocalDateTime now = LocalDateTime.now();

        EmailVerification verification;
        if (existingVerification.isPresent()) {
            log.info("기존 인증 기록 업데이트. 이메일: {}", email);
            verification = existingVerification.get();
            verification.updateForResend(token, expirationTime);
            verification.setDateTimeAt(now);
        } else {
            log.info("새로운 인증 기록 생성. 이메일: {}", email);
            verification = EmailVerification.builder()
                                            .email(email)
                                            .verificationCode(token)
                                            .status(VerificationStatus.UNREAD)
                                            .dateTimeAt(now)
                                            .expirationAt(expirationTime)
                                            .build();
        }

        try {
            emailVerificationRepository.save(verification);

            String redisKey = "email:verify:" + token;
            redisTemplate.opsForValue()
                         .set(redisKey, email, 10, TimeUnit.MINUTES);

            String link = "http://localhost:8080/api/email/verify?token=" + token;

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(email);
            helper.setSubject("[lumi] 이메일 인증");
            helper.setText(
                    "<html><body>" + "<p>이메일 인증을 위해 아래 링크를 클릭해주세요:</p>" + "<a href=\"" + link + "\">" + link + "</a>" + "</body></html>",
                    true);

            // ✅ 비동기 실행이므로 실제 발송에 시간이 걸려도 API 응답엔 영향 없음
            long startTime = System.currentTimeMillis();
            mailSender.send(message);
            long endTime = System.currentTimeMillis();
            log.info("이메일 발송 성공. 이메일: {} | 소요 시간: {} ms", email, (endTime - startTime));
        } catch (Exception e) {
            log.error("이메일 전송 오류. 이메일: {}, 원인: {}", email, e.getMessage(), e);
            verification.markAsError();
        }
    }

    @Override
    @Transactional
    public String verifyEmail(String token) {
        String redisKey = "email:verify:" + token;
        String email = redisTemplate.opsForValue()
                                    .get(redisKey);

        if (email == null) {
            log.warn("유효하지 않거나 만료된 토큰: {}", token);
            throw new GlobalException(ExceptionMessage.EMAIL_VERIFICATION_FAILED);
        }

        Optional<EmailVerification> optionalVerification =
                emailVerificationRepository.findByEmail(email);
        if (optionalVerification.isEmpty()) {
            log.error("인증 기록 없음. 이메일: {}", email);
            throw new GlobalException(ExceptionMessage.EMAIL_VERIFICATION_FAILED);
        }

        EmailVerification verification = optionalVerification.get();
        if (LocalDateTime.now()
                         .isAfter(verification.getExpirationAt())) {
            log.warn("토큰 만료. 이메일: {}", email);
            verification.markAsExpired();
            throw new GlobalException(ExceptionMessage.EMAIL_VERIFICATION_FAILED);
        }
        if (!verification.getVerificationCode()
                         .equals(token)) {
            log.warn("인증 코드 불일치. 토큰: {}", token);
            throw new GlobalException(ExceptionMessage.EMAIL_VERIFICATION_FAILED);
        }

        log.info("이메일 인증 성공. 이메일: {}", email);
        verification.markAsVerified();

        redisTemplate.delete(redisKey);

        return "이메일 인증이 완료되었습니다.";
    }

    @Override
    public boolean isEmailVerified(String email) {
        return emailVerificationRepository.findByEmail(email)
                                          .map(EmailVerification::getStatus)
                                          .filter(status -> status == VerificationStatus.VERIFIED)
                                          .isPresent();
    }
}
