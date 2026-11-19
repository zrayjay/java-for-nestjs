package dev.javafornestjs.capstone;

import dev.javafornestjs.capstone.core.RequestFailure;

import static dev.javafornestjs.capstone.core.RequestFailure.Code.VALIDATION_ERROR;

final class ResourceIds {
    private ResourceIds() {
    }

    static long parseId(String id) {
        if (id != null && id.matches("[1-9][0-9]{0,18}")) {
            try {
                return Long.parseLong(id);
            } catch (NumberFormatException outOfBounds) {
                throw new RequestFailure(VALIDATION_ERROR, "ID is out of range");
            }
        }
        throw new RequestFailure(VALIDATION_ERROR, "ID must be a positive decimal string");
    }
}
