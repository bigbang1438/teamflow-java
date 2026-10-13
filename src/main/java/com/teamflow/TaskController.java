package com.teamflow;

import com.teamflow.ApiModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/v1/projects/{projectId}")
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service = service; }

    @GetMapping("/tasks")
    public TaskPage list(
        Principal user, @PathVariable @Positive long projectId,
        @RequestParam(required = false) @Size(max = 120) String q,
        @RequestParam(required = false) @Pattern(regexp = "backlog|doing|blocked|done") String status,
        @RequestParam(required = false) @Pattern(regexp = "low|medium|high") String priority,
        @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return service.list(projectId, user.getName(), q, status, priority, page, size);
    }
    @GetMapping("/tasks/{id}")
    public Map<String, Task> get(Principal user, @PathVariable @Positive long projectId, @PathVariable @Positive long id) { return Map.of("data", service.get(projectId, user.getName(), id)); }
    @PostMapping(value = "/tasks", consumes = "application/json")
    public ResponseEntity<Map<String, Task>> create(Principal user, @PathVariable @Positive long projectId, @RequestBody @Valid TaskInput input) {
        Task task = service.create(projectId, user.getName(), input);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/tasks/" + task.id())).body(Map.of("data", task));
    }
    @PutMapping(value = "/tasks/{id}", consumes = "application/json")
    public Map<String, Task> update(Principal user, @PathVariable @Positive long projectId, @PathVariable @Positive long id, @RequestBody @Valid TaskInput input) {
        return Map.of("data", service.update(projectId, user.getName(), id, input));
    }
    @PatchMapping(value = "/tasks/{id}/status", consumes = "application/json")
    public Map<String, Task> status(Principal user, @PathVariable @Positive long projectId, @PathVariable @Positive long id, @RequestBody @Valid StatusInput input) {
        return Map.of("data", service.changeStatus(projectId, user.getName(), id, input));
    }
    @GetMapping("/tasks/{id}/activities")
    public Map<String, Object> activities(Principal user, @PathVariable @Positive long projectId, @PathVariable @Positive long id) { return Map.of("data", service.activities(projectId, user.getName(), id)); }
    @GetMapping("/summary")
    public Map<String, Summary> summary(Principal user, @PathVariable @Positive long projectId) { return Map.of("data", service.summary(projectId, user.getName())); }
    @GetMapping("/tasks/{id}/comments") public Map<String, Object> comments(Principal user, @PathVariable @Positive long projectId, @PathVariable @Positive long id) { return Map.of("data", service.comments(projectId, user.getName(), id)); }
    @PostMapping("/tasks/{id}/comments") public ResponseEntity<?> comment(Principal user, @PathVariable @Positive long projectId, @PathVariable @Positive long id, @RequestBody @Valid CommentInput input) {
        Comment comment = service.comment(projectId, user.getName(), id, input);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/tasks/" + id + "/comments")).body(Map.of("data", comment));
    }
}
