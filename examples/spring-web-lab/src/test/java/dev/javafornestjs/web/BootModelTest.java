package dev.javafornestjs.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javafornestjs.web.feature.FeatureAutoConfiguration;
import dev.javafornestjs.web.feature.FeatureGreeting;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BootModelTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeatureAutoConfiguration.class));

    @Test
    void conditionCreatesDefaultBeanAndPropertyCanDisableIt() {
        runner.run(context -> assertEquals("auto", context.getBean(FeatureGreeting.class).message()));
        runner.withPropertyValues("lab.feature.enabled=false").run(context ->
                assertTrue(context.getBeansOfType(FeatureGreeting.class).isEmpty()));
    }

    @Test
    void userBeanMakesMissingBeanConditionBackOff() {
        runner.withBean(FeatureGreeting.class, () -> new FeatureGreeting("custom"))
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(FeatureGreeting.class).size());
                    assertEquals("custom", context.getBean(FeatureGreeting.class).message());
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApiProperties.class)
    static class PropertiesOnly { }

    @Test
    void invalidConfigurationFailsForTheExpectedBindingReason() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class)
                .withPropertyValues("lab.label= ").run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    boolean found = false;
                    while (failure != null) {
                        if (failure instanceof BindValidationException binding) {
                            found = binding.getValidationErrors().getAllErrors().stream()
                                    .anyMatch(error -> error.getCode().equals("NotBlank"));
                        }
                        failure = failure.getCause();
                    }
                    assertTrue(found, "Expected real NotBlank binding failure");
                });
    }

    @Test
    void bootProfileAndExplicitCloseHaveARealLifecycle() {
        LifecycleConfiguration.Events events;
        try (var context = new SpringApplicationBuilder(ApiApplication.class).web(WebApplicationType.NONE)
                .profiles("test").properties("spring.main.banner-mode=off", "logging.level.root=ERROR").run()) {
            assertEquals("test-profile", context.getBean(ApiProperties.class).label());
            assertEquals("auto", context.getBean(FeatureGreeting.class).message());
            events = context.getBean(LifecycleConfiguration.Events.class);
            assertEquals(List.of("construct", "init", "ready"), events.values());
        }
        assertEquals(List.of("construct", "init", "ready", "destroy"), events.values());
    }
}
