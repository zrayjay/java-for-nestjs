package dev.javafornestjs.capstone.security;

import java.util.Locale;

public enum OrderPermission {
    READ, CREATE, EDIT, SUBMIT, APPROVE, CANCEL;

    public String code() {
        return "orders:" + name().toLowerCase(Locale.ROOT);
    }

    public boolean supports(OrderScope scope) {
        return switch (this) {
            case CREATE, EDIT, SUBMIT -> scope == OrderScope.OWN;
            case READ, APPROVE, CANCEL -> true;
        };
    }
}
