package com.safori.domain.voice.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.repository.VoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.safori.domain.voice.exception.VoiceHandler.NOT_FOUND;

@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VoiceAdaptorImpl implements VoiceAdaptor {

    private final VoiceRepository voiceRepository;

    @Override
    public Voice queryById(Long voiceId) {
        return voiceRepository.findById(voiceId)
                .orElseThrow(() -> NOT_FOUND);
    }

    @Override
    public List<Voice> queryByUsername(String username) {
        return voiceRepository.findByUser_Username(username);
    }

    @Override
    public List<Voice> queryByUsernameAndCreatedAt(String username, LocalDate createdAt) {
        LocalDateTime start = createdAt.atStartOfDay();
        LocalDateTime end = createdAt.plusDays(1).atStartOfDay();
        return voiceRepository.findByUser_UsernameAndCreatedDateBetween(username, start, end);
    }

    @Override
    public List<Voice> queryLatestByUsername(String username, int limit) {
        if (limit <= 0) return List.of();
        return voiceRepository.findByUser_UsernameOrderByCreatedDateDesc(username, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public Voice save(Voice voice) {
        return voiceRepository.save(voice);
    }

    @Override
    @Transactional
    public void deleteById(Long voiceId) {
        voiceRepository.deleteById(voiceId);
    }
}
