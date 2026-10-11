package com.safori.domain.access.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessCatalogSynchronizerTest {

    @Mock AccessProvisioningDomainService provisioningService;
    @InjectMocks AccessCatalogSynchronizer synchronizer;

    @Test
    @DisplayName("기동 시 카탈로그를 동기화한다")
    void synchronizesOnStartup() {
        synchronizer.run(null);

        verify(provisioningService).synchronizeCatalog();
    }

    @Test
    @DisplayName("다른 컨테이너와 겹쳐 유니크 제약에 걸리면 한 번 다시 돌린다")
    void retriesOnceWhenAnotherContainerWins() {
        given(provisioningService.synchronizeCatalog())
                .willThrow(new DataIntegrityViolationException("uq_ap_code"))
                .willReturn(null);

        synchronizer.run(null);

        verify(provisioningService, times(2)).synchronizeCatalog();
    }

    @Test
    @DisplayName("다시 돌려도 실패하면 기동을 멈춘다 — 카탈로그 없이 뜨지 않는다")
    void failsStartupWhenRetryFails() {
        given(provisioningService.synchronizeCatalog())
                .willThrow(new DataIntegrityViolationException("uq_ap_code"));

        assertThatThrownBy(() -> synchronizer.run(null)).isInstanceOf(DataIntegrityViolationException.class);
        verify(provisioningService, times(2)).synchronizeCatalog();
    }
}
