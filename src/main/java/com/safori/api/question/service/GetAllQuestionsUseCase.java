package com.safori.api.question.service;

import com.safori.common.annotation.UseCase;
import com.safori.common.consts.UserServiceQuestionStaticValues;
import com.safori.domain.question.entity.QuestionCategory;
import com.safori.api.question.dto.QuestionResponse;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@UseCase
@RequiredArgsConstructor
public class GetAllQuestionsUseCase {

    public List<QuestionResponse> execute() {
        List<QuestionResponse> responses = new ArrayList<>();
        long sequence = 1L;

        for (QuestionCategory category : QuestionCategory.values()) {
            List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP
                    .getOrDefault(category.name(), List.of());

            for (int i = 0; i < questions.size(); i++) {
                responses.add(QuestionResponse.builder()
                        .id(sequence++)
                        .questionCategory(category)
                        .questionIndex(i)
                        .content(questions.get(i))
                        .build());
            }
        }

        return responses;
    }
}
