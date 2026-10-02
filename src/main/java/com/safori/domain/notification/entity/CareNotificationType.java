package com.safori.domain.notification.entity;

/** 복지관(관리자·담당자·보호자)에게 보내는 알림 종류. */
public enum CareNotificationType {
    /** 대상자 상태 코드가 즉시 확인으로 올라감 — 관리자·담당자·보호자에게 바로. */
    URGENT_ENTERED,
    /** 주의 기록이 48시간 동안 미확인 — 담당자·보호자에게. */
    CAUTION_UNCHECKED_48H
}
