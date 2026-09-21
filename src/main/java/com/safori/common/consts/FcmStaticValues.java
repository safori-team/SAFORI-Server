package com.safori.common.consts;

import lombok.experimental.UtilityClass;

/**
 * FCM 전송 관련 고정 상수.
 */
@UtilityClass
public final class FcmStaticValues {

    /** sendEachForMulticast 1회 호출당 토큰 수 상한 */
    public static final int MULTICAST_BATCH_SIZE = 500;

}
