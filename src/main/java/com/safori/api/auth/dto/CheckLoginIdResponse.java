package com.safori.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이디 사용 가능 여부")
public record CheckLoginIdResponse(
        @Schema(description = "사용 가능하면 true. 어르신·관리자·담당자·보호자 계정 중 하나라도 쓰고 있으면 false", example = "true")
        boolean available
) {
}
