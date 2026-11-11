package dev.javafornestjs.web;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
    private final TaskRepository repository;
    public TaskService(TaskRepository repository) { this.repository = repository; }

    public static final class MissingTask extends RuntimeException { }

    public List<Tasks.TaskItem> list(int limit) {
        if (limit < 1 || limit > 100) { throw new IllegalArgumentException("Invalid limit"); }
        return repository.list(limit);
    }
    public Tasks.TaskItem find(int id) { return repository.find(id).orElseThrow(MissingTask::new); }
    public Tasks.TaskItem create(String title, int priority) { return repository.create(title, priority); }
    public Tasks.TaskItem replace(int id, String title, int priority) {
        return repository.replace(id, title, priority).orElseThrow(MissingTask::new);
    }
    public void delete(int id) {
        if (!repository.delete(id)) { throw new MissingTask(); }
    }
}
