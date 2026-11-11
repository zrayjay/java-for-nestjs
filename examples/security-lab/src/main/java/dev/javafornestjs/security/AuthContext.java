package dev.javafornestjs.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContext {
    private AuthContext() { }
    public static SecurityUser current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof SecurityUser user)) {
            throw new AccessDeniedException("Access denied");
        }
        return user;
    }
    public static void require(String... permissions) {
        var user = current();
        for (String permission : permissions) {
            if (user.permissions().contains(permission)) { return; }
        }
        throw new AccessDeniedException("Access denied");
    }
}
