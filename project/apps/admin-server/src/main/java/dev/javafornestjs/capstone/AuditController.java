package dev.javafornestjs.capstone;

import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.AuditService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static dev.javafornestjs.capstone.ResourceIds.parseId;

@RestController
class AuditController {
    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/api/v1/audit-events")
    AuditService.Page page(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return audit.page(SessionIdentity.from(jwt), resourceType, resourceId == null ? null : parseId(resourceId),
                page, size);
    }
}
