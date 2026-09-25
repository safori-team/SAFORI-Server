package com.safori.domain.organization.repository;

import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    boolean existsByOrganizationAndAccount(Organization organization, BackofficeAccount account);

    Optional<OrganizationMember> findByOrganizationAndAccount(Organization organization, BackofficeAccount account);

    /**
     * 토큰이 가리키는 기관 멤버. 계정·기관을 함께 읽어 활성 상태 판정에서 추가 쿼리가 나가지 않게 한다.
     */
    @Query("""
            SELECT m
            FROM OrganizationMember m
            JOIN FETCH m.account a
            JOIN FETCH m.organization o
            WHERE a.accountUuid = :accountUuid
              AND o.publicId = :organizationPublicId
            """)
    Optional<OrganizationMember> findForAuthentication(@Param("accountUuid") String accountUuid,
                                                       @Param("organizationPublicId") String organizationPublicId);

    /** 권한 판정용 조회. 계정·기관을 함께 읽는다. */
    @Query("""
            SELECT m
            FROM OrganizationMember m
            JOIN FETCH m.account
            JOIN FETCH m.organization
            WHERE m.id = :memberId
            """)
    Optional<OrganizationMember> findForAuthorization(@Param("memberId") Long memberId);
}
