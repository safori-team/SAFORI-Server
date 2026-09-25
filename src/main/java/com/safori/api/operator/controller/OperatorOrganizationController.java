package com.safori.api.operator.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.operator.dto.CreateOrganizationRequest;
import com.safori.api.operator.dto.CreateOrganizationResponse;
import com.safori.api.operator.service.CreateOrganizationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.safori.security.filter.OperatorKeyFilter.HEADER;

@Tag(name = "operator",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[운영자]")),
     description = "SAFORI 운영자 전용. `X-Operator-Key` 헤더로 인증한다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/operator/organizations")
public class OperatorOrganizationController {

    private final CreateOrganizationUseCase createOrganizationUseCase;

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
}
