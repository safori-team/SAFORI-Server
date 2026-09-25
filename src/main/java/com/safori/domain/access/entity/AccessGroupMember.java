package com.safori.domain.access.entity;

import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * 그룹 소속. PK는 (group_id, organization_member_id). 그룹과 구성원은 같은 기관이어야 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@IdClass(AccessGroupMember.Key.class)
@Table(name = "access_group_member",
        indexes = @Index(name = "idx_agm_member", columnList = "organization_member_id"))
public class AccessGroupMember extends BaseTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_agm_group"))
    private AccessGroup group;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_member_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_agm_member"))
    private OrganizationMember member;

    public static AccessGroupMember of(AccessGroup group, OrganizationMember member) {
        return AccessGroupMember.builder()
                .group(group)
                .member(member)
                .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long group;
        private Long member;
    }
}
