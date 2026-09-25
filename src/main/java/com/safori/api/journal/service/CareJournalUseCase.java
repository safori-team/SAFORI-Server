package com.safori.api.journal.service;

import com.safori.api.journal.dto.JournalDetailResponse;
import com.safori.api.journal.dto.JournalFormResponse;
import com.safori.api.journal.dto.JournalSummary;
import com.safori.api.journal.dto.WriteJournalRequest;
import com.safori.api.recipient.service.OrganizationRecipients;
import com.safori.api.worker.service.OrganizationWorkers;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.care.entity.CareJournal;
import com.safori.domain.care.entity.CareJournalSelection;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.JournalOption;
import com.safori.domain.care.exception.CareHandler;
import com.safori.domain.care.model.JournalSelectionInput;
import com.safori.domain.care.repository.CareJournalRepository;
import com.safori.domain.care.repository.CareJournalSelectionRepository;
import com.safori.domain.care.repository.JournalOptionGroupRepository;
import com.safori.domain.care.repository.JournalOptionRepository;
import com.safori.domain.care.service.CareJournalDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.safori.domain.care.service.JournalFormSeeder.ACTION;
import static com.safori.domain.care.service.JournalFormSeeder.FOLLOW_UP;
import static com.safori.domain.care.service.JournalFormSeeder.METHOD;
import static com.safori.domain.care.service.JournalFormSeeder.RESULT;

/**
 * 일지 폼·등록·상세, 그리고 대상자 상세의 최근 조치 기록.
 */
@UseCase
@RequiredArgsConstructor
public class CareJournalUseCase {

    /** 대상자 상세에 보이는 최근 조치 기록 수. */
    static final int RECENT_LIMIT = 20;

    private final OrganizationRecipients organizationRecipients;
    private final OrganizationWorkers organizationWorkers;
    private final CareJournalDomainService journalDomainService;
    private final CareJournalRepository journalRepository;
    private final CareJournalSelectionRepository selectionRepository;
    private final JournalOptionGroupRepository groupRepository;
    private final JournalOptionRepository optionRepository;
    private final UserAdaptor userAdaptor;

    @Transactional(readOnly = true)
    public JournalFormResponse form() {
        List<JournalOption> options = optionRepository.findActiveWithGroup();
        return new JournalFormResponse(groupRepository.findAllByOrderBySortOrderAsc().stream()
                .map(group -> new JournalFormResponse.Group(group.getCode(), group.getLabel(), group.getSelection(),
                        group.isRequired(), formOptions(options.stream()
                                .filter(o -> o.getGroup().getCode().equals(group.getCode())).toList(), null)))
                .toList());
    }

    @Transactional
    public JournalDetailResponse write(BackofficeActor actor, String careRecipientId, WriteJournalRequest request) {
        CareRecipient recipient = organizationRecipients.get(actor, careRecipientId);
        CareJournal journal = journalDomainService.write(recipient, organizationWorkers.actorOf(actor),
                request.confirmedAt(),
                request.selections().stream().map(s -> new JournalSelectionInput(s.optionCode(), s.text())).toList(),
                request.memo(), request.guardianVisible());
        return detail(recipient, journal, journal.getSelections());
    }

    @Transactional(readOnly = true)
    public JournalDetailResponse get(BackofficeActor actor, String careRecipientId, String journalId) {
        CareRecipient recipient = organizationRecipients.get(actor, careRecipientId);
        CareJournal journal = journalRepository.findByRecipientAndPublicId(recipient, journalId)
                .orElseThrow(() -> CareHandler.JOURNAL_NOT_FOUND);
        return detail(recipient, journal, selectionRepository.findByJournals(List.of(journal)));
    }

    /** 대상자 상세의 최근 조치 기록(확인 일시 최신순). 선택 항목은 한 번에 읽는다. */
    @Transactional(readOnly = true)
    public List<JournalSummary> recent(CareRecipient recipient) {
        List<CareJournal> journals = journalRepository.findByRecipientOrderByConfirmedAtDescIdDesc(
                recipient, PageRequest.of(0, RECENT_LIMIT));
        if (journals.isEmpty()) {
            return List.of();
        }
        Map<Long, List<CareJournalSelection>> selections = selectionRepository.findByJournals(journals).stream()
                .collect(Collectors.groupingBy(s -> s.getJournal().getId()));
        return journals.stream().map(journal -> {
            List<CareJournalSelection> chosen = selections.getOrDefault(journal.getId(), List.of());
            return new JournalSummary(journal.getPublicId(), journal.getConfirmedAt(),
                    journal.getWriter().getAccount().getName(), first(chosen, METHOD), first(chosen, RESULT),
                    labels(chosen, ACTION), labels(chosen, FOLLOW_UP));
        }).toList();
    }

    private JournalDetailResponse detail(CareRecipient recipient, CareJournal journal,
                                         List<CareJournalSelection> selections) {
        String recipientName = recipient.getUserId() == null ? null
                : userAdaptor.queryUserById(recipient.getUserId()).getName();
        Map<String, List<CareJournalSelection>> byGroup = selections.stream()
                .collect(Collectors.groupingBy(s -> s.getOption().getGroup().getCode()));
        List<JournalDetailResponse.Section> sections = groupRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(group -> byGroup.containsKey(group.getCode()))
                .map(group -> new JournalDetailResponse.Section(group.getCode(), group.getLabel(),
                        items(byGroup.get(group.getCode()), null)))
                .toList();
        return new JournalDetailResponse(journal.getPublicId(), recipient.getPublicId(), recipientName,
                journal.getWriter().getAccount().getName(), journal.getStatusCodeSnapshot(),
                journal.getProcessingStatusSnapshot(), journal.getConfirmedAt(), journal.getCreatedDate(),
                journal.isGuardianVisible(), journal.getMemo(), sections);
    }

    /** 고른 항목을 부모-자식 트리로. 부모 코드가 {@code parentCode}인 항목들이 한 단계다. */
    private static List<JournalDetailResponse.Item> items(List<CareJournalSelection> selections, String parentCode) {
        return selections.stream()
                .filter(s -> Objects.equals(parentCodeOf(s.getOption()), parentCode))
                .map(s -> new JournalDetailResponse.Item(s.getOption().getCode(), s.getLabelSnapshot(), s.getText(),
                        items(selections, s.getOption().getCode())))
                .toList();
    }

    private static List<JournalFormResponse.Option> formOptions(List<JournalOption> options, String parentCode) {
        return options.stream()
                .filter(o -> Objects.equals(parentCodeOf(o), parentCode))
                .map(o -> new JournalFormResponse.Option(o.getCode(), o.getLabel(), o.isExclusive(), o.isTextInput(),
                        formOptions(options, o.getCode())))
                .toList();
    }

    private static String parentCodeOf(JournalOption option) {
        return option.getParent() == null ? null : option.getParent().getCode();
    }

    private static String first(List<CareJournalSelection> selections, String groupCode) {
        List<String> labels = labels(selections, groupCode);
        return labels.isEmpty() ? null : labels.get(0);
    }

    private static List<String> labels(List<CareJournalSelection> selections, String groupCode) {
        return selections.stream()
                .filter(s -> s.getOption().getGroup().getCode().equals(groupCode) && s.getOption().getParent() == null)
                .map(CareJournalSelection::getLabelSnapshot)
                .toList();
    }
}
