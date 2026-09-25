package com.safori.domain.care.repository;

import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.care.entity.CareAssignment;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.GuardianRecipientLink;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할 범위가 걸린 어르신 목록 쿼리. 전체를 읽고 거르지 않고 범위를 조건으로 거는지 본다.
 *
 * <pre>
 * 기관 A  assigned        ── 담당자 현재 배정
 *         linked          ── 보호자 현재 연결
 *         formerlyRelated ── 배정·연결 모두 종료
 *         unrelated
 *         (비활성 어르신 — 담당자 배정 중)
 * 기관 B  어르신
 * </pre>
 */
@DataJpaTest
class CareRecipientRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 12, 0);

    @Autowired CareRecipientRepository repository;
    @Autowired EntityManager em;

    private Organization organization;
    private OrganizationMember worker;
    private OrganizationMember guardian;
    private CareRecipient assigned;
    private CareRecipient linked;
    private CareRecipient formerlyRelated;
    private CareRecipient unrelated;

    @BeforeEach
    void setUp() {
        organization = persist(Organization.create("기관"));
        worker = persistMember(organization, "worker");
        guardian = persistMember(organization, "guardian");

        assigned = persist(CareRecipient.register(organization, null));
        persist(CareAssignment.start(assigned, worker, null, null, NOW));

        linked = persist(CareRecipient.register(organization, null));
        persist(GuardianRecipientLink.start(linked, guardian, null, NOW));

        formerlyRelated = persist(CareRecipient.register(organization, null));
        persist(CareAssignment.start(formerlyRelated, worker, null, null, NOW.minusDays(10))).end(null, NOW.minusDays(1));
        persist(GuardianRecipientLink.start(formerlyRelated, guardian, null, NOW.minusDays(10))).end(null, NOW.minusDays(1));

        unrelated = persist(CareRecipient.register(organization, null));

        CareRecipient inactive = persist(CareRecipient.register(organization, null));
        persist(CareAssignment.start(inactive, worker, null, null, NOW));
        inactive.deactivate();

        persist(CareRecipient.register(persist(Organization.create("다른 기관")), null));
    }

    @Test
    @DisplayName("기관 전체 범위는 소속 기관의 활성 어르신을 모두 읽는다 — 비활성·다른 기관 제외")
    void organizationWideReadsAllActiveRecipients() {
        assertThat(accessibleIds(worker, true, false, false))
                .containsExactly(assigned.getId(), linked.getId(), formerlyRelated.getId(), unrelated.getId());
    }

    @Test
    @DisplayName("배정 범위는 현재 배정된 어르신만 읽는다 — 끝난 배정·비활성 어르신 제외")
    void assignedScopeReadsCurrentAssignmentsOnly() {
        assertThat(accessibleIds(worker, false, true, false)).containsExactly(assigned.getId());
    }

    @Test
    @DisplayName("연결 범위는 현재 연결된 어르신만 읽는다 — 끝난 연결 제외")
    void linkedScopeReadsCurrentLinksOnly() {
        assertThat(accessibleIds(guardian, false, false, true)).containsExactly(linked.getId());
    }

    @Test
    @DisplayName("배정과 연결이 같은 어르신에 겹쳐도 한 번만 세고, 페이지 count가 맞다")
    void overlappingRelationsAreCountedOnce() {
        persist(GuardianRecipientLink.start(assigned, worker, null, NOW));
        persist(GuardianRecipientLink.start(linked, worker, null, NOW));

        Page<CareRecipient> firstPage = repository.findAccessible(
                organization.getId(), worker.getId(), false, true, true, PageRequest.of(0, 1, Sort.by("id")));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).extracting(CareRecipient::getId).containsExactly(assigned.getId());
    }

    @Test
    @DisplayName("어떤 범위도 없으면 아무것도 읽지 않는다")
    void noScopeReadsNothing() {
        assertThat(accessibleIds(worker, false, false, false)).isEmpty();
    }

    private List<Long> accessibleIds(OrganizationMember member, boolean organizationWide,
                                     boolean includesAssigned, boolean includesLinked) {
        return repository.findAccessible(organization.getId(), member.getId(),
                        organizationWide, includesAssigned, includesLinked, PageRequest.of(0, 20, Sort.by("id")))
                .map(CareRecipient::getId)
                .getContent();
    }

    private OrganizationMember persistMember(Organization organization, String loginId) {
        BackofficeAccount account = persist(BackofficeAccount.create(loginId, "hash", loginId, null));
        return persist(OrganizationMember.invite(organization, account, null));
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
