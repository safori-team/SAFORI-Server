package com.safori.api.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "수정 후 내 계정 정보. 나머지(역할·권한·기관)는 GET /v1/api/users")
public record MyAccountResponse(
        @Schema(description = "아이디", example = "orgadmin01") String loginId,
        @Schema(description = "이름", example = "이관리") String name,
        @Schema(description = "연락처 (숫자만)", example = "01012345678") String phone
) {
}
