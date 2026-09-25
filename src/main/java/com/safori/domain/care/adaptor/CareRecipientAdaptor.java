package com.safori.domain.care.adaptor;

import com.safori.domain.access.policy.RecipientAccessScope;
import com.safori.domain.care.entity.CareRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CareRecipientAdaptor {

    Optional<CareRecipient> findById(Long recipientId);

    /** URL 경로 변수처럼 외부 식별자({@code public_id})로 들어온 어르신. */
    Optional<CareRecipient> findByPublicId(String publicId);

    /**
     * 권한 판정이 준 범위({@code BackofficeAccessPolicy#recipientScope})로 제한한 어르신 목록.
     * 전체를 읽은 뒤 거르지 않고 범위를 쿼리 조건으로 건다. 범위가 비어 있으면 쿼리 없이 빈 페이지.
     */
    Page<CareRecipient> queryAccessible(RecipientAccessScope scope, Pageable pageable);
}
