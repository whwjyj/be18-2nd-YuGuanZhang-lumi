package com.yuguanzhang.lumi.user.service.update;

import com.yuguanzhang.lumi.user.dto.update.UpdateRequestDto;
import com.yuguanzhang.lumi.user.dto.update.UpdateResponesDto;

public interface UpdateService {
    UpdateResponesDto update(UpdateRequestDto updateRequestDto);
}
