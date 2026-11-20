package dev.javafornestjs.capstone;

import dev.javafornestjs.capstone.core.AuditTrail;
import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.SessionService;
import dev.javafornestjs.capstone.system.SessionService.TokenPair;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    private final SessionService sessions;
    private final AuditTrail audit;

    AuthController(SessionService sessions, AuditTrail audit) {
        this.sessions = sessions;
        this.audit = audit;
    }

    @PostMapping("/login")
    ResponseEntity<TokenPair> login(@RequestBody Login request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(sessions.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenPair> refresh(@RequestBody Refresh request) {
        var result = sessions.refresh(request.refreshToken());
        if (result.replayedAccount() != null) {
            audit.recordCommittedReplay(result.replayedAccount());
        }
        if (result.tokens() == null) {
            throw SessionService.invalidAuthentication();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.tokens());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt caller, @RequestBody Refresh request) {
        sessions.logout(SessionIdentity.from(caller), request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    record Login(@Schema(requiredMode = REQUIRED) String username, @Schema(requiredMode = REQUIRED) String password) {
    }

    record Refresh(@Schema(requiredMode = REQUIRED) String refreshToken) {
    }
}
