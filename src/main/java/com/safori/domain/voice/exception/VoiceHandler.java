package com.safori.domain.voice.exception;

import com.safori.common.exception.BaseErrorCode;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;

public class VoiceHandler extends GeneralException {

    public static final GeneralException NOT_FOUND =
            new VoiceHandler(ErrorStatus.VOICE_NOT_FOUND);
    public static final GeneralException NO_PERMISSION =
            new VoiceHandler(ErrorStatus.VOICE_NO_PERMISSION);

    public VoiceHandler(BaseErrorCode code) {
        super(code);
    }
}
