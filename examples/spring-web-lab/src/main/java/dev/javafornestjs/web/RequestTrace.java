package dev.javafornestjs.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class RequestTrace {
    private static final String ATTRIBUTE = RequestTrace.class.getName();
    private static final class Entry {
        private final List<String> events = new ArrayList<>();
        private final CountDownLatch finished = new CountDownLatch(1);
    }
    private final ConcurrentHashMap<String, Entry> completed = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();

    public void begin(HttpServletRequest request, HttpServletResponse response) {
        String id = UUID.randomUUID().toString();
        var entry = new Entry();
        request.setAttribute(ATTRIBUTE, entry);
        completed.put(id, entry);
        order.add(id);
        while (completed.size() > 100) {
            String oldest = order.poll();
            if (oldest == null) { break; }
            completed.remove(oldest);
        }
        response.setHeader("X-Lab-Request-Id", id);
    }
    public void add(HttpServletRequest request, String event) {
        if (request.getAttribute(ATTRIBUTE) instanceof Entry entry) { entry.events.add(event); }
    }
    public void finish(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof Entry entry) { entry.finished.countDown(); }
    }
    public List<String> take(String id) throws InterruptedException {
        var entry = completed.get(id);
        if (entry == null || !entry.finished.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Request trace not completed");
        }
        completed.remove(id);
        order.remove(id);
        return List.copyOf(entry.events);
    }
}
