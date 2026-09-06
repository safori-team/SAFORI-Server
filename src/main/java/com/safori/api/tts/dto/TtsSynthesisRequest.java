package com.safori.api.tts.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "TTS 합성 요청")
public record TtsSynthesisRequest(
        @Schema(description = "합성할 평문. 1~500자, 개행 허용, SSML 불가", example = "오늘 하루는 어땠나요?")
        String text,

        @Schema(description = """
                공용 캐시 대상 여부. 생략 시 true.

                true  - 전 사용자가 공유하는 고정 문구(질문 목록, CBT 왜곡 설명, 버튼 라벨 등).
                        tts/shared/ 에 장기 보관되어 최초 1회만 GCP를 호출한다.
                false - 사용자마다 달라지는 일회성 문구(마음일기 본문, 도란이 응답 등).
                        tts/ephemeral/ 에 저장되어 짧은 수명주기로 만료된다.

                동적 문구에 true를 보내면 재사용되지 않을 오디오가 장기 보관되고,
                고정 문구에 false를 보내면 캐시 효과가 사라져 매번 GCP를 호출한다.
                """, example = "true", defaultValue = "true")
        Boolean cacheable
) {

    /**
     * 미지정(null) 시 공용 캐시로 간주한다.
     * 고정 문구에 캐시가 걸리지 않는 쪽이 비용 영향이 크고 조용히 새기 때문에 true를 기본값으로 둔다.
     */
    public boolean isCacheable() {
        return cacheable == null || cacheable;
    }
}
