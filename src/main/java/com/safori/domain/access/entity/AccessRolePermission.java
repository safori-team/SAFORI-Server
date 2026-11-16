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
 * 기관 역할에 부여된 권한. (role_id, permission_id) 쌍이 PK다.
 *
 * <p>Hibernate는 복합 PK 컬럼을 속성 이름순((permission_id, role_id))으로 만든다. 권한 계산은 역할로 찾으므로
 * role_id 인덱스를 따로 둔다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@IdClass(AccessRolePermission.Key.class)
@Table(name = "access_role_permission",
        indexes = @Index(name = "idx_arp_role", columnList = "role_id"))
public class AccessRolePermission extends BaseTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_arp_role"))
    private AccessRole role;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false, foreignKey = @ForeignKey(name = "fk_arp_permission"))
    private AccessPermission permission;

    public static AccessRolePermission of(AccessRole role, AccessPermission permission) {
        return AccessRolePermission.builder()
                .role(role)
                .permission(permission)
                .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long role;
        private Long permission;
    }
}
