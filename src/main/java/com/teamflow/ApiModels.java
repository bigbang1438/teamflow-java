package com.teamflow;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ApiModels {
    private ApiModels() {}

    public record Task(long id, String title, String description, String assignee,
                       String priority, String status, long version, LocalDate dueDate,
                       String createdAt, String updatedAt) {}
    public record TaskInput(
        @NotBlank(message = "标题不能为空") @Size(max = 120, message = "标题最多 120 字") String title,
        @Size(max = 2000, message = "描述最多 2000 字") String description,
        @Size(max = 60, message = "负责人最多 60 字") String assignee,
        @NotBlank(message = "请选择优先级") @Pattern(regexp = "low|medium|high", message = "优先级不合法") String priority,
        LocalDate dueDate,
        @PositiveOrZero(message = "版本号不能为负数") Long version) {}
    public record StatusInput(
        @NotBlank @Pattern(regexp = "backlog|doing|blocked|done", message = "状态不合法") String status,
        @NotNull(message = "请提供版本号") @PositiveOrZero Long version) {}
    public record Activity(long id, long taskId, String action, String detail, String createdAt) {}
    public record Meta(int page, int size, long total, long totalPages) {}
    public record TaskPage(List<Task> data, Meta meta) {}
    public record Summary(long total, Map<String, Long> byStatus, long overdue, double completionRate) {}
}
