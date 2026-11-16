package com.safori.api.account.controller;

import com.safori.api.account.dto.MyAccountResponse;
import com.safori.api.account.dto.UpdateMyAccountRequest;
import com.safori.api.account.service.UpdateMyAccountUseCase;
import com.safori.api.common.dto.ApiResponseDto;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "my-account",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[내 정보]")),
     description = "관리자·담당자·보호자 본인 정보 수정 API. 조회는 GET /v1/api/users.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/admin/me")
public class MyAccountApiController {

    private final UpdateMyAccountUseCase updateMyAccountUseCase;

    @Operation(operationId = "updateMyAccount", summary = "내 정보 수정",
            description = """
                    바꾸지 않는 항목은 null로 보냅니다.
                    - 관리자: 이름·연락처·아이디·비밀번호
                    - 담당자·보호자: 비밀번호만 (아이디는 지금 값과 같으면 보내도 됨). 연락처 변경은 기관 관리자에게 문의
                    아이디를 바꿔도 로그인은 유지됩니다. 다음 로그인부터 새 아이디를 씁니다.
                    """)
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = """
            - `4000`: 입력값 형식 오류
            - `4350`: 이미 사용 중인 로그인 아이디입니다
            - `4352`: 기관 관리자만 수정할 수 있는 항목입니다 (담당자·보호자가 이름·연락처·아이디 변경)
            """)
    @PutMapping
    public ApiResponseDto<MyAccountResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Valid @RequestBody UpdateMyAccountRequest request) {
        return ApiResponseDto.onSuccess(updateMyAccountUseCase.execute(actor, request));
    }
}
