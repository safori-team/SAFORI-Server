package com.safori.api.tts.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.tts.dto.TtsSynthesisRequest;
import com.safori.api.tts.dto.TtsSynthesisResponse;
import com.safori.api.tts.service.SynthesizeSpeechUseCase;
import com.safori.common.annotation.UserCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[TTS]",
     description = """
             화면 낭독용 음성 합성 API. GCP Cloud Text-to-Speech(Chirp3-HD)로 합성한 MP3의
             presigned GET URL을 반환한다.

             합성 결과는 S3에 캐시되어 전 사용자가 공유하므로, 고정 문구(질문 목록·CBT 왜곡 설명 등)는
             최초 1회만 GCP를 호출한다. 고정 문구인지 일회성 문구인지는 서버가 알 수 없으므로
             요청의 cacheable로 구분한다.

             4252(음성 합성 실패)는 치명적 오류가 아니다. 클라이언트는 기기 내장 TTS로 폴백해
             낭독을 계속하면 된다.
             """)
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/tts")
public class TtsApiController {

    private final SynthesizeSpeechUseCase synthesizeSpeechUseCase;

    @Operation(summary = "텍스트 음성 합성",
            description = """
                    평문을 MP3로 합성하고 재생용 presigned GET URL(유효시간 1시간)을 반환한다.
                    text는 1~500자의 평문이며 SSML은 지원하지 않는다.

                    cacheable(생략 시 true)로 보관 정책이 갈린다.
                      true  - 전 사용자 공유 고정 문구. 장기 보관되어 최초 1회만 GCP를 호출
                      false - 마음일기 본문 등 일회성 문구. 짧은 수명주기로 만료

                    에러: 4250(빈 문장), 4251(500자 초과), 4252(합성 실패·타임아웃)
                    """)
    @ApiResponse(responseCode = "200", description = "합성 성공 또는 캐시 히트")
    @PostMapping
    public ApiResponseDto<TtsSynthesisResponse> synthesize(
            @UserCode String username,
            @RequestBody TtsSynthesisRequest request
    ) {
        return ApiResponseDto.onSuccess(
                synthesizeSpeechUseCase.execute(username, request.text(), request.isCacheable()));
    }
}
