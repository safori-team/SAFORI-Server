package com.safori.api.user.dto;

import com.safori.domain.user.entity.Gender;
import com.safori.security.dto.AccountRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Schema(description = "내 정보 응답")
@Builder
@Getter
@RequiredArgsConstructor
public class UserInfoResponse {
    @Schema(description = "역할 (ELDER / ORG_ADMIN / CARE_WORKER / GUARDIAN)", example = "ELDER")
    private final AccountRole role;
    @Schema(description = "현재 권한 코드. 어르신은 빈 목록. 메뉴 노출 여부는 이 값으로 판단한다.", example = "[\"RECIPIENT_READ\"]")
    private final List<String> permissions;
    @Schema(description = "소속 기관. 어르신은 null")
    private final Organization organization;
    @Schema(description = "로그인 아이디", example = "user01")
    private final String username;
    @Schema(description = "사용자 이름 (실명)", example = "홍길동")
    private final String name;
    @Schema(description = "성별 (MALE / FEMALE)", example = "MALE")
    private final Gender gender;
    @Schema(description = "별명 (없으면 null)", example = "길동이")
    private final String nickname;

    public record Organization(
            @Schema(description = "기관 외부 식별자") String publicId,
            @Schema(description = "기관 이름", example = "사포리 복지관") String name) {
    }
}
