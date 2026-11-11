package dev.javafornestjs.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    public record Login(@NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{0,39}") String username,
                         @NotBlank @Size(max = 72) String password) { }
    public record Refresh(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken) { }
    public record RoleChange(@NotNull @Size(max = 10) List<@NotNull @Positive Integer> roles) { }
    public record Me(int id, String username, int departmentId, List<String> permissions) { }
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/auth/login")
    public ResponseEntity<AuthService.Tokens> login(@Valid @RequestBody Login request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(request.username(), request.password()));
    }
    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthService.Tokens> refresh(@Valid @RequestBody Refresh request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.rotate(request.refreshToken()));
    }
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        auth.logout();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/me")
    public ResponseEntity<Me> me() {
        var user = AuthContext.current();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Me(user.id(), user.username(), user.departmentId(), user.permissions().stream().sorted().toList()));
    }
    @PutMapping("/admin/users/{id}/roles")
    public ResponseEntity<Void> roles(@PathVariable("id") String raw, @Valid @RequestBody RoleChange request) {
        if (!raw.matches("[1-9][0-9]{0,9}")) { throw new IllegalArgumentException("Invalid id"); }
        auth.assignRoles(Integer.parseInt(raw), request.roles());
        return ResponseEntity.noContent().build();
    }
}
