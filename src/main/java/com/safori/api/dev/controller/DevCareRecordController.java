package com.safori.api.dev.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.recipient.dto.CareRecordResponse;
import com.safori.api.recipient.service.CareRecordUseCase;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.care.entity.CareStatusCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * [개발/테스트 전용] 상태 코드 판정 규칙이 붙기 전, 대상자 기록을 직접 만들어 현황 화면을 확인한다.
 * {@code @Profile("!prod")} — 운영에서는 빈이 만들어지지 않는다.
 */
@Tag(name = "dev-care-record",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[개발용 - 대상자 기록]")),
     description = "상태 코드 판정 규칙이 붙기 전 기록을 수동으로 만드는 개발 도구. 운영(prod)에서는 비활성화된다.")
@RestController
@Profile("!prod")
@RequiredArgsConstructor
@RequestMapping("/v1/api/admin/dev/care-recipients")
public class DevCareRecordController {

    private final CareRecordUseCase careRecordUseCase;

    @Operation(operationId = "devRaiseCareRecord", summary = "[개발용] 대상자 기록 추가",
            description = """
                    시스템 감지를 흉내 내 기록을 추가합니다. 덮어쓰기 규칙이 그대로 적용됩니다.
                    현재 기록보다 같거나 높으면 현재 기록이 되고(처리 상태 미확인), 낮으면 흡수됩니다.
                    """)
    @PostMapping("/{careRecipientId}/records")
    public ApiResponseDto<CareRecordResponse> raise(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "대상자 식별자 (public_id)") @PathVariable String careRecipientId,
            @Valid @RequestBody RaiseRequest request) {
        return ApiResponseDto.onSuccess(careRecordUseCase.raise(actor, careRecipientId, request.statusCode(),
                request.reasonType(), request.reasonMessage(), request.detectedAt()));
    }

    public record RaiseRequest(
            @Schema(description = "INTEREST / CAUTION / URGENT", example = "URGENT") @NotNull CareStatusCode statusCode,
            @Schema(description = "사유 종류 (선택)", example = "CONNECT_REQUEST") String reasonType,
            @Schema(description = "카드 문구", example = "담당자와의 연결을 요청했어요.") @NotBlank String reasonMessage,
            @Schema(description = "감지 시각 (선택, 없으면 지금)") LocalDateTime detectedAt) {
    }
}
