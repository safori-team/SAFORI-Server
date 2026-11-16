package com.safori.api.guardian.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.guardian.dto.GuardianDetailResponse;
import com.safori.api.guardian.dto.GuardianListResponse;
import com.safori.api.guardian.dto.GuardianStatusFilter;
import com.safori.api.guardian.dto.LinkGuardianRequest;
import com.safori.api.guardian.dto.RegisterGuardianRequest;
import com.safori.api.guardian.dto.UpdateGuardianRequest;
import com.safori.api.guardian.service.GuardianUseCase;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "guardian",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[보호자]")),
     description = "보호자 관리·대상자 연결 API. 백오피스 토큰이 필요하다. 보호자는 대상자 한 명에만 연결된다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/admin")
public class GuardianApiController {

    private final GuardianUseCase guardianUseCase;

    @Operation(operationId = "registerGuardian", summary = "보호자 등록",
            description = """
                    보호자 계정을 만들어 로그인한 관리자의 기관에 바로 소속시킵니다(승인 절차 없음).
                    `careRecipientId`를 보내면 관계(`relation`)와 함께 그 대상자에 바로 연결하고, 비우면 연결하지 않습니다.
                    관계가 `OTHER`(기타)면 `relationText`가 필요합니다. 아이디는 어르신·백오피스 계정을 통틀어 유일해야 합니다.
                    """)
    @ApiResponse(responseCode = "200", description = "등록 성공 — 보호자 상세 반환")
    @ApiResponse(responseCode = "400", description = """
            - `4000`: 입력값 형식 오류 (아이디·비밀번호·연락처, 대상자를 골랐는데 관계 없음 등)
            - `4350`: 이미 사용 중인 로그인 아이디입니다
            - `4454`: 존재하지 않는 대상자입니다
            - `4451`: 비활성화된 어르신입니다
            """)
    @PostMapping("/guardians")
    public ApiResponseDto<GuardianDetailResponse> register(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Valid @RequestBody RegisterGuardianRequest request) {
        return ApiResponseDto.onSuccess(guardianUseCase.register(actor, request));
    }

    @Operation(operationId = "listGuardians", summary = "보호자 목록 조회",
            description = """
                    로그인한 구성원 기관의 보호자 목록입니다. 대상자 상세 → 보호자 연결(선택) 화면도 같이 씁니다.
                    - `status`: `ALL` / `LINKED`(연결) / `UNLINKED`(미연결). 연결 화면에서는 `UNLINKED`로 부르세요.
                    - `keyword`: 보호자 이름 또는 연결 대상자 이름 (부분 일치)
                    `counts`는 탭 개수(검색어 적용, `status`와 무관)입니다. 정렬은 이름순입니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/guardians")
    public ApiResponseDto<GuardianListResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "탭 (ALL / LINKED / UNLINKED)") @RequestParam(defaultValue = "ALL") GuardianStatusFilter status,
            @Parameter(description = "보호자·대상자 이름 검색 (부분 일치)") @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 (1부터)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponseDto.onSuccess(guardianUseCase.list(actor, status, keyword, page, size));
    }

    @Operation(operationId = "getGuardian", summary = "보호자 상세 조회",
            description = "보호자 기본 정보(연락처·가입일·계정 상태)와 연결 대상자(이름·생년월일·관계·연결일)입니다. 연결이 없으면 `careRecipient`가 null입니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "- `4307`: 존재하지 않는 구성원입니다 (다른 기관 소속이거나 보호자가 아님)")
    @GetMapping("/guardians/{guardianId}")
    public ApiResponseDto<GuardianDetailResponse> get(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "보호자 식별자 (계정 UUID)") @PathVariable String guardianId) {
        return ApiResponseDto.onSuccess(guardianUseCase.get(actor, guardianId));
    }

    @Operation(operationId = "updateGuardian", summary = "보호자 정보 수정",
            description = """
                    이름·연락처·계정 상태를 수정합니다. 아이디·비밀번호는 바꾸지 않고, 관계는 보호자 연결 API로 바꿉니다.
                    `active=false`로 바꾸면 보호자가 로그인 중이어도 다음 요청부터 끊깁니다. 연결은 그대로 둡니다.
                    """)
    @ApiResponse(responseCode = "200", description = "수정 성공 — 보호자 상세 반환")
    @ApiResponse(responseCode = "400", description = """
            - `4000`: 입력값 형식 오류
            - `4307`: 존재하지 않는 구성원입니다
            """)
    @PutMapping("/guardians/{guardianId}")
    public ApiResponseDto<GuardianDetailResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "보호자 식별자 (계정 UUID)") @PathVariable String guardianId,
            @Valid @RequestBody UpdateGuardianRequest request) {
        return ApiResponseDto.onSuccess(guardianUseCase.update(actor, guardianId, request));
    }

    @Operation(operationId = "linkGuardian", summary = "보호자 연결",
            description = """
                    대상자에 보호자를 관계와 함께 연결합니다. 대상자 한 명에 보호자 여럿이 연결될 수 있지만,
                    보호자는 대상자 한 명에만 연결됩니다. 이미 이 대상자와 연결된 보호자면 관계만 바꿉니다.
                    """)
    @ApiResponse(responseCode = "200", description = "연결 성공 — 보호자 상세 반환")
    @ApiResponse(responseCode = "400", description = """
            - `4000`: 입력값 형식 오류 (기타인데 relationText 없음 등)
            - `4454`: 존재하지 않는 대상자입니다
            - `4307`: 존재하지 않는 구성원입니다 (보호자가 아님)
            - `4451`: 비활성화된 어르신입니다
            - `4453`: 연결할 수 없는 보호자입니다 (비활성 보호자 등)
            - `4459`: 이미 다른 대상자와 연결된 보호자입니다
            """)
    @PutMapping("/care-recipients/{careRecipientId}/guardians")
    public ApiResponseDto<GuardianDetailResponse> link(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "대상자 식별자 (public_id)") @PathVariable String careRecipientId,
            @Valid @RequestBody LinkGuardianRequest request) {
        return ApiResponseDto.onSuccess(guardianUseCase.link(actor, careRecipientId, request));
    }

    @Operation(operationId = "unlinkGuardian", summary = "보호자 연결 해제",
            description = "대상자와 보호자의 연결을 끊습니다. 연결 이력은 남고, 이미 끊겨 있으면 아무것도 하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "해제 성공 — 보호자 상세 반환 (careRecipient는 null)")
    @ApiResponse(responseCode = "400", description = """
            - `4454`: 존재하지 않는 대상자입니다
            - `4307`: 존재하지 않는 구성원입니다
            """)
    @DeleteMapping("/care-recipients/{careRecipientId}/guardians/{guardianId}")
    public ApiResponseDto<GuardianDetailResponse> unlink(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Parameter(description = "대상자 식별자 (public_id)") @PathVariable String careRecipientId,
            @Parameter(description = "보호자 식별자 (계정 UUID)") @PathVariable String guardianId) {
        return ApiResponseDto.onSuccess(guardianUseCase.unlink(actor, careRecipientId, guardianId));
    }
}
