package com.safori.api.user.dto;

import com.safori.domain.user.entity.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "내 정보 응답")
@Builder
@Getter
@RequiredArgsConstructor
public class UserInfoResponse {
    @Schema(description = "로그인 아이디", example = "user01")
    private final String username;
    @Schema(description = "사용자 이름 (실명)", example = "홍길동")
    private final String name;
    @Schema(description = "성별 (MALE / FEMALE)", example = "MALE")
    private final Gender gender;
    @Schema(description = "별명 (없으면 null)", example = "길동이")
    private final String nickname;
}
