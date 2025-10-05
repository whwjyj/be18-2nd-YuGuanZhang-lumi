package com.yuguanzhang.lumi.user.service.update;

import com.yuguanzhang.lumi.common.exception.GlobalException;
import com.yuguanzhang.lumi.common.exception.message.ExceptionMessage;
import com.yuguanzhang.lumi.user.dto.UserDetailsDto;
import com.yuguanzhang.lumi.user.dto.update.UpdateRequestDto;
import com.yuguanzhang.lumi.user.dto.update.UpdateResponesDto;
import com.yuguanzhang.lumi.user.entity.User;
import com.yuguanzhang.lumi.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateServiceImpl implements UpdateService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UpdateResponesDto update(UpdateRequestDto requestDto) {

        // SecurityContext에서 인증 정보 가져오기
        UserDetailsDto userDetails = (UserDetailsDto) SecurityContextHolder.getContext()
                                                                           .getAuthentication()
                                                                           .getPrincipal();
        UUID userId = userDetails.getUser()
                                 .getUserId();

        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new GlobalException(
                                          ExceptionMessage.USER_NOT_FOUND));

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new GlobalException(ExceptionMessage.NOT_PASSWORD);
        }

        // 새 비밀번호 같은면 오류
        if (requestDto.getPassword()
                      .equals(requestDto.getNewPassword())) {
            throw new GlobalException(ExceptionMessage.SAME_PASSWORD);
        }

        // 비밀번호 업데이트
        user.updatePassword(passwordEncoder.encode(requestDto.getNewPassword()));
        userRepository.save(user);

        return new UpdateResponesDto("비밀번호 변경 성공");
    }
}
