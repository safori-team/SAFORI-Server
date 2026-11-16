package com.safori.domain.care.service;

import com.safori.domain.care.entity.JournalOption;
import com.safori.domain.care.entity.JournalOptionGroup;
import com.safori.domain.care.entity.JournalOptionGroup.Selection;
import com.safori.domain.care.repository.JournalOptionGroupRepository;
import com.safori.domain.care.repository.JournalOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 기동 시 기본 일지 폼(섹션·항목)을 넣는다. 없는 코드만 넣고 이미 있는 행은 건드리지 않는다
 * (운영 중 바꾼 문구·비활성화를 덮어쓰지 않는다). 컨테이너 여러 개가 동시에 넣다 겹치면 그 행은 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JournalFormSeeder implements ApplicationRunner {

    public static final String METHOD = "METHOD";
    public static final String RESULT = "RESULT";
    public static final String CONDITION = "CONDITION";
    public static final String ACTION = "ACTION";
    public static final String FOLLOW_UP = "FOLLOW_UP";

    private record GroupSeed(String code, String label, Selection selection, boolean required) {
    }

    private record OptionSeed(String code, String group, String parent, String label, boolean exclusive,
                              boolean textInput) {
    }

    private static final List<GroupSeed> GROUPS = List.of(
            new GroupSeed(METHOD, "확인 방식", Selection.SINGLE, true),
            new GroupSeed(RESULT, "확인 결과", Selection.SINGLE, true),
            new GroupSeed(CONDITION, "대상자 상태", Selection.MULTI, true),
            new GroupSeed(ACTION, "수행한 조치", Selection.MULTI, false),
            new GroupSeed(FOLLOW_UP, "필요한 후속 조치", Selection.MULTI, false));

    /** 부모가 먼저 오도록 나열한다. */
    private static final List<OptionSeed> OPTIONS = List.of(
            option("VISIT", METHOD, "방문"),
            option("PHONE", METHOD, "전화"),
            option("GUARDIAN_CONTACT", METHOD, "보호자 연락"),
            option("CONTACTED", RESULT, "연락됨"),
            option("NOT_CONTACTED", RESULT, "연락 안 됨"),
            option("ABSENT_ON_VISIT", RESULT, "방문 시 부재"),
            new OptionSeed("NO_ISSUE", CONDITION, null, "특이사항 없음", true, false),
            option("EMOTION_CHANGE", CONDITION, "정서 변화"),
            child("LONELINESS", CONDITION, "EMOTION_CHANGE", "외로움 표현"),
            child("ANXIETY_EXPRESSION", CONDITION, "EMOTION_CHANGE", "불안 표현"),
            child("DEPRESSION_EXPRESSION", CONDITION, "EMOTION_CHANGE", "우울·무기력 표현"),
            child("ANGER_EXPRESSION", CONDITION, "EMOTION_CHANGE", "분노·짜증 표현"),
            child("CONVERSATION_REFUSAL", CONDITION, "EMOTION_CHANGE", "대화 거부"),
            new OptionSeed("EMOTION_OTHER", CONDITION, "EMOTION_CHANGE", "기타", false, true),
            option("HEALTH_COMPLAINT", CONDITION, "건강 이상 호소"),
            option("EATING_CHANGE", CONDITION, "식생활 변화"),
            option("SLEEP_CHANGE", CONDITION, "수면 상태 변화"),
            option("OUTING_DECREASE", CONDITION, "외출 활동 감소"),
            new OptionSeed("CONDITION_OTHER", CONDITION, null, "기타", false, true),
            option("WELLBEING_CHECKED", ACTION, "안부 확인 완료"),
            option("ORG_REPORTED", ACTION, "기관 보고 완료"),
            option("GUARDIAN_CONTACTED", ACTION, "보호자 연락 완료"),
            option("GUARDIAN_CONTACT_NEEDED", FOLLOW_UP, "보호자 연락 필요"),
            option("HOSPITAL_VISIT_NEEDED", FOLLOW_UP, "병원 진료 필요"),
            option("EMERGENCY_NEEDED", FOLLOW_UP, "긴급 대응 필요"),
            new OptionSeed("FOLLOW_UP_OTHER", FOLLOW_UP, null, "기타", false, true));

    private final JournalOptionGroupRepository groupRepository;
    private final JournalOptionRepository optionRepository;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, JournalOptionGroup> groups = new HashMap<>();
        for (int i = 0; i < GROUPS.size(); i++) {
            GroupSeed seed = GROUPS.get(i);
            int order = i;
            groups.put(seed.code(), groupRepository.findById(seed.code()).orElseGet(() -> insert(() ->
                    groupRepository.save(new JournalOptionGroup(seed.code(), seed.label(), seed.selection(),
                            seed.required(), order)), () -> groupRepository.findById(seed.code()).orElseThrow())));
        }
        Map<String, JournalOption> options = new HashMap<>();
        for (int i = 0; i < OPTIONS.size(); i++) {
            OptionSeed seed = OPTIONS.get(i);
            int order = i;
            options.put(seed.code(), optionRepository.findById(seed.code()).orElseGet(() -> insert(() ->
                    optionRepository.save(new JournalOption(seed.code(), groups.get(seed.group()),
                            seed.parent() == null ? null : options.get(seed.parent()), seed.label(),
                            seed.exclusive(), seed.textInput(), order, true)),
                    () -> optionRepository.findById(seed.code()).orElseThrow())));
        }
    }

    private static <T> T insert(java.util.function.Supplier<T> save, java.util.function.Supplier<T> reload) {
        try {
            return save.get();
        } catch (DataIntegrityViolationException e) {
            log.info("일지 폼 기본 데이터를 다른 컨테이너가 먼저 넣었다 — 건너뜀");
            return reload.get();
        }
    }

    private static OptionSeed option(String code, String group, String label) {
        return new OptionSeed(code, group, null, label, false, false);
    }

    private static OptionSeed child(String code, String group, String parent, String label) {
        return new OptionSeed(code, group, parent, label, false, false);
    }
}
