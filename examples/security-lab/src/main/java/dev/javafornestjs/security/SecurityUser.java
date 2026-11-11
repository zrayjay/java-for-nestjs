package dev.javafornestjs.security;

import java.security.Principal;
import java.util.Set;

public record SecurityUser(int id, String username, int departmentId, Set<String> permissions,
                           String sessionId) implements Principal {
    @Override
    public String getName() { return username; }
}
