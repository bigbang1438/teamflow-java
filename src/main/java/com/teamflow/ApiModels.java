package com.teamflow;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ApiModels {
    private ApiModels() {}

    public record Task(long id, long projectId, String title, String description, String assignee,
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
    public record Activity(long id, long taskId, String actor, String action, String detail, String createdAt) {}
    public record Project(long id, String name, String role, String createdAt) {}
    public record ProjectInput(@NotBlank @Size(max = 80) String name) {}
    public record Member(long id, String username, String displayName, String role) {}
    public record MemberInput(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,40}") String username,
                              @NotBlank @Pattern(regexp = "editor|viewer") String role) {}
    public record RoleInput(@NotBlank @Pattern(regexp = "editor|viewer") String role) {}
    public record Comment(long id, String actor, String body, String createdAt) {}
    public record CommentInput(@NotBlank @Size(max = 1000) String body) {}
    public record ProjectActivity(long id, Long taskId, String actor, String action, String detail, String createdAt) {}
    public record Meta(int page, int size, long total, long totalPages) {}
    public record TaskPage(List<Task> data, Meta meta) {}
    public record Summary(long total, Map<String, Long> byStatus, long overdue, double completionRate) {}
}
