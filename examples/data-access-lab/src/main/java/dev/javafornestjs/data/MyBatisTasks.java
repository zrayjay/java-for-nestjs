package dev.javafornestjs.data;

import dev.javafornestjs.web.TaskRepository;
import dev.javafornestjs.web.Tasks;
import java.util.List;
import java.util.Optional;

public final class MyBatisTasks implements TaskRepository {
    private final TaskMapper mapper;
    public MyBatisTasks(TaskMapper mapper) { this.mapper = mapper; }
    private Tasks.TaskItem item(TaskRow row) { return new Tasks.TaskItem(row.getId(), row.getTitle(), row.getPriority()); }
    @Override
    public List<Tasks.TaskItem> list(int limit) { return mapper.listLimited(limit).stream().map(this::item).toList(); }
    @Override
    public Optional<Tasks.TaskItem> find(int id) { return Optional.ofNullable(mapper.selectById(id)).map(this::item); }
    @Override
    public Tasks.TaskItem create(String title, int priority) {
        var row = new TaskRow();
        row.setTitle(title);
        row.setPriority(priority);
        row.setOwnerId(1);
        mapper.insert(row);
        return item(row);
    }
    @Override
    public Optional<Tasks.TaskItem> replace(int id, String title, int priority) {
        var row = new TaskRow();
        row.setId(id);
        row.setTitle(title);
        row.setPriority(priority);
        return mapper.updateById(row) == 0 ? Optional.empty() : find(id);
    }
    @Override
    public boolean delete(int id) { return mapper.deleteById(id) != 0; }
}
