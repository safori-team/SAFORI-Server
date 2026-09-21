package com.safori.api.notification.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.notification.dto.DeviceTokenRegisterRequest;
import com.safori.api.notification.service.DeleteDeviceTokenUseCase;
import com.safori.api.notification.service.RegisterDeviceTokenUseCase;
import com.safori.common.annotation.UserCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[디바이스 토큰]", description = "FCM 푸시 알림용 디바이스 토큰 등록/삭제 API.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/users/device-tokens")
public class DeviceTokenApiController {

    private final RegisterDeviceTokenUseCase registerDeviceTokenUseCase;
    private final DeleteDeviceTokenUseCase deleteDeviceTokenUseCase;

    @Operation(summary = "디바이스 토큰 등록",
            description = "로그인 후 클라이언트의 FCM 토큰을 등록합니다. 동일 토큰이 이미 있으면 현재 사용자로 소유자를 갱신합니다(upsert). 등록된 deviceTokenId를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "등록 성공 — deviceTokenId 반환")
    @ApiResponse(responseCode = "401", description = "로그인 필요 (유효한 토큰 없음)")
    @PostMapping
    public ApiResponseDto<Long> register(
            @UserCode String username,
            @Valid @RequestBody DeviceTokenRegisterRequest request) {
        return ApiResponseDto.onSuccess(registerDeviceTokenUseCase.execute(username, request));
    }

    @Operation(summary = "디바이스 토큰 삭제",
            description = "로그아웃 시 FCM 토큰을 삭제합니다. 본인 소유 토큰만 삭제되며, 없어도 성공으로 응답합니다(멱등).")
    @ApiResponse(responseCode = "200", description = "삭제 성공")
    @ApiResponse(responseCode = "401", description = "로그인 필요 (유효한 토큰 없음)")
    @DeleteMapping
    public ApiResponseDto<String> delete(
            @UserCode String username,
            @RequestParam String token) {
        deleteDeviceTokenUseCase.execute(username, token);
        return ApiResponseDto.onSuccess("deleted");
    }
}
