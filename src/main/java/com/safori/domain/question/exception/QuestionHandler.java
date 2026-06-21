package com.safori.domain.question.exception;

import com.safori.common.exception.BaseErrorCode;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;

public class QuestionHandler extends GeneralException {

    public static final GeneralException NOT_FOUND =
            new QuestionHandler(ErrorStatus.QUESTION_NOT_FOUND);

    public QuestionHandler(BaseErrorCode code) {
        super(code);
    }
}
