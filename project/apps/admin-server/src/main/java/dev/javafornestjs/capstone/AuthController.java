package dev.javafornestjs.capstone;

import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.SessionService;
import dev.javafornestjs.capstone.system.SessionService.TokenPair;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    private final SessionService sessions;

    AuthController(SessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/login")
    ResponseEntity<TokenPair> login(@RequestBody Login request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(sessions.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenPair> refresh(@RequestBody Refresh request) {
        var pair = sessions.refresh(request.refreshToken()).orElseThrow(SessionService::invalidAuthentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pair);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt caller, @RequestBody Refresh request) {
        sessions.logout(SessionIdentity.from(caller), request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    record Login(String username, String password) {
    }

    record Refresh(String refreshToken) {
    }
}
