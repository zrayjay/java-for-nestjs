package dev.javafornestjs.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import java.math.BigDecimal;

public final class Tasks {
    public static final String TITLE = "[A-Za-z][A-Za-z0-9 _-]{0,79}";
    private Tasks() { }

    public record TaskItem(int id, String title, int priority) {
        public TaskItem {
            if (id < 1 || title == null || !title.matches(TITLE) || priority < 1 || priority > 5) {
                throw new IllegalArgumentException("Invalid task");
            }
        }
    }

    public record PriorityRequest(
            @Schema(type = "integer", format = "int32", minimum = "1", maximum = "5")
            @NotNull @DecimalMin("1") @DecimalMax("5") BigDecimal priority) {
        @AssertTrue
        @JsonIgnore
        public boolean isIntegral() {
            return priority == null || priority.stripTrailingZeros().scale() <= 0;
        }
        int asInt() { return priority.intValueExact(); }
    }

    public record TaskWriteRequest(@NotBlank @Size(max = 80) @Pattern(regexp = TITLE) String title,
                                   @NotNull @Valid PriorityRequest details) { }
    public record TaskDetails(int priority) { }
    public record TaskView(int id, String title, TaskDetails details) {
        static TaskView from(TaskItem item) {
            return new TaskView(item.id(), item.title(), new TaskDetails(item.priority()));
        }
    }
    public interface Draft extends Default { }
    public interface Publish extends Default { }
    public record ValidationProbe(@NotNull @Size(max = 80)
                                  @Pattern(regexp = TITLE, groups = Publish.class) String title) { }
}
