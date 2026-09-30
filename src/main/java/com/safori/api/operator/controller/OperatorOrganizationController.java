package com.safori.api.operator.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.common.dto.PagedResponse;
import com.safori.api.guardian.dto.GuardianDetailResponse;
import com.safori.api.guardian.dto.RegisterGuardianRequest;
import com.safori.api.operator.dto.ChangeOrganizationStatusRequest;
import com.safori.api.operator.dto.CreateOrganizationRequest;
import com.safori.api.operator.dto.CreateOrganizationResponse;
import com.safori.api.operator.dto.OrganizationDetailResponse;
import com.safori.api.operator.dto.OrganizationSummaryResponse;
import com.safori.api.operator.dto.RenameOrganizationRequest;
import com.safori.api.operator.dto.ReplaceOrganizationAdminRequest;
import com.safori.api.operator.dto.ReplaceOrganizationAdminResponse;
import com.safori.api.operator.service.CreateOrganizationUseCase;
import com.safori.api.operator.service.OperatorOrganizationUseCase;
import com.safori.api.recipient.dto.RegisterRecipientResponse;
import com.safori.api.user.dto.UserRegisterRequest;
import com.safori.api.worker.dto.RegisterWorkerRequest;
import com.safori.api.worker.dto.RegisterWorkerResponse;
import com.safori.domain.organization.entity.OrganizationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.safori.security.filter.OperatorKeyFilter.HEADER;

@Tag(name = "operator",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[운영자]")),
     description = "SAFORI 운영자 전용. `X-Operator-Key` 헤더로 인증한다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/operator/organizations")
public class OperatorOrganizationController {

    private final CreateOrganizationUseCase createOrganizationUseCase;
    private final OperatorOrganizationUseCase operatorOrganizationUseCase;

    @Operation(operationId = "create", summary = "기관 + 최초 기관 관리자 생성",
            description = """
                    기관을 만들고(기본 역할·그룹 포함) 관리자 계정을 ACTIVE 상태의 기관 관리자(ORG_ADMIN)로 등록한다.
                    기관 관리자는 기관당 1명이다.
                    """,
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true,
                    description = "운영자 키"))
    @ApiResponse(responseCode = "200", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = "- `4350`: 이미 사용 중인 로그인 아이디입니다")
    @ApiResponse(responseCode = "401", description = "운영자 키가 없거나 틀림, 또는 서버에 운영자 키 미설정")
    @PostMapping
    public ApiResponseDto<CreateOrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        return ApiResponseDto.onSuccess(createOrganizationUseCase.execute(request));
    }

    @Operation(operationId = "listOrganizations", summary = "기관 목록 조회",
            description = """
                    기관명 부분 일치(`keyword`)와 상태(`status`)로 거른 기관 목록입니다. 최근 생성순입니다.
                    관리자(아이디·이름)와 담당자 수(소속 종료 제외)·대상자 수(활성)를 함께 줍니다.
                    """,
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "운영자 키가 없거나 틀림")
    @GetMapping
    public ApiResponseDto<PagedResponse<OrganizationSummaryResponse>> list(
            @Parameter(description = "기관명 검색 (부분 일치)") @RequestParam(required = false) String keyword,
            @Parameter(description = "기관 상태 (비우면 전체)") @RequestParam(required = false) OrganizationStatus status,
            @Parameter(description = "페이지 (1부터)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponseDto.onSuccess(operatorOrganizationUseCase.list(keyword, status, page, size));
    }

    @Operation(operationId = "getOrganization", summary = "기관 상세 조회",
            description = "기관 정보와 관리자(연락처·계정 상태 포함), 담당자·보호자 수(소속 종료 제외)·대상자 수(활성)입니다.",
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "- `4308`: 존재하지 않는 기관입니다")
    @GetMapping("/{organizationPublicId}")
    public ApiResponseDto<OrganizationDetailResponse> get(
            @Parameter(description = "기관 식별자 (public_id)") @PathVariable String organizationPublicId) {
        return ApiResponseDto.onSuccess(operatorOrganizationUseCase.get(organizationPublicId));
    }

    @Operation(operationId = "renameOrganization", summary = "기관 이름 변경",
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "200", description = "변경 성공 — result 없음")
    @ApiResponse(responseCode = "400", description = "- `4000`: 입력값 형식 오류")
    @ApiResponse(responseCode = "404", description = "- `4308`: 존재하지 않는 기관입니다")
    @PatchMapping("/{organizationPublicId}")
    public ApiResponseDto<Void> rename(
            @Parameter(description = "기관 식별자 (public_id)") @PathVariable String organizationPublicId,
            @Valid @RequestBody RenameOrganizationRequest request) {
        operatorOrganizationUseCase.rename(organizationPublicId, request.name());
        return ApiResponseDto.onSuccess(null);
    }

    @Operation(operationId = "changeOrganizationStatus", summary = "기관 상태 변경",
            description = """
                    `INACTIVE`로 바꾸면 관리자를 포함한 소속 구성원 전원이 다음 요청부터 권한을 잃고(로그인 중이어도),
                    새 구성원도 받을 수 없습니다. `ACTIVE`로 되돌리면 그대로 돌아옵니다.
                    """,
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "200", description = "변경 성공 — result 없음")
    @ApiResponse(responseCode = "404", description = "- `4308`: 존재하지 않는 기관입니다")
    @PatchMapping("/{organizationPublicId}/status")
    public ApiResponseDto<Void> changeStatus(
            @Parameter(description = "기관 식별자 (public_id)") @PathVariable String organizationPublicId,
            @Valid @RequestBody ChangeOrganizationStatusRequest request) {
        operatorOrganizationUseCase.changeStatus(organizationPublicId, request.status());
        return ApiResponseDto.onSuccess(null);
    }

    @Operation(operationId = "replaceOrganizationAdmin", summary = "기관 관리자 교체",
            description = """
                    새 관리자 계정을 만들어 기관 관리자로 바꿉니다. 기존 관리자는 소속이 종료되고 계정이 정지돼
                    로그인 중이어도 다음 요청부터 끊깁니다. 관리자가 없는 기관이면 새로 지정만 합니다.
                    새 아이디가 중복이면 아무것도 바뀌지 않습니다.
                    """,
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "200", description = "교체 성공")
    @ApiResponse(responseCode = "400", description = """
            - `4000`: 입력값 형식 오류 (휴대폰 번호 등)
            - `4350`: 이미 사용 중인 로그인 아이디입니다
            - `4300`: 비활성화된 기관입니다
            """)
    @ApiResponse(responseCode = "404", description = "- `4308`: 존재하지 않는 기관입니다")
    @PutMapping("/{organizationPublicId}/admin")
    public ApiResponseDto<ReplaceOrganizationAdminResponse> replaceAdmin(
            @Parameter(description = "기관 식별자 (public_id)") @PathVariable String organizationPublicId,
            @Valid @RequestBody ReplaceOrganizationAdminRequest request) {
        return ApiResponseDto.onSuccess(operatorOrganizationUseCase.replaceAdmin(organizationPublicId, request));
    }

    @Operation(operationId = "operatorRegisterManager", summary = "기관 담당자 계정 생성 (테스트·지원용)",
            description = """
                    운영자가 기관의 담당자 계정을 바로 만듭니다. 요청은 관리자의 담당자 등록(`POST /v1/api/admin/managers`)과 같고,
                    등록자만 SAFORI 운영으로 남습니다.
                    """,
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "200", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = """
            - `4000`: 입력값 형식 오류
            - `4350`: 이미 사용 중인 로그인 아이디입니다
            - `4300`: 비활성화된 기관입니다
            """)
    @ApiResponse(responseCode = "404", description = "- `4308`: 존재하지 않는 기관입니다")
    @PostMapping("/{organizationPublicId}/managers")
    public ApiResponseDto<RegisterWorkerResponse> registerManager(
            @Parameter(description = "기관 식별자 (public_id)") @PathVariable String organizationPublicId,
            @Valid @RequestBody RegisterWorkerRequest request) {
        return ApiResponseDto.onSuccess(operatorOrganizationUseCase.registerManager(organizationPublicId, request));
    }

    @Operation(operationId = "operatorRegisterGuardian", summary = "기관 보호자 계정 생성 (테스트·지원용)",
            description = """
                    운영자가 기관의 보호자 계정을 바로 만듭니다. 요청은 관리자의 보호자 등록(`POST /v1/api/admin/guardians`)과 같아
                    `careRecipientId`·`relation`을 보내면 그 기관 대상자에 바로 연결합니다.
                    """,
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "200", description = "생성 성공 — 보호자 상세 반환")
    @ApiResponse(responseCode = "400", description = """
            - `4000`: 입력값 형식 오류
            - `4350`: 이미 사용 중인 로그인 아이디입니다
            - `4454`: 존재하지 않는 대상자입니다 (다른 기관 대상자 포함)
            - `4300`: 비활성화된 기관입니다
            """)
    @ApiResponse(responseCode = "404", description = "- `4308`: 존재하지 않는 기관입니다")
    @PostMapping("/{organizationPublicId}/guardians")
    public ApiResponseDto<GuardianDetailResponse> registerGuardian(
            @Parameter(description = "기관 식별자 (public_id)") @PathVariable String organizationPublicId,
            @Valid @RequestBody RegisterGuardianRequest request) {
        return ApiResponseDto.onSuccess(operatorOrganizationUseCase.registerGuardian(organizationPublicId, request));
    }

    @Operation(operationId = "operatorRegisterCareRecipient", summary = "대상자(어르신) 계정 생성 (테스트·지원용)",
            description = """
                    어르신 앱 계정을 만들고 바로 그 기관의 대상자로 등록합니다(앱 회원가입 + 대상자 추가).
                    요청은 앱 회원가입(`POST /v1/api/users/sign-up`)과 같고, 만든 계정으로 어르신 앱에 로그인할 수 있습니다.
                    """,
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "200", description = "생성 성공 — 대상자 식별자 반환")
    @ApiResponse(responseCode = "400", description = """
            - `4000`: 입력값 형식 오류
            - `4050`: 이미 존재하는 username입니다
            - `4300`: 비활성화된 기관입니다
            """)
    @ApiResponse(responseCode = "404", description = "- `4308`: 존재하지 않는 기관입니다")
    @PostMapping("/{organizationPublicId}/care-recipients")
    public ApiResponseDto<RegisterRecipientResponse> registerCareRecipient(
            @Parameter(description = "기관 식별자 (public_id)") @PathVariable String organizationPublicId,
            @Valid @RequestBody UserRegisterRequest request) {
        return ApiResponseDto.onSuccess(operatorOrganizationUseCase.registerRecipient(organizationPublicId, request));
    }
}
