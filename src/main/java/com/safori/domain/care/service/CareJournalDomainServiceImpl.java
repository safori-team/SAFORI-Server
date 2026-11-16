package com.safori.domain.care.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.care.entity.CareJournal;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.JournalOption;
import com.safori.domain.care.entity.JournalOptionGroup;
import com.safori.domain.care.model.JournalSelectionInput;
import com.safori.domain.care.repository.CareJournalRepository;
import com.safori.domain.care.repository.JournalOptionGroupRepository;
import com.safori.domain.care.repository.JournalOptionRepository;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.safori.domain.care.exception.CareHandler.JOURNAL_INVALID_SELECTION;

@Transactional
@DomainService
@RequiredArgsConstructor
public class CareJournalDomainServiceImpl implements CareJournalDomainService {

    private final CareJournalRepository journalRepository;
    private final JournalOptionRepository optionRepository;
    private final JournalOptionGroupRepository groupRepository;

    @Override
    public CareJournal write(CareRecipient recipient, OrganizationMember writer, LocalDateTime confirmedAt,
                             List<JournalSelectionInput> selections, String memo, boolean guardianVisible) {
        Organization.requireMembers(recipient.getOrganization(), writer);
        Map<String, JournalOption> options = optionRepository.findActiveWithGroup().stream()
                .collect(Collectors.toMap(JournalOption::getCode, Function.identity()));
        Map<JournalOption, String> chosen = validate(selections, options);

        CareJournal journal = CareJournal.write(recipient, writer, confirmedAt,
                StringUtils.hasText(memo) ? memo.trim() : null, guardianVisible);
        chosen.forEach(journal::select);
        return journalRepository.save(journal);
    }

    /**
     * 폼 규칙: 활성 항목만, 중복 없이 / 직접 입력 항목은 입력값 필수 / 하위 항목은 부모를 같이 골라야 함 /
     * 단일 선택 섹션은 정확히 1개, 필수 섹션은 1개 이상 / 단독 항목(특이사항 없음)은 그 섹션에서 혼자만.
     */
    private Map<JournalOption, String> validate(List<JournalSelectionInput> selections,
                                                Map<String, JournalOption> options) {
        Map<JournalOption, String> chosen = new HashMap<>();
        for (JournalSelectionInput input : selections) {
            JournalOption option = options.get(input.optionCode());
            if (option == null || chosen.containsKey(option)) {
                throw JOURNAL_INVALID_SELECTION;
            }
            boolean hasText = StringUtils.hasText(input.text());
            if (option.isTextInput() != hasText) {
                throw JOURNAL_INVALID_SELECTION;
            }
            chosen.put(option, hasText ? input.text().trim() : null);
        }
        for (JournalOption option : chosen.keySet()) {
            if (option.getParent() != null && !chosen.containsKey(option.getParent())) {
                throw JOURNAL_INVALID_SELECTION;
            }
        }
        Map<String, List<JournalOption>> byGroup = chosen.keySet().stream()
                .collect(Collectors.groupingBy(option -> option.getGroup().getCode()));
        for (JournalOptionGroup group : groupRepository.findAllByOrderBySortOrderAsc()) {
            List<JournalOption> inGroup = byGroup.getOrDefault(group.getCode(), List.of());
            long topLevel = inGroup.stream().filter(option -> option.getParent() == null).count();
            boolean singleViolated = group.getSelection() == JournalOptionGroup.Selection.SINGLE && topLevel > 1;
            boolean requiredViolated = group.isRequired() && topLevel == 0;
            boolean exclusiveViolated = inGroup.size() > 1 && inGroup.stream().anyMatch(JournalOption::isExclusive);
            if (singleViolated || requiredViolated || exclusiveViolated) {
                throw JOURNAL_INVALID_SELECTION;
            }
        }
        return chosen;
    }
}
