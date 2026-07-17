package com.safori.domain.notification.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * FCM 디바이스 토큰. 사용자 1:N (멀티 디바이스).
 * 토큰 문자열은 전역 unique — 기기 양도 등으로 같은 토큰이 다른 사용자로 재등록되면 소유자를 갱신한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(of = "id", callSuper = false)
@Table(name = "device_token")
public class DeviceToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    /** FCM registration token. 통상 ~163자이나 상한 미보장이라 512자로 여유 확보 */
    @Column(nullable = false, unique = true, length = 512)
    private String token;

    /** 같은 토큰이 다른 사용자로 재등록될 때(기기 양도/재로그인) 소유자 교체 */
    public void reassignTo(User user) {
        this.user = user;
    }
}
