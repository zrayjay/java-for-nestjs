package dev.javafornestjs.web;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "lab.storage", havingValue = "memory", matchIfMissing = true)
public class InMemoryTasks implements TaskRepository {
    private final ConcurrentHashMap<Integer, Tasks.TaskItem> items = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();

    @Override
    public List<Tasks.TaskItem> list(int limit) {
        return items.values().stream().sorted(Comparator.comparingInt(Tasks.TaskItem::id)).limit(limit).toList();
    }
    @Override
    public Optional<Tasks.TaskItem> find(int id) { return Optional.ofNullable(items.get(id)); }
    @Override
    public Tasks.TaskItem create(String title, int priority) {
        var item = new Tasks.TaskItem(nextId.updateAndGet(Math::incrementExact), title, priority);
        items.put(item.id(), item);
        return item;
    }
    @Override
    public Optional<Tasks.TaskItem> replace(int id, String title, int priority) {
        return Optional.ofNullable(items.computeIfPresent(id, (key, old) -> new Tasks.TaskItem(id, title, priority)));
    }
    @Override
    public boolean delete(int id) { return items.remove(id) != null; }

    public void clearForTest() {
        items.clear();
        nextId.set(0);
    }
}
