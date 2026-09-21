package com.safori.domain.notification.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.notification.entity.DeviceToken;
import com.safori.domain.notification.repository.DeviceTokenRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Adaptor
@RequiredArgsConstructor
public class DeviceTokenAdaptorImpl implements DeviceTokenAdaptor {

    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    public List<DeviceToken> queryTokensByUserId(Long userId) {
        return deviceTokenRepository.findAllByUser_Id(userId);
    }
}
