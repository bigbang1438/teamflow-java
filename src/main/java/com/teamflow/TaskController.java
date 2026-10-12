package com.teamflow;

import com.teamflow.ApiModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/v1")
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service = service; }

    @GetMapping("/tasks")
    public TaskPage list(
        @RequestParam(required = false) @Size(max = 120) String q,
        @RequestParam(required = false) @Pattern(regexp = "backlog|doing|blocked|done") String status,
        @RequestParam(required = false) @Pattern(regexp = "low|medium|high") String priority,
        @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return service.list(q, status, priority, page, size);
    }
    @GetMapping("/tasks/{id}")
    public Map<String, Task> get(@PathVariable @Positive long id) { return Map.of("data", service.get(id)); }
    @PostMapping(value = "/tasks", consumes = "application/json")
    public ResponseEntity<Map<String, Task>> create(@RequestBody @Valid TaskInput input) {
        Task task = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + task.id())).body(Map.of("data", task));
    }
    @PutMapping(value = "/tasks/{id}", consumes = "application/json")
    public Map<String, Task> update(@PathVariable @Positive long id, @RequestBody @Valid TaskInput input) {
        return Map.of("data", service.update(id, input));
    }
    @PatchMapping(value = "/tasks/{id}/status", consumes = "application/json")
    public Map<String, Task> status(@PathVariable @Positive long id, @RequestBody @Valid StatusInput input) {
        return Map.of("data", service.changeStatus(id, input));
    }
    @GetMapping("/tasks/{id}/activities")
    public Map<String, Object> activities(@PathVariable @Positive long id) { return Map.of("data", service.activities(id)); }
    @GetMapping("/summary")
    public Map<String, Summary> summary() { return Map.of("data", service.summary()); }
}
