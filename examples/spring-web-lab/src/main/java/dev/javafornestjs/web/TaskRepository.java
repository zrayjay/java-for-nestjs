package dev.javafornestjs.web;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    List<Tasks.TaskItem> list(int limit);
    Optional<Tasks.TaskItem> find(int id);
    Tasks.TaskItem create(String title, int priority);
    Optional<Tasks.TaskItem> replace(int id, String title, int priority);
    boolean delete(int id);
}
