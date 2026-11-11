package dev.javafornestjs.security.mapper;

import dev.javafornestjs.data.TaskRow;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SecuredTaskMapper {
    @Select("""
            SELECT t.id,t.title,t.priority,t.owner_id FROM data_tasks t
            LEFT JOIN sec_users u ON u.id=t.owner_id
            WHERE (#{any}=TRUE OR u.department_id=#{department}) ORDER BY t.id LIMIT #{limit}
            """)
    List<TaskRow> list(@Param("limit") int limit, @Param("department") int department, @Param("any") boolean any);
    @Select("""
            SELECT t.id,t.title,t.priority,t.owner_id FROM data_tasks t
            LEFT JOIN sec_users u ON u.id=t.owner_id
            WHERE t.id=#{id} AND (#{any}=TRUE OR u.department_id=#{department})
            """)
    TaskRow find(@Param("id") int id, @Param("department") int department, @Param("any") boolean any);
    @Insert("INSERT INTO data_tasks(title,priority,owner_id) VALUES (#{title},#{priority},#{ownerId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int create(TaskRow row);
    @Update("""
            UPDATE data_tasks SET title=#{title},priority=#{priority}
            WHERE id=#{id} AND (#{any}=TRUE OR owner_id=#{owner})
            """)
    int replace(@Param("id") int id, @Param("title") String title, @Param("priority") int priority,
                @Param("owner") int owner, @Param("any") boolean any);
    @Delete("DELETE FROM data_tasks WHERE id=#{id} AND (#{any}=TRUE OR owner_id=#{owner})")
    int delete(@Param("id") int id, @Param("owner") int owner, @Param("any") boolean any);
}
