package com.safori.api.recipient.dto;

import com.safori.api.journal.dto.JournalSummary;
import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareReasonType;
import com.safori.domain.care.entity.CareRecord;
import com.safori.domain.care.entity.CareStatusCode;
import com.safori.domain.care.entity.GuardianRelation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "대상자 상세. 기록 상세 화면(현황 카드)과 대상자 정보 상세 화면이 같이 쓴다.")
public record RecipientDetailResponse(
        @Schema(description = "대상자 식별자 (public_id)") String careRecipientId,
        @Schema(description = "이름", example = "김영희") String name,
        @Schema(description = "생년월일. 없으면 null", example = "1960-03-12") LocalDate birthDate,
        @Schema(description = "연락처 (숫자만). 없으면 null", example = "01012345678") String phone,
        @Schema(description = "아이디 (어르신 앱)", example = "elder01") String loginId,
        @Schema(description = "이용 상태. true=활성화, false=비활성화(현황·목록에서 빠짐)", example = "true") boolean active,
        @Schema(description = "가입일 (어르신 앱 가입 일시)") LocalDateTime joinedAt,
        @Schema(description = "현재 담당자. 미배정이면 null") Manager manager,
        @Schema(description = "연결된 보호자 (연결 순). 없으면 빈 목록 — '연결된 보호자가 없습니다'") List<Guardian> guardians,
        @Schema(description = "확인 필요도(상태 코드). null이면 표시 없음(X)") CareStatusCode statusCode,
        @Schema(description = "처리 상태. 상태 코드가 없으면 null") CareProcessingStatus processingStatus,
        @Schema(description = "확인 사유. 상태 코드가 있을 때만 있다 (없으면 null)") Reason reason,
        @Schema(description = "최근 조치 기록 (일지, 확인 일시 최신순 최대 20건). 카드를 누르면 일지 상세") List<JournalSummary> recentActions
) {

    public record Manager(
            @Schema(description = "담당자 식별자 (계정 UUID)") String managerId,
            @Schema(description = "이름", example = "김철수") String name,
            @Schema(description = "직종", example = "사회복지사") String jobTitle,
            @Schema(description = "연락처 (숫자만)", example = "01012345678") String phone) {
    }

    public record Guardian(
            @Schema(description = "보호자 식별자 (계정 UUID)") String guardianId,
            @Schema(description = "이름", example = "김희영") String name,
            @Schema(description = "관계", example = "CHILD") GuardianRelation relation,
            @Schema(description = "화면 표시용 관계 (기타면 입력값)", example = "자녀") String relationLabel,
            @Schema(description = "연락처 (숫자만)", example = "01012345678") String phone) {
    }

    public record Reason(
            @Schema(description = "기록 식별자. 기록 상세·처리 상태 변경 경로의 {recordId} (처리 상태는 현재 기록만 바꿀 수 있다)") String recordId,
            @Schema(description = "사유 종류") CareReasonType reasonType,
            @Schema(description = "사유 제목", example = "동일 감정 반복") String title,
            @Schema(description = "사유 설명", example = "최근 일기 3건 중 2건에서 슬픔 계열 감정이 반복됐어요.") String message,
            @Schema(description = "안내 라벨", example = "확인 권장") String guidanceLabel,
            @Schema(description = "안내 문구", example = "다음 연락이나 방문 시 최근 기분에 달라진 점이 있는지 살펴봐 주세요.") String guidance,
            @Schema(description = "요청·감지 시각") LocalDateTime detectedAt) {

        /** 기록의 사유. 기록이 없으면 null. 기록의 사유·문구는 바뀌지 않으므로 지난 기록이면 그때의 사유다. */
        public static Reason of(CareRecord record) {
            return record == null ? null : new Reason(record.getPublicId(), record.getReasonType(),
                    record.getReasonType().title(), record.getReasonMessage(), record.getStatusCode().guidanceLabel(),
                    record.getReasonType().guidance(), record.getDetectedAt());
        }
    }
}
