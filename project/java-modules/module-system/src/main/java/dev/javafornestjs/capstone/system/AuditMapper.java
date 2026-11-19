package dev.javafornestjs.capstone.system;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
interface AuditMapper {
    String FILTERS = """
             <if test="resourceType != null"> AND resource_type = #{resourceType} </if>
             <if test="resourceId != null"> AND resource_id = #{resourceId} </if>
            """;

    @Insert("""
            INSERT INTO sys_audit_event(request_id, actor_id, resource_type, resource_id, action, occurred_at)
            VALUES (#{requestId}, #{actorId}, #{resourceType}, #{resourceId}, #{action}, #{timestamp})
            """)
    void insert(String requestId, long actorId, String resourceType, long resourceId, String action, long timestamp);

    @Select("<script> SELECT id, request_id, actor_id, resource_type, resource_id, action, occurred_at "
            + "FROM sys_audit_event WHERE 1 = 1 " + FILTERS + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset} </script>")
    List<AuditRow> page(String resourceType, Long resourceId, int size, long offset);

    @Select("<script> SELECT COUNT(*) FROM sys_audit_event WHERE 1 = 1 " + FILTERS + "</script>")
    long count(String resourceType, Long resourceId);

    record AuditRow(long id, String requestId, long actorId, String resourceType, long resourceId,
            String action, long occurredAt) {
    }
}
