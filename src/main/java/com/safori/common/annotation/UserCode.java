package com.safori.common.annotation;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

/**
 * 인증된 사용자의 username을 Spring Security principal에서 주입한다.
 */
@Hidden
@AuthenticationPrincipal(expression = "username")
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserCode {
}
