package com.safori.api.journal.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.journal.dto.JournalDetailResponse;
import com.safori.api.journal.dto.JournalFormResponse;
import com.safori.api.journal.dto.JournalListResponse;
import com.safori.api.journal.dto.WriteJournalRequest;
import com.safori.api.journal.service.CareJournalUseCase;
import com.safori.domain.access.policy.BackofficeActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "care-journal",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[일지]")),
     description = "담당자 일지(최근 조치 기록) API. 백오피스 토큰이 필요하다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/admin")
public class JournalApiController {

    private final CareJournalUseCase careJournalUseCase;

    @Operation(operationId = "getJournalForm", summary = "일지 폼 항목 조회",
            description = """
                    새 일지 작성 화면의 섹션과 항목입니다. 항목은 하위 항목(children)을 몇 단계든 가질 수 있습니다.
                    화면을 고정해서 만들어도 되고, 이 응답으로 그리면 항목이 바뀌어도 앱을 고치지 않아도 됩니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/journal-form")
    public ApiResponseDto<JournalFormResponse> form() {
        return ApiResponseDto.onSuccess(careJournalUseCase.form());
    }

    @Operation(operationId = "writeJournal", summary = "일지 등록",
            description = """
                    고른 항목 코드를 평평한 목록으로 보냅니다(하위 항목도 같은 목록에). 서버가 폼 규칙을 검사합니다.
                    - 단일 선택 섹션(확인 방식·확인 결과)은 1개, 필수 섹션(대상자 상태 포함)은 1개 이상
                    - '특이사항 없음'은 다른 대상자 상태와 함께 고를 수 없음
                    - 하위 항목은 부모 항목을 같이 골라야 함, '기타'는 text 필수
                    작성 당시 확인 필요도·처리 상태가 함께 저장됩니다. 처리 상태는 바꾸지 않습니다(토글은 별도 API).
                    """)
    @ApiResponse(responseCode = "200", description = "등록 성공 — 일지 상세 반환")
    @ApiResponse(responseCode = "400", description = """
            - `4454`: 존재하지 않는 대상자입니다
            - `4457`: 일지 항목 선택이 올바르지 않습니다
            """)
    @PostMapping("/care-recipients/{careRecipientId}/journals")
    public ApiResponseDto<JournalDetailResponse> write(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "대상자 식별자 (public_id)") @PathVariable String careRecipientId,
            @Valid @RequestBody WriteJournalRequest request) {
        return ApiResponseDto.onSuccess(careJournalUseCase.write(actor, careRecipientId, request));
    }

    @Operation(operationId = "getJournal", summary = "일지 상세 조회",
            description = "섹션별로 고른 항목(작성 당시 문구)과 확인 일시·작성 일시·작성 당시 상태를 줍니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = """
            - `4454`: 존재하지 않는 대상자입니다
            - `4458`: 존재하지 않는 일지입니다
            """)
    @GetMapping("/care-recipients/{careRecipientId}/journals/{journalId}")
    public ApiResponseDto<JournalDetailResponse> get(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "대상자 식별자 (public_id)") @PathVariable String careRecipientId,
            @Parameter(description = "일지 식별자") @PathVariable String journalId) {
        return ApiResponseDto.onSuccess(careJournalUseCase.get(actor, careRecipientId, journalId));
    }

    @Operation(operationId = "listJournals", summary = "일지 목록 조회",
            description = """
                    일지 목록 화면입니다. 관리자는 기관 전체, 담당자는 본인에게 배정된 대상자의 일지만 보입니다.
                    - `from`~`to`: 확인 일시 기준 기간(양 끝 포함). 비우면 최근 30일(오늘 포함)이고, 적용된 기간을 응답에 담습니다.
                    - `keyword`: 대상자 이름 또는 작성자 이름 (부분 일치)
                    - `managerId`: 작성자(담당자) 필터 (계정 UUID). 담당자 화면에서는 보내지 않습니다.
                    정렬은 확인 일시 최신순 고정입니다. 확인 필요도는 일지 작성 당시 값입니다. 전체 건수는 `journals.totalElements`입니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "- `4460`: 조회 기간이 올바르지 않습니다 (시작일이 종료일보다 늦음)")
    @GetMapping("/journals")
    public ApiResponseDto<JournalListResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "대상자·작성자 이름 검색") @RequestParam(required = false) String keyword,
            @Parameter(description = "작성자 필터 (계정 UUID)") @RequestParam(required = false) String managerId,
            @Parameter(description = "조회 시작일 (확인 일시 기준)", example = "2026-09-01") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "조회 종료일 (이날 포함)", example = "2026-09-30") @RequestParam(required = false) LocalDate to,
            @Parameter(description = "페이지 (1부터)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponseDto.onSuccess(careJournalUseCase.list(actor, keyword, managerId, from, to, page, size));
    }
}
