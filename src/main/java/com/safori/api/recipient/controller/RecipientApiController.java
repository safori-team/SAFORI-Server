package com.safori.api.recipient.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.recipient.dto.RecipientLookupResponse;
import com.safori.api.recipient.dto.RegisterRecipientRequest;
import com.safori.api.recipient.dto.RegisterRecipientResponse;
import com.safori.api.recipient.service.LookupRecipientUseCase;
import com.safori.api.recipient.service.RegisterRecipientUseCase;
import com.safori.api.worker.dto.AssignManagerRequest;
import com.safori.api.worker.dto.AssignmentResponse;
import com.safori.api.worker.service.WorkerAssignmentUseCase;
import com.safori.domain.access.policy.BackofficeActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "care-recipient",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[대상자]")),
     description = "기관 대상자(어르신) API. 백오피스 토큰이 필요하다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/admin/care-recipients")
public class RecipientApiController {

    private final LookupRecipientUseCase lookupRecipientUseCase;
    private final RegisterRecipientUseCase registerRecipientUseCase;
    private final WorkerAssignmentUseCase workerAssignmentUseCase;

    @Operation(operationId = "lookupRecipient", summary = "대상자 추가 전 어르신 조회",
            description = """
                    어르신 앱 아이디를 정확히 입력했을 때만 조회됩니다(부분 검색 없음).
                    본인 확인용으로 이름·휴대폰 번호·생년월일은 마스킹해서 반환합니다.
                    `registered=true`면 이미 어느 기관에 등록된 어르신이라 추가할 수 없습니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "- `4052`: 존재하지 않는 유저입니다")
    @GetMapping("/lookup")
    public ApiResponseDto<RecipientLookupResponse> lookup(
            @Parameter(description = "어르신 앱 아이디", example = "elder01") @RequestParam @NotBlank String loginId) {
        return ApiResponseDto.onSuccess(lookupRecipientUseCase.execute(loginId));
    }

    @Operation(operationId = "registerRecipient", summary = "대상자 추가",
            description = """
                    조회한 어르신을 로그인한 구성원의 기관에 대상자로 등록합니다. 기관은 토큰에서 정해집니다.
                    어르신은 한 기관에만 등록됩니다.
                    """)
    @ApiResponse(responseCode = "200", description = "등록 성공 — 대상자 publicId 반환")
    @ApiResponse(responseCode = "400", description = """
            - `4052`: 존재하지 않는 유저입니다
            - `4450`: 이미 기관에 등록된 어르신입니다
            - `4300`: 비활성화된 기관입니다
            """)
    @PostMapping
    public ApiResponseDto<RegisterRecipientResponse> register(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Valid @RequestBody RegisterRecipientRequest request) {
        return ApiResponseDto.onSuccess(registerRecipientUseCase.execute(actor, request.loginId()));
    }

    @Operation(operationId = "assignManager", summary = "담당자 배정",
            description = "미배정 대상자에게 담당자를 배정합니다. 이미 담당자가 있으면 바꿉니다(담당자 변경과 같은 동작).")
    @ApiResponse(responseCode = "200", description = "배정 성공")
    @ApiResponse(responseCode = "400", description = ASSIGN_ERRORS)
    @PostMapping("/{careRecipientId}/manager")
    public ApiResponseDto<AssignmentResponse> assignManager(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "대상자 식별자 (public_id)") @PathVariable String careRecipientId,
            @Valid @RequestBody AssignManagerRequest request) {
        return ApiResponseDto.onSuccess(workerAssignmentUseCase.assign(actor, careRecipientId, request.managerId()));
    }

    @Operation(operationId = "changeManager", summary = "담당자 변경",
            description = "대상자의 담당자를 바꿉니다. 기존 배정은 종료되고 이력이 남습니다. 미배정이면 새로 배정합니다.")
    @ApiResponse(responseCode = "200", description = "변경 성공")
    @ApiResponse(responseCode = "400", description = ASSIGN_ERRORS)
    @PutMapping("/{careRecipientId}/manager")
    public ApiResponseDto<AssignmentResponse> changeManager(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "대상자 식별자 (public_id)") @PathVariable String careRecipientId,
            @Valid @RequestBody AssignManagerRequest request) {
        return ApiResponseDto.onSuccess(workerAssignmentUseCase.assign(actor, careRecipientId, request.managerId()));
    }

    @Operation(operationId = "unassignManager", summary = "담당자 배정 해제",
            description = "대상자를 미배정으로 돌립니다. 배정 이력은 남습니다. 이미 미배정이면 아무것도 하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "해제 성공 — managerId는 null")
    @ApiResponse(responseCode = "400", description = "- `4454`: 존재하지 않는 대상자입니다")
    @DeleteMapping("/{careRecipientId}/manager")
    public ApiResponseDto<AssignmentResponse> unassignManager(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "대상자 식별자 (public_id)") @PathVariable String careRecipientId) {
        return ApiResponseDto.onSuccess(workerAssignmentUseCase.unassign(actor, careRecipientId));
    }

    private static final String ASSIGN_ERRORS = """
            - `4454`: 존재하지 않는 대상자입니다
            - `4307`: 존재하지 않는 구성원입니다 (담당자가 아님)
            - `4451`: 비활성화된 어르신입니다
            - `4452`: 배정할 수 없는 담당자입니다 (비활성 담당자 등)
            """;
}
