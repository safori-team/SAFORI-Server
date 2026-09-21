package com.safori.domain.chatbot.entity;

import java.util.Locale;

public enum DoranEmotion {
    HAPPY, SAD, NEUTRAL, ANGRY, ANXIETY, SURPRISE;

    public String getCode() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DoranEmotion fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("emotion code is null or blank");
        }
        return DoranEmotion.valueOf(code.trim().toUpperCase(Locale.ROOT));
    }
}
