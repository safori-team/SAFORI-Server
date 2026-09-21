package com.safori.domain.notification.adaptor;

import com.safori.domain.notification.entity.DeviceToken;
import java.util.List;

public interface DeviceTokenAdaptor {

    List<DeviceToken> queryTokensByUserId(Long userId);
}
