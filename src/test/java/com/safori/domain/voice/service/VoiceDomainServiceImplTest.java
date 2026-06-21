package com.safori.domain.voice.service;

import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.repository.VoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class VoiceDomainServiceImplTest {

    @Mock VoiceRepository voiceRepository;
    @Mock User user;

    @Test
    @DisplayName("uploadVoiceFile - 메타데이터를 매핑해 Voice를 저장한다")
    void uploadVoiceFile_savesMappedVoice() {
        // given
        VoiceDomainServiceImpl service = new VoiceDomainServiceImpl(voiceRepository);
        given(voiceRepository.save(any(Voice.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Voice result = service.uploadVoiceFile(user, "voices/u/uuid.m4a", "제목",
                37, 44100, 128000);

        // then
        ArgumentCaptor<Voice> captor = ArgumentCaptor.forClass(Voice.class);
        org.mockito.Mockito.verify(voiceRepository).save(captor.capture());
        Voice saved = captor.getValue();

        assertThat(saved.getVoiceKey()).isEqualTo("voices/u/uuid.m4a");
        assertThat(saved.getVoiceTitle()).isEqualTo("제목");
        assertThat(saved.getDuration()).isEqualTo(37);
        assertThat(saved.getSampleRate()).isEqualTo(44100);
        assertThat(saved.getBitRate()).isEqualTo(128000);
        assertThat(saved.getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.PENDING);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(result).isSameAs(saved);
    }
}
