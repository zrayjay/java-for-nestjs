package dev.javafornestjs.capstone;

import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.AccountsService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static dev.javafornestjs.capstone.ResourceIds.parseId;

@RestController
@RequestMapping("/api/v1")
class UsersController {
    private final AccountsService accounts;

    UsersController(AccountsService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/me")
    AccountsService.Me me(@AuthenticationPrincipal Jwt jwt) {
        return accounts.me(SessionIdentity.from(jwt));
    }

    @GetMapping("/departments")
    List<AccountsService.Department> departments(@AuthenticationPrincipal Jwt jwt) {
        return accounts.departments(SessionIdentity.from(jwt));
    }

    @GetMapping("/users")
    AccountsService.Page users(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return accounts.users(SessionIdentity.from(jwt), page, size);
    }

    @GetMapping("/users/{id}")
    AccountsService.User user(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return accounts.user(SessionIdentity.from(jwt), parseId(id));
    }

    @PostMapping("/users")
    ResponseEntity<AccountsService.User> create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateUser request) {
        var user = accounts.create(SessionIdentity.from(jwt), request.username(), request.displayName(),
                parseId(request.departmentId()), request.password());
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }

    @PatchMapping("/users/{id}")
    AccountsService.User update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody UpdateUser request) {
        Long department = request.departmentId() == null ? null : parseId(request.departmentId());
        return accounts.update(SessionIdentity.from(jwt), parseId(id), request.displayName(), department,
                request.status());
    }

    @DeleteMapping("/users/{id}")
    ResponseEntity<Void> disable(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        accounts.disable(SessionIdentity.from(jwt), parseId(id));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/password")
    ResponseEntity<Void> replacePassword(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody PasswordChange request) {
        accounts.changePassword(SessionIdentity.from(jwt), parseId(id), request.password());
        return ResponseEntity.noContent().build();
    }

    record CreateUser(String username, String displayName, String departmentId, String password) {
    }

    record UpdateUser(String displayName, String departmentId, String status) {
    }

    record PasswordChange(String password) {
    }
}
