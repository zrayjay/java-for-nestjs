package dev.javafornestjs.messaging;

import java.net.URI;
import java.util.Set;

public final class MessageEnvironment {
    private static final Set<String> HOSTS = Set.of("127.0.0.1", "localhost");
    private MessageEnvironment() { }
    public static String jdbc(String value) {
        if (value == null || !value.startsWith("jdbc:mysql://")) { throw new IllegalArgumentException("Explicit local message_lab JDBC URL required"); }
        var uri = URI.create(value.substring(5));
        if (!HOSTS.contains(uri.getHost()) || !"/message_lab".equals(uri.getRawPath()) || uri.getUserInfo() != null
                || uri.getFragment() != null || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535))
                || (uri.getRawQuery() != null && !Set.of("allowPublicKeyRetrieval=true&useSSL=false", "useSSL=false&allowPublicKeyRetrieval=true").contains(uri.getRawQuery()))) {
            throw new IllegalArgumentException("Only the disposable local message_lab JDBC URL is allowed");
        }
        return value;
    }
    public static String amqp(String value) {
        if (value == null) { throw new IllegalArgumentException("Explicit local AMQP message_lab URL required"); }
        var uri = URI.create(value);
        if (!"amqp".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost()) || !"/message_lab".equals(uri.getRawPath())
                || uri.getUserInfo() == null || !uri.getUserInfo().contains(":") || uri.getFragment() != null
                || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535))
                || (uri.getRawQuery() != null && !"heartbeat=5".equals(uri.getRawQuery()))) {
            throw new IllegalArgumentException("Only the disposable local AMQP message_lab vhost is allowed");
        }
        return value;
    }
}
