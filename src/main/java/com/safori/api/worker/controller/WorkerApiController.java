package com.safori.api.worker.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.worker.dto.ManagerDetailResponse;
import com.safori.api.worker.dto.ManagerListResponse;
import com.safori.api.worker.dto.ManagerRecipientsResponse;
import com.safori.api.worker.dto.ManagerStatusFilter;
import com.safori.api.worker.dto.RegisterWorkerRequest;
import com.safori.api.worker.dto.RegisterWorkerResponse;
import com.safori.api.worker.dto.ReplaceManagerRecipientsRequest;
import com.safori.api.worker.dto.UpdateWorkerRequest;
import com.safori.api.worker.dto.WorkerProfileResponse;
import com.safori.api.worker.service.GetWorkerUseCase;
import com.safori.api.worker.service.ListWorkersUseCase;
import com.safori.api.worker.service.RegisterWorkerUseCase;
import com.safori.api.worker.service.UpdateWorkerUseCase;
import com.safori.api.worker.service.WorkerAssignmentUseCase;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "manager",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[담당자]")),
     description = "담당자 관리 API. 백오피스 토큰이 필요하다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/admin/managers")
public class WorkerApiController {

    private final RegisterWorkerUseCase registerWorkerUseCase;
    private final UpdateWorkerUseCase updateWorkerUseCase;
    private final ListWorkersUseCase listWorkersUseCase;
    private final GetWorkerUseCase getWorkerUseCase;
    private final WorkerAssignmentUseCase workerAssignmentUseCase;

    @Operation(operationId = "registerManager", summary = "담당자 등록",
            description = """
                    담당자 계정을 만들어 로그인한 관리자의 기관에 바로 소속시킵니다(승인 절차 없음).
                    `active=false`면 계정을 비활성화 상태로 만들어 로그인할 수 없습니다.
                    아이디는 어르신·백오피스 계정을 통틀어 유일해야 합니다.
                    """)
    @ApiResponse(responseCode = "200", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = """
            - `4000`: 입력값 형식 오류 (아이디·비밀번호·연락처 등)
            - `4350`: 이미 사용 중인 로그인 아이디입니다
            - `4300`: 비활성화된 기관입니다
            """)
    @PostMapping
    public ApiResponseDto<RegisterWorkerResponse> register(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Valid @RequestBody RegisterWorkerRequest request) {
        return ApiResponseDto.onSuccess(registerWorkerUseCase.execute(actor, request));
    }

    @Operation(operationId = "updateManager", summary = "담당자 정보 수정",
            description = """
                    등록 폼의 기본 정보(이름·연락처·직종·계정 상태)를 수정합니다. 아이디·비밀번호는 바꾸지 않습니다.
                    `active=false`로 바꾸면 담당자가 로그인 중이어도 다음 요청부터 끊깁니다.
                    """)
    @ApiResponse(responseCode = "200", description = "수정 성공 — 수정된 기본 정보 반환")
    @ApiResponse(responseCode = "400", description = """
            - `4000`: 입력값 형식 오류
            - `4307`: 존재하지 않는 구성원입니다 (다른 기관 소속이거나 담당자가 아님)
            """)
    @PutMapping("/{managerId}")
    public ApiResponseDto<WorkerProfileResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "담당자 식별자 (계정 UUID)") @PathVariable String managerId,
            @Valid @RequestBody UpdateWorkerRequest request) {
        return ApiResponseDto.onSuccess(updateWorkerUseCase.execute(actor, managerId, request));
    }

    @Operation(operationId = "listManagers", summary = "담당자 목록 조회",
            description = """
                    로그인한 구성원 기관의 담당자 목록입니다. 담당자 목록 화면과 담당자 변경(선택) 화면이 같이 씁니다.
                    `counts`는 탭 개수(전체·활성화·비활성화)로, 검색어는 적용하고 `status`와는 무관합니다.
                    담당자 변경 화면에서는 배정할 수 있는 `status=ACTIVE`로 부르세요. 정렬은 이름순입니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ApiResponseDto<ManagerListResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "탭 (ALL / ACTIVE / INACTIVE)") @RequestParam(defaultValue = "ALL") ManagerStatusFilter status,
            @Parameter(description = "이름 검색 (부분 일치)") @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 (1부터)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponseDto.onSuccess(listWorkersUseCase.execute(actor, status, keyword, page, size));
    }

    @Operation(operationId = "getManager", summary = "담당자 상세 조회",
            description = "담당자 기본 정보(소속기관·직종·역할·계정 상태)와 현재 배정 대상자 목록(이름순)입니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "- `4307`: 존재하지 않는 구성원입니다")
    @GetMapping("/{managerId}")
    public ApiResponseDto<ManagerDetailResponse> get(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "담당자 식별자 (계정 UUID)") @PathVariable String managerId) {
        return ApiResponseDto.onSuccess(getWorkerUseCase.execute(actor, managerId));
    }

    @Operation(operationId = "replaceManagerRecipients", summary = "담당자 배정 인원 일괄 변경",
            description = """
                    담당자 상세 → 배정 인원 변경 화면에서 체크한 대상자 전체를 보내면, 담당자의 배정을 그 목록과 똑같이 맞춥니다.
                    - 새로 체크한 대상자: 이 담당자로 배정 (다른 담당자였으면 옮김)
                    - 체크를 해제한 대상자: 배정 해제 (미배정)
                    잘못된 대상자 식별자가 하나라도 있으면 아무것도 바꾸지 않습니다.
                    """)
    @ApiResponse(responseCode = "200", description = "변경 성공 — 변경 후 배정 대상자")
    @ApiResponse(responseCode = "400", description = """
            - `4307`: 존재하지 않는 구성원입니다
            - `4454`: 존재하지 않는 대상자입니다
            - `4451`: 비활성화된 어르신입니다
            - `4452`: 배정할 수 없는 담당자입니다 (비활성 담당자 등)
            """)
    @PutMapping("/{managerId}/care-recipients")
    public ApiResponseDto<ManagerRecipientsResponse> replaceRecipients(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "담당자 식별자 (계정 UUID)") @PathVariable String managerId,
            @Valid @RequestBody ReplaceManagerRecipientsRequest request) {
        return ApiResponseDto.onSuccess(workerAssignmentUseCase.replace(actor, managerId, request.careRecipientIds()));
    }
}
