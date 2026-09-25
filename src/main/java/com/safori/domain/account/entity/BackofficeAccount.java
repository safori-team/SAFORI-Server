package com.safori.domain.account.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * 기관 관리자·담당자·보호자의 백오피스 로그인 계정.
 *
 * <p>어르신 앱 계정({@code users})과 상속·FK 관계가 없는 별도 정체성이다. 계정은 로그인만 책임지고,
 * 어느 기관에서 어떤 역할로 활동하는지는 {@code organization_member}가 기관별로 관리한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "backoffice_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_boa_account_uuid", columnNames = "account_uuid"),
                @UniqueConstraint(name = "uq_boa_login_id", columnNames = "login_id")
        })
public class BackofficeAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    /** 외부 노출·토큰 subject용 식별자. */
    @Column(name = "account_uuid", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String accountUuid;

    @Column(name = "login_id", nullable = false, length = 64)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 휴대폰 번호(숫자만). 계정 정보 공유(문자)·연락에 쓴다. */
    @Column(name = "phone", length = 11)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(16)")
    private BackofficeAccountStatus status;

    /**
     * 토큰 무효화 버전. 토큰에 실린 값과 다르면 서명이 유효해도 인증하지 않는다.
     * 정지·강제 로그아웃처럼 발급된 토큰을 만료 전에 끊어야 할 때 올린다.
     */
    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    public static BackofficeAccount create(String loginId, String passwordHash, String name, String phone) {
        return BackofficeAccount.builder()
                .accountUuid(UUID.randomUUID().toString())
                .loginId(loginId)
                .passwordHash(passwordHash)
                .name(name)
                .phone(phone)
                .status(BackofficeAccountStatus.ACTIVE)
                .authVersion(0L)
                .build();
    }

    public boolean isActive() {
        return this.status == BackofficeAccountStatus.ACTIVE;
    }

    /** 정지. 이미 발급된 토큰도 함께 무효화한다. */
    public void suspend() {
        this.status = BackofficeAccountStatus.SUSPENDED;
        revokeIssuedTokens();
    }

    public void activate() {
        this.status = BackofficeAccountStatus.ACTIVE;
    }

    /** 이미 발급된 모든 토큰을 다음 요청부터 무효화한다. */
    public void revokeIssuedTokens() {
        this.authVersion++;
    }
}
