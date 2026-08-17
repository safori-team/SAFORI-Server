package com.safori.domain.chatbot.exception;

import com.safori.common.exception.BaseErrorCode;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;

public class ChatbotHandler extends GeneralException {

    public static final GeneralException SESSION_NOT_FOUND =
            new ChatbotHandler(ErrorStatus.CHAT_SESSION_NOT_FOUND);
    public static final GeneralException SESSION_NO_PERMISSION =
            new ChatbotHandler(ErrorStatus.CHAT_SESSION_NO_PERMISSION);
    public static final GeneralException MESSAGE_NOT_FOUND =
            new ChatbotHandler(ErrorStatus.CHAT_MESSAGE_NOT_FOUND);
    public static final GeneralException MESSAGE_NO_PERMISSION =
            new ChatbotHandler(ErrorStatus.CHAT_MESSAGE_NO_PERMISSION);
    public static final GeneralException FEEDBACK_INVALID_EMOTION =
            new ChatbotHandler(ErrorStatus.CHAT_FEEDBACK_INVALID_EMOTION);
    public static final GeneralException VOICE_STT_FAILED =
            new ChatbotHandler(ErrorStatus.CHAT_VOICE_STT_FAILED);
    public static final GeneralException SESSION_CLOSED =
            new ChatbotHandler(ErrorStatus.CHAT_SESSION_CLOSED);
    public static final GeneralException OFFER_NOT_FOUND =
            new ChatbotHandler(ErrorStatus.CHAT_OFFER_NOT_FOUND);
    public static final GeneralException OFFER_NO_PERMISSION =
            new ChatbotHandler(ErrorStatus.CHAT_OFFER_NO_PERMISSION);
    public static final GeneralException OFFER_NOT_OFFERABLE =
            new ChatbotHandler(ErrorStatus.CHAT_OFFER_NOT_OFFERABLE);
    public static final GeneralException OFFER_EXPIRED =
            new ChatbotHandler(ErrorStatus.CHAT_OFFER_EXPIRED);
    public static final GeneralException REPLY_IN_PROGRESS =
            new ChatbotHandler(ErrorStatus.CHAT_REPLY_IN_PROGRESS);
    public static final GeneralException SESSION_CRISIS_CLOSED =
            new ChatbotHandler(ErrorStatus.CHAT_SESSION_CRISIS_CLOSED);

    public ChatbotHandler(BaseErrorCode code) {
        super(code);
    }
}
