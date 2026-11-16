package com.safori.domain.care.entity;

import com.safori.domain.organization.entity.Organization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static com.safori.domain.care.entity.CareProcessingStatus.ABSORBED;
import static com.safori.domain.care.entity.CareProcessingStatus.DONE;
import static com.safori.domain.care.entity.CareProcessingStatus.IN_PROGRESS;
import static com.safori.domain.care.entity.CareProcessingStatus.UNCHECKED;
import static com.safori.domain.care.entity.CareStatusCode.CAUTION;
import static com.safori.domain.care.entity.CareStatusCode.INTEREST;
import static com.safori.domain.care.entity.CareStatusCode.URGENT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기록 덮어쓰기(같거나 높으면 교체, 낮으면 흡수)와 완료 시 상태 코드 X.
 */
class CareRecipientRecordTest {

    private final AtomicLong ids = new AtomicLong();
    private final CareRecipient recipient = CareRecipient.register(Organization.create("기관"), 1L);

    @Test
    @DisplayName("같거나 높은 등급은 현재 기록을 교체하고 기존은 흡수, 낮은 등급은 흡수된다")
    void higherOrEqualOverwritesLowerIsAbsorbed() {
        CareRecord interest = receive(INTEREST);
        CareRecord urgent = receive(URGENT);
        CareRecord caution = receive(CAUTION);

        assertThat(recipient.getCurrentRecord()).isSameAs(urgent);
        assertThat(interest.getProcessingStatus()).isEqualTo(ABSORBED);
        assertThat(caution.getProcessingStatus()).isEqualTo(ABSORBED);
        assertThat(urgent.getProcessingStatus()).isEqualTo(UNCHECKED);

        CareRecord urgentAgain = receive(URGENT);
        assertThat(recipient.getCurrentRecord()).isSameAs(urgentAgain);
        assertThat(urgent.getProcessingStatus()).isEqualTo(ABSORBED);
    }

    @Test
    @DisplayName("현재 기록만 처리할 수 있고, 완료하면 현재 기록이 비어 상태 코드 X가 된다")
    void processingOnlyCurrentAndDoneClearsStatus() {
        CareRecord caution = receive(CAUTION);
        CareRecord absorbed = receive(INTEREST);

        assertThat(recipient.process(absorbed, IN_PROGRESS, null, LocalDateTime.now())).isFalse();
        assertThat(recipient.process(caution, ABSORBED, null, LocalDateTime.now())).isFalse();
        assertThat(recipient.process(caution, IN_PROGRESS, null, LocalDateTime.now())).isTrue();
        assertThat(recipient.getCurrentRecord()).isSameAs(caution);

        assertThat(recipient.process(caution, DONE, null, LocalDateTime.now())).isTrue();
        assertThat(recipient.getCurrentRecord()).isNull();
        assertThat(caution.getProcessingStatus()).isEqualTo(DONE);

        CareRecord next = receive(INTEREST);
        assertThat(recipient.getCurrentRecord()).isSameAs(next);
    }

    private CareRecord receive(CareStatusCode code) {
        CareRecord record = CareRecord.builder()
                .id(ids.incrementAndGet())
                .publicId("r" + ids.get())
                .recipient(recipient)
                .statusCode(code)
                .reasonMessage("사유")
                .detectedAt(LocalDateTime.now())
                .processingStatus(UNCHECKED)
                .build();
        recipient.receive(record);
        return record;
    }
}
