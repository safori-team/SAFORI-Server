package com.safori.domain.chatbot.service;

import com.safori.domain.chatbot.model.CrisisAssessment;

/** 키워드만으로 판단할 수 없는 발화의 문맥을 분류하는 포트. */
public interface CrisisClassifier {
    CrisisAssessment classify(String userInput);
}
