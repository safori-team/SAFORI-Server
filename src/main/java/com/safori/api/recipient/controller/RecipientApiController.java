package com.safori.api.recipient.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.recipient.dto.RecipientLookupResponse;
import com.safori.api.recipient.dto.RegisterRecipientRequest;
import com.safori.api.recipient.dto.RegisterRecipientResponse;
import com.safori.api.recipient.service.LookupRecipientUseCase;
import com.safori.api.recipient.service.RegisterRecipientUseCase;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "recipient",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[대상자]")),
     description = "기관 대상자(어르신) API. 백오피스 토큰이 필요하다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/backoffice/recipients")
public class RecipientApiController {

    private final LookupRecipientUseCase lookupRecipientUseCase;
    private final RegisterRecipientUseCase registerRecipientUseCase;

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
}
