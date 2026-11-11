package dev.javafornestjs.security;

import dev.javafornestjs.data.TaskRow;
import dev.javafornestjs.security.mapper.SecuredTaskMapper;
import dev.javafornestjs.web.TaskRepository;
import dev.javafornestjs.web.Tasks;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class PolicyTaskRepository implements TaskRepository {
    private final SecuredTaskMapper mapper;
    private final AuthService auth;
    public PolicyTaskRepository(SecuredTaskMapper mapper, AuthService auth) {
        this.mapper = mapper;
        this.auth = auth;
    }
    private Tasks.TaskItem item(TaskRow row) { return new Tasks.TaskItem(row.getId(), row.getTitle(), row.getPriority()); }
    @Override
    public List<Tasks.TaskItem> list(int limit) {
        AuthContext.require("task:read:department", "task:read:any");
        var user = AuthContext.current();
        return mapper.list(limit, user.departmentId(), user.permissions().contains("task:read:any")).stream().map(this::item).toList();
    }
    @Override
    public Optional<Tasks.TaskItem> find(int id) {
        AuthContext.require("task:read:department", "task:read:any");
        var user = AuthContext.current();
        return Optional.ofNullable(mapper.find(id, user.departmentId(), user.permissions().contains("task:read:any"))).map(this::item);
    }
    @Override
    @Transactional
    public Tasks.TaskItem create(String title, int priority) {
        AuthContext.require("task:write:own", "task:write:any");
        var user = AuthContext.current();
        var row = new TaskRow();
        row.setTitle(title);
        row.setPriority(priority);
        row.setOwnerId(user.id());
        mapper.create(row);
        auth.audit(user.id(), "task.created", row.getId().toString());
        return item(row);
    }
    @Override
    @Transactional
    public Optional<Tasks.TaskItem> replace(int id, String title, int priority) {
        AuthContext.require("task:write:own", "task:write:any");
        var user = AuthContext.current();
        if (mapper.replace(id, title, priority, user.id(), user.permissions().contains("task:write:any")) == 0) { return Optional.empty(); }
        auth.audit(user.id(), "task.updated", Integer.toString(id));
        return Optional.of(new Tasks.TaskItem(id, title, priority));
    }
    @Override
    @Transactional
    public boolean delete(int id) {
        AuthContext.require("task:write:own", "task:write:any");
        var user = AuthContext.current();
        if (mapper.delete(id, user.id(), user.permissions().contains("task:write:any")) == 0) { return false; }
        auth.audit(user.id(), "task.deleted", Integer.toString(id));
        return true;
    }
}
