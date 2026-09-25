package com.safori.api.user.dto;

import com.safori.common.consts.AccountStaticValues;
import com.safori.common.util.PhoneNumber;
import com.safori.domain.user.entity.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Schema(description = "회원가입 요청")
@Builder
@AllArgsConstructor
@Getter
public class UserRegisterRequest {

    @Schema(description = "사용자 이름 (실명)", example = "홍길동")
    @NotBlank
    private final String name;

    @Schema(description = "로그인 아이디 (6~12자, 영문·숫자만 허용)", example = "user01")
    @NotBlank
    @Size(min = 6, max = 12, message = "아이디는 6~12자로 입력해 주세요")
    @Pattern(regexp = AccountStaticValues.LOGIN_ID_PATTERN, message = "아이디는 영문, 숫자만 사용할 수 있습니다")
    private final String username;

    @Schema(description = "비밀번호 (8~20자, 영문·숫자 혼합 필수)", example = "myPass1234")
    @NotBlank
    @Size(min = 8, max = 20, message = "비밀번호는 8~20자로 입력해 주세요")
    @Pattern(regexp = AccountStaticValues.PASSWORD_PATTERN, message = "비밀번호는 영문, 숫자를 혼합하여 입력해 주세요")
    private final String password;

    @Schema(description = "성별 (MALE / FEMALE)", example = "MALE")
    @NotNull(message = "성별은 필수입니다")
    private final Gender gender;

    @Schema(description = "생년월일 (yyyy-MM-dd, 선택). 앱 회원가입 화면에 추가되면 필수로 바꾼다.", example = "1960-03-12")
    @Past(message = "생년월일은 오늘 이전 날짜여야 합니다")
    private final LocalDate birthDate;

    @Schema(description = "휴대폰 번호 (선택, 하이픈 있어도 됨). 앱 회원가입 화면에 추가되면 필수로 바꾼다.", example = "010-1234-5678")
    @Pattern(regexp = PhoneNumber.PATTERN, message = "휴대폰 번호 형식이 올바르지 않습니다")
    private final String phone;

    @Schema(description = "별명 (선택)", example = "길동이")
    private final String nickname;
}
