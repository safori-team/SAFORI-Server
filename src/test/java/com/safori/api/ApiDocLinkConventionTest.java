package com.safori.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API 문서 링크(`#tag/{tag}/operation/{operationId}`)는 Figma 화면 주석에 붙는다. 링크가 깨지지 않도록
 * 태그 이름은 영문 slug(표시명은 x-displayName), operationId는 직접 지정하고 전체에서 유일해야 한다.
 */
class ApiDocLinkConventionTest {

    @Test
    @DisplayName("모든 API는 영문 slug 태그 + x-displayName, 직접 지정한 유일한 operationId를 갖는다")
    void apiDocLinksAreStable() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> violations = new ArrayList<>();
        Set<String> operationIds = new HashSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.safori.api")) {
            Class<?> controller = ClassUtils.forName(candidate.getBeanClassName(), getClass().getClassLoader());
            Tag tag = controller.getAnnotation(Tag.class);
            if (tag == null || !tag.name().matches("[a-z0-9-]+")) {
                violations.add(controller.getSimpleName() + ": @Tag name은 영문 소문자 slug여야 한다");
            } else if (Arrays.stream(tag.extensions()).map(Extension::properties).flatMap(Arrays::stream)
                    .noneMatch(p -> p.name().equals("x-displayName"))) {
                violations.add(controller.getSimpleName() + ": @Tag에 x-displayName(한글 표시명)이 없다");
            }
            for (Method method : controller.getDeclaredMethods()) {
                Operation operation = method.getAnnotation(Operation.class);
                if (operation == null) {
                    continue;
                }
                if (operation.operationId().isBlank()) {
                    violations.add(controller.getSimpleName() + "." + method.getName() + ": operationId 미지정");
                } else if (!operationIds.add(operation.operationId())) {
                    violations.add(controller.getSimpleName() + "." + method.getName()
                            + ": operationId 중복 " + operation.operationId());
                }
            }
        }

        assertThat(operationIds).isNotEmpty();
        assertThat(violations).isEmpty();
    }
}
