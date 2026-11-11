package dev.javafornestjs.data;

public final class DatabaseUrl {
    private DatabaseUrl() { }
    public static String dedicated(String url) {
        if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/data_lab"
                + "(\\?allowPublicKeyRetrieval=true&useSSL=false)?")) {
            throw new IllegalArgumentException("Only the local dedicated data_lab URL is allowed");
        }
        int port = Integer.parseInt(url.substring(url.indexOf(':', "jdbc:mysql://".length()) + 1,
                url.indexOf('/' , "jdbc:mysql://".length())));
        if (port < 1 || port > 65535) { throw new IllegalArgumentException("Invalid port"); }
        return url;
    }
}
