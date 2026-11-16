package com.safori.api.emergency.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.emergency.dto.EmergencyCallResponse;
import com.safori.api.emergency.service.EmergencyCallUseCase;
import com.safori.common.annotation.UserCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "emergency-call",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[긴급 전화]")),
     description = "어르신 앱 긴급 전화(119) 기록 API.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/emergency-calls")
public class EmergencyCallApiController {

    private final EmergencyCallUseCase emergencyCallUseCase;

    @Operation(operationId = "recordEmergencyCall", summary = "긴급 전화 기록",
            description = """
                    긴급 전화 확인 모달에서 "전화 앱 열기"를 누를 때 119 전화 화면을 여는 것과 함께 호출합니다. 요청 본문은 없습니다.
                    어르신이 복지관(기관)에 등록된 대상자면 대상자 현황에 **즉시 확인**(도움 요청, "119에 SOS 요청을 했어요.")을 올립니다.
                    누를 때마다 새 기록이 올라가고, 진행 중이던 확인 사유는 이력으로 넘어갑니다.
                    - `notified=true`: 복지관에 올라갔다 ("담당자에게도 알렸어요" 안내 가능)
                    - `notified=false`: 기관에 등록되지 않았거나 이용이 중지된 어르신이라 올리지 않았다
                    전화 걸기를 막지 않도록, 앱은 이 응답을 기다리지 않고 전화 화면을 열면 됩니다.
                    """)
    @ApiResponse(responseCode = "200", description = "기록 성공 — 복지관 알림 여부 반환")
    @ApiResponse(responseCode = "401", description = "로그인 필요 (유효한 토큰 없음)")
    @PostMapping
    public ApiResponseDto<EmergencyCallResponse> record(@UserCode String username) {
        return ApiResponseDto.onSuccess(emergencyCallUseCase.execute(username));
    }
}
