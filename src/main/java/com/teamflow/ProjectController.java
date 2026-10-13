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

@RestController @Validated @RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService projects;
    public ProjectController(ProjectService projects) { this.projects = projects; }
    @GetMapping public Map<String, Object> list(Principal user) { return Map.of("data", projects.list(user.getName())); }
    @PostMapping public ResponseEntity<?> create(Principal user, @RequestBody @Valid ProjectInput input) {
        Project project = projects.create(user.getName(), input);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.id())).body(Map.of("data", project));
    }
    @GetMapping("/{projectId}/members") public Map<String, Object> members(Principal user, @PathVariable @Positive long projectId) { return Map.of("data", projects.members(projectId, user.getName())); }
    @PostMapping("/{projectId}/members") public ResponseEntity<?> member(Principal user, @PathVariable @Positive long projectId, @RequestBody @Valid MemberInput input) {
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/members/" + input.username())).body(Map.of("data", projects.addMember(projectId, user.getName(), input)));
    }
    @PutMapping("/{projectId}/members/{username}") public Map<String, Object> role(Principal user, @PathVariable @Positive long projectId,
        @PathVariable @Pattern(regexp = "[a-zA-Z0-9_]{3,40}") String username, @RequestBody @Valid RoleInput input) {
        return Map.of("data", projects.changeRole(projectId, user.getName(), username, input));
    }
    @GetMapping("/{projectId}/activities") public Map<String, Object> activities(Principal user, @PathVariable @Positive long projectId) { return Map.of("data", projects.activities(projectId, user.getName())); }
}
