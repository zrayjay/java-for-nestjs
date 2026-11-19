package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.core.RequestFailure;
import java.nio.charset.StandardCharsets;

import static dev.javafornestjs.capstone.core.RequestFailure.Code.VALIDATION_ERROR;

final class AccountInputs {
    private AccountInputs() {
    }

    static void validateUsername(String username) {
        if (username == null || !username.matches("[a-z][a-z0-9._-]{2,39}")) {
            throw new RequestFailure(VALIDATION_ERROR, "Invalid username");
        }
    }

    static boolean usablePassword(String password) {
        return password != null && password.length() <= 72
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    static void validatePassword(String password) {
        if (!usablePassword(password) || password.codePointCount(0, password.length()) < 12) {
            throw new RequestFailure(VALIDATION_ERROR, "Password requires 12 characters and at most 72 UTF-8 bytes");
        }
    }
}
