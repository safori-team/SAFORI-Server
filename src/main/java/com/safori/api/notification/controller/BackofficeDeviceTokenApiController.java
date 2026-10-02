package com.safori.api.notification.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.notification.dto.DeviceTokenRegisterRequest;
import com.safori.api.notification.service.DeleteDeviceTokenUseCase;
import com.safori.api.notification.service.RegisterDeviceTokenUseCase;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "admin-device-token",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[알림 기기 등록]")),
     description = """
             관리자·담당자·보호자의 FCM 푸시 알림용 디바이스 토큰 등록/삭제 API.
             - 즉시 확인 진입: 관리자·담당자·보호자에게 바로 (`data.type = CARE_URGENT`)
             - 주의 48시간 미확인: 담당자·보호자에게 (`data.type = CARE_CAUTION_UNCHECKED`)
             - `data.careRecipientId`, `data.recordId`로 대상자·기록 화면으로 이동할 수 있습니다.
             """)
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/admin/device-tokens")
public class BackofficeDeviceTokenApiController {

    private final RegisterDeviceTokenUseCase registerDeviceTokenUseCase;
    private final DeleteDeviceTokenUseCase deleteDeviceTokenUseCase;

    @Operation(operationId = "registerAdminDeviceToken", summary = "알림 기기 등록",
            description = "로그인 후 FCM 토큰을 등록합니다. 같은 토큰이 이미 있으면 지금 로그인한 계정으로 소유자를 바꿉니다(upsert).")
    @ApiResponse(responseCode = "200", description = "등록 성공 — deviceTokenId 반환")
    @ApiResponse(responseCode = "401", description = "로그인 필요 (유효한 토큰 없음)")
    @PostMapping
    public ApiResponseDto<Long> register(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @Valid @RequestBody DeviceTokenRegisterRequest request) {
        return ApiResponseDto.onSuccess(registerDeviceTokenUseCase.executeForAccount(actor.accountId(), request));
    }

    @Operation(operationId = "deleteAdminDeviceToken", summary = "알림 기기 삭제",
            description = "로그아웃 시 FCM 토큰을 삭제합니다. 본인 소유 토큰만 삭제되며, 없어도 성공으로 응답합니다(멱등).")
    @ApiResponse(responseCode = "200", description = "삭제 성공")
    @ApiResponse(responseCode = "401", description = "로그인 필요 (유효한 토큰 없음)")
    @DeleteMapping
    public ApiResponseDto<String> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal BackofficeActor actor,
            @RequestParam String token) {
        deleteDeviceTokenUseCase.executeForAccount(actor.accountId(), token);
        return ApiResponseDto.onSuccess("deleted");
    }
}
