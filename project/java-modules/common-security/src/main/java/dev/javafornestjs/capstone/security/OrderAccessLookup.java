package dev.javafornestjs.capstone.security;

public interface OrderAccessLookup {
    OrderAccess forSession(SessionIdentity identity);
}
