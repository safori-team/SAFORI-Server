package com.safori.api.operator.dto;

import com.safori.api.recipient.dto.CareRecordResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "대상자 상태 코드 변경(테스트용) 결과")
public record ChangeRecipientStatusResponse(
        @Schema(description = "상태가 바뀌는(바뀐) 시각") LocalDateTime appliesAt,
        @Schema(description = "나중에 바뀌도록 예약했으면 true, 바로 바꿨으면 false", example = "false") boolean scheduled,
        @Schema(description = "바로 바꿨을 때의 새 현재 기록. 예약했거나 표시 없음으로 바꿨으면 null") CareRecordResponse record
) {
}
