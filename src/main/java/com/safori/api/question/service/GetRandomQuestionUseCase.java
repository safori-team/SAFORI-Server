package com.safori.api.question.service;

import com.safori.common.annotation.UseCase;
import com.safori.api.question.dto.QuestionResponse;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@UseCase
@RequiredArgsConstructor
public class GetRandomQuestionUseCase {

    private final GetAllQuestionsUseCase getAllQuestionsUseCase;

    public QuestionResponse execute() {
        List<QuestionResponse> questions = getAllQuestionsUseCase.execute();
        if (questions.isEmpty()) {
            return null;
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(questions.size());
        return questions.get(randomIndex);
    }
}
