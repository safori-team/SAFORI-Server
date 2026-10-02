package com.safori.api.notification.service;

import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.port.PushSendResult;
import com.safori.common.annotation.UseCase;
import com.safori.common.consts.NotificationStaticValues;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecord;
import com.safori.domain.care.repository.CareAssignmentRepository;
import com.safori.domain.care.repository.CareRecordRepository;
import com.safori.domain.care.repository.GuardianRecipientLinkRepository;
import com.safori.domain.notification.entity.CareNotification;
import com.safori.domain.notification.entity.CareNotificationType;
import com.safori.domain.notification.repository.CareNotificationRepository;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.domain.user.adaptor.UserAdaptor;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복지관 알림(역할별 발송 조건 표)을 보내고 이력을 남긴다.
 *
 * <ul>
 *   <li>즉시 확인 진입: 기관 관리자 · 현재 담당자 · 연결 보호자</li>
 *   <li>주의 48시간 미확인: 현재 담당자 · 연결 보호자</li>
 * </ul>
 * 이용 중인 계정에만 보낸다. 받는 계정마다 이력 행을 먼저 만들고(유니크 제약) 전송한 뒤 결과를 적는다.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class SendCarePushUseCase {

    static final Duration CAUTION_UNCHECKED_AFTER = Duration.ofHours(48);

    private final CareRecordRepository recordRepository;
    private final CareAssignmentRepository assignmentRepository;
    private final GuardianRecipientLinkRepository linkRepository;
    private final OrganizationMemberRepository memberRepository;
    private final CareNotificationRepository notificationRepository;
    private final UserAdaptor userAdaptor;
    private final SendPushNotificationUseCase sendPushNotificationUseCase;

    /** 48시간 미확인 알림을 보낼 기록들. */
    @Transactional(readOnly = true)
    public List<Long> cautionUncheckedRecordIds(LocalDateTime now) {
        return notificationRepository.findCautionUncheckedRecordIds(now.minus(CAUTION_UNCHECKED_AFTER));
    }

    @Transactional
    public void execute(Long recordId, CareNotificationType type) {
        CareRecord record = recordRepository.findById(recordId).orElse(null);
        if (record == null || !record.getRecipient().isActive()
                || !record.getRecipient().getOrganization().isActive()) {
            return;
        }
        CareRecipient recipient = record.getRecipient();
        String name = recipient.getUserId() == null ? "대상자" : userAdaptor.queryUserById(recipient.getUserId()).getName();

        // 계정 → 보호자 여부. 한 계정이 여러 역할이어도 한 번만 보낸다.
        Map<BackofficeAccount, Boolean> audience = new LinkedHashMap<>();
        if (type == CareNotificationType.URGENT_ENTERED) {
            memberRepository.findCurrentByTemplate(List.of(recipient.getOrganization().getId()),
                    RoleTemplateCode.ORG_ADMIN.name()).forEach(admin -> add(audience, admin, false));
        }
        assignmentRepository.findCurrentByRecipient(recipient)
                .forEach(assignment -> add(audience, assignment.getWorker(), false));
        linkRepository.findCurrentByRecipient(recipient)
                .forEach(link -> add(audience, link.getGuardian(), true));

        Map<String, String> data = Map.of(
                NotificationStaticValues.DATA_TYPE, type == CareNotificationType.URGENT_ENTERED
                        ? NotificationStaticValues.TYPE_CARE_URGENT
                        : NotificationStaticValues.TYPE_CARE_CAUTION_UNCHECKED,
                NotificationStaticValues.KEY_CARE_RECIPIENT_ID, recipient.getPublicId(),
                NotificationStaticValues.KEY_RECORD_ID, record.getPublicId());

        List<CareNotification> notifications = new ArrayList<>();
        audience.forEach((account, guardian) -> {
            if (!notificationRepository.existsByRecordAndTypeAndAccount(record, type, account)) {
                notifications.add(notificationRepository.save(CareNotification.of(record, type, account,
                        title(type, name), body(type, guardian))));
            }
        });
        // 다른 서버가 같은 알림을 먼저 잡았으면 여기서 유니크 제약으로 실패해 아무것도 보내지 않는다.
        notificationRepository.flush();

        for (CareNotification notification : notifications) {
            try {
                PushSendResult result = sendPushNotificationUseCase.executeForAccount(
                        notification.getAccount().getId(),
                        new PushMessage(notification.getTitle(), notification.getBody(), data));
                notification.sent(result.successCount(), result.failureCount());
            } catch (Exception e) {
                // 한 명 실패해도 나머지는 보낸다. 이력은 0건 성공으로 남는다.
                log.error("복지관 알림 전송 실패 — type={}, recordId={}, accountId={}",
                        type, recordId, notification.getAccount().getId(), e);
            }
        }
    }

    private static void add(Map<BackofficeAccount, Boolean> audience, OrganizationMember member, boolean guardian) {
        if (member.isActiveActor()) {
            audience.putIfAbsent(member.getAccount(), guardian);
        }
    }

    private static String title(CareNotificationType type, String name) {
        return (type == CareNotificationType.URGENT_ENTERED
                ? NotificationStaticValues.CARE_URGENT_TITLE
                : NotificationStaticValues.CARE_CAUTION_UNCHECKED_TITLE).formatted(name);
    }

    private static String body(CareNotificationType type, boolean guardian) {
        if (type == CareNotificationType.URGENT_ENTERED) {
            return NotificationStaticValues.CARE_URGENT_BODY;
        }
        return guardian ? NotificationStaticValues.CARE_CAUTION_UNCHECKED_GUARDIAN_BODY
                : NotificationStaticValues.CARE_CAUTION_UNCHECKED_WORKER_BODY;
    }
}
