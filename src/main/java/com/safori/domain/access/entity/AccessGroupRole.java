package com.safori.domain.access.entity;

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
 * 그룹에 연결된 역할. 그룹 구성원 전원이 이 역할을 상속한다. PK는 (group_id, role_id).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@IdClass(AccessGroupRole.Key.class)
@Table(name = "access_group_role",
        indexes = @Index(name = "idx_agr_role", columnList = "role_id"))
public class AccessGroupRole extends BaseTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_agr_group"))
    private AccessGroup group;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_agr_role"))
    private AccessRole role;

    public static AccessGroupRole of(AccessGroup group, AccessRole role) {
        return AccessGroupRole.builder()
                .group(group)
                .role(role)
                .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long group;
        private Long role;
    }
}
