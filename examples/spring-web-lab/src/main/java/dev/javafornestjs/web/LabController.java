package dev.javafornestjs.web;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LabController {
    private final ApiProperties properties;
    public LabController(ApiProperties properties) { this.properties = properties; }
    public record LabInfo(String label) { }
    public record Health(String status) { }
    @GetMapping("/lab-info")
    public LabInfo info() { return new LabInfo(properties.label()); }
    @GetMapping("/health")
    public Health health() { return new Health("ok"); }
    @PostMapping("/validation/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void draft(@Validated(Tasks.Draft.class) @RequestBody Tasks.ValidationProbe probe) { }
    @PostMapping("/validation/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(@Validated(Tasks.Publish.class) @RequestBody Tasks.ValidationProbe probe) { }
}
