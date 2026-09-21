package com.safori.common.exception;

public interface BaseErrorCode extends BaseCode {
    String getExplainError() throws NoSuchFieldException;
}
