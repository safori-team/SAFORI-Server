package com.safori.domain.chatbot.model;

/** 이전 대화 한 턴. 프롬프트 히스토리 구성에 사용한다. */
public record HistoryTurn(String userInput, String botMessage) {}
