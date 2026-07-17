package com.safori.api.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "FCM 디바이스 토큰 등록 요청")
@Builder
@AllArgsConstructor
@Getter
public class DeviceTokenRegisterRequest {

    @Schema(description = "FCM registration token", example = "dGhpc0lzQW5GY21Ub2tlbg...")
    @NotBlank
    @Size(max = 512, message = "토큰은 512자를 초과할 수 없습니다")
    private final String token;
}
