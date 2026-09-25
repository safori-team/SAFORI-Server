package com.safori.api.worker.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.worker.dto.RegisterWorkerRequest;
import com.safori.api.worker.dto.RegisterWorkerResponse;
import com.safori.api.worker.service.RegisterWorkerUseCase;
import com.safori.domain.access.policy.BackofficeActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "worker",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[담당자]")),
     description = "담당자 관리 API. 백오피스 토큰이 필요하다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/backoffice/workers")
public class WorkerApiController {

    private final RegisterWorkerUseCase registerWorkerUseCase;

    @Operation(operationId = "registerWorker", summary = "담당자 등록",
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
}
