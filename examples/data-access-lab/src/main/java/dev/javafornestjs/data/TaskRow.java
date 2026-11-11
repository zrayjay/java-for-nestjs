package dev.javafornestjs.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("data_tasks")
public class TaskRow {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String title;
    private Integer priority;
    private Integer ownerId;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer value) { priority = value; }
    public Integer getOwnerId() { return ownerId; }
    public void setOwnerId(Integer value) { ownerId = value; }
}
