package dev.javafornestjs.web;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
@ApiResponses({@ApiResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ApiErrors.ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Missing resource",
                content = @Content(schema = @Schema(implementation = ApiErrors.ApiError.class)))})
public class TasksController {
    private final TaskService service;
    private final RequestTrace traces;
    public TasksController(TaskService service, RequestTrace traces) {
        this.service = service;
        this.traces = traces;
    }
    private int id(String value) {
        if (!value.matches("[1-9][0-9]{0,9}")) { throw new IllegalArgumentException("Invalid id"); }
        return Integer.parseInt(value);
    }
    @GetMapping
    public List<Tasks.TaskView> list(@RequestParam(name = "limit", required = false) String limit,
                                     HttpServletRequest request) {
        traces.add(request, "controller");
        if (limit == null) { limit = "20"; }
        if (!limit.matches("[1-9][0-9]{0,2}")) { throw new IllegalArgumentException("Invalid limit"); }
        return service.list(Integer.parseInt(limit)).stream().map(Tasks.TaskView::from).toList();
    }
    @GetMapping("/{id}")
    public Tasks.TaskView find(@PathVariable("id") String value, HttpServletRequest request) {
        traces.add(request, "controller");
        return Tasks.TaskView.from(service.find(id(value)));
    }
    @PostMapping
    @ApiResponse(responseCode = "201", description = "Created")
    public ResponseEntity<Tasks.TaskView> create(@Valid @RequestBody Tasks.TaskWriteRequest body,
                                                HttpServletRequest request) {
        traces.add(request, "controller");
        var item = service.create(body.title(), body.details().asInt());
        return ResponseEntity.created(URI.create("/tasks/" + item.id())).body(Tasks.TaskView.from(item));
    }
    @PutMapping("/{id}")
    public Tasks.TaskView replace(@PathVariable("id") String value,
                                  @Valid @RequestBody Tasks.TaskWriteRequest body, HttpServletRequest request) {
        traces.add(request, "controller");
        return Tasks.TaskView.from(service.replace(id(value), body.title(), body.details().asInt()));
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String value, HttpServletRequest request) {
        traces.add(request, "controller");
        service.delete(id(value));
    }
}
