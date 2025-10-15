package com.yuguanzhang.lumi.user.controller;



import com.yuguanzhang.lumi.common.dto.BaseResponseDto;
import com.yuguanzhang.lumi.user.dto.update.UpdateRequestDto;
import com.yuguanzhang.lumi.user.dto.update.UpdateResponesDto;
import com.yuguanzhang.lumi.user.service.update.UpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UpdateController {

    private final UpdateService updateService;

    @PatchMapping("/api/user/update")
    public BaseResponseDto<UpdateResponesDto> update(@RequestBody UpdateRequestDto request) {
        UpdateResponesDto response = updateService.update(request);
        return BaseResponseDto.of(HttpStatus.OK, response);
    }

}
