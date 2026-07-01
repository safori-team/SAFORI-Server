package com.safori.domain.voice.exception;

import com.safori.common.exception.BaseErrorCode;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;

public class VoiceHandler extends GeneralException {

    public static final GeneralException NOT_FOUND =
            new VoiceHandler(ErrorStatus.VOICE_NOT_FOUND);
    public static final GeneralException NO_PERMISSION =
            new VoiceHandler(ErrorStatus.VOICE_NO_PERMISSION);
    public static final GeneralException ANALYSIS_NOT_COMPLETED =
            new VoiceHandler(ErrorStatus.VOICE_ANALYSIS_NOT_COMPLETED);
    public static final GeneralException ANALYSIS_RESULT_NOT_FOUND =
            new VoiceHandler(ErrorStatus.VOICE_ANALYSIS_RESULT_NOT_FOUND);
    public static final GeneralException ALREADY_EXISTS_TODAY =
            new VoiceHandler(ErrorStatus.VOICE_ALREADY_EXISTS_TODAY);

    public VoiceHandler(BaseErrorCode code) {
        super(code);
    }
}
