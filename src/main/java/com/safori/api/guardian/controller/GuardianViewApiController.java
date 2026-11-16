package com.safori.api.guardian.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.journal.dto.JournalDetailResponse;
import com.safori.api.journal.dto.JournalListResponse;
import com.safori.api.journal.service.CareJournalUseCase;
import com.safori.api.recipient.dto.RecipientListResponse;
import com.safori.api.recipient.service.ListRecipientsUseCase;
import com.safori.domain.access.policy.BackofficeActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 보호자 화면용 조회. 응답 형식은 직원용({@code /v1/api/admin/...})과 같고 경로 앞부분만 다르다.
 * 권한은 GUARDIAN_STATUS_READ 하나이고, 범위는 로그인한 보호자에게 현재 연결된 대상자·보호자 공개 일지다.
 */
@Tag(name = "guardian-view",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[보호자 화면]")),
     description = "보호자 화면(대상자 현황·최근 안부 확인·일지 상세) 조회 API. 응답 형식은 직원용 API와 같다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/guardian")
public class GuardianViewApiController {

    private final ListRecipientsUseCase listRecipientsUseCase;
    private final CareJournalUseCase careJournalUseCase;

    @Operation(operationId = "guardianListCareRecipients", summary = "연결된 대상자 조회 (보호자)",
            description = """
                    보호자 홈 상단의 어르신 정보입니다. 형식은 `GET /v1/api/admin/care-recipients`와 같습니다.
                    보호자는 대상자 한 명에만 연결되므로 보통 0~1건입니다.
                    보호자 권한 밖의 값(현재 상태 코드·사유·처리 상태·담당자·최근 안부 확인)은 null이고, `counts`는 `total`만 채웁니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/care-recipients")
    public ApiResponseDto<RecipientListResponse> listCareRecipients(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "페이지 (1부터)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponseDto.onSuccess(listRecipientsUseCase.forGuardian(actor, page, size));
    }

    @Operation(operationId = "guardianListJournals", summary = "최근 안부 확인 (보호자)",
            description = """
                    연결된 대상자의 **보호자 공개 일지**입니다. 형식은 `GET /v1/api/admin/journals`와 같습니다.
                    기간을 비우면 최근 30일(오늘 포함), 확인 일시 최신순입니다. 상태 코드·사유 문구·처리 상태는 일지 작성 당시 값입니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "- `4460`: 조회 기간이 올바르지 않습니다")
    @GetMapping("/journals")
    public ApiResponseDto<JournalListResponse> listJournals(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "조회 시작일 (확인 일시 기준)", example = "2026-09-01") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "조회 종료일 (이날 포함)", example = "2026-09-30") @RequestParam(required = false) LocalDate to,
            @Parameter(description = "페이지 (1부터)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponseDto.onSuccess(careJournalUseCase.list(actor, null, null, from, to, page, size));
    }

    @Operation(operationId = "guardianGetJournal", summary = "일지 상세 (보호자)",
            description = """
                    연결된 대상자의 보호자 공개 일지 상세입니다. 형식은 직원용 일지 상세와 같고, 상태는 모두 작성 당시 값입니다.
                    비공개 일지는 `4458`, 연결되지 않은 대상자는 403입니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = """
            - `4454`: 존재하지 않는 대상자입니다
            - `4458`: 존재하지 않는 일지입니다 (비공개 일지 포함)
            """)
    @GetMapping("/care-recipients/{careRecipientId}/journals/{journalId}")
    public ApiResponseDto<JournalDetailResponse> getJournal(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "대상자 식별자 (public_id)") @PathVariable String careRecipientId,
            @Parameter(description = "일지 식별자") @PathVariable String journalId) {
        return ApiResponseDto.onSuccess(careJournalUseCase.get(actor, careRecipientId, journalId));
    }
}
