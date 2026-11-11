package dev.javafornestjs.data.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "data_tasks")
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, length = 80)
    private String title;
    @Column(nullable = false, columnDefinition = "tinyint")
    private Byte priority;
    @Column(name = "owner_id", nullable = false)
    private Integer ownerId;
    public Integer getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public Integer getPriority() { return priority == null ? null : priority.intValue(); }
    public void setPriority(Integer value) { priority = value == null ? null : value.byteValue(); }
    public Integer getOwnerId() { return ownerId; }
    public void setOwnerId(Integer value) { ownerId = value; }
}
