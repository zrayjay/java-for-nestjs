package dev.javafornestjs.capstone.orders;

import dev.javafornestjs.capstone.security.OrderScope;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface OrdersMapper {
    String COLUMNS = """
            id, customer_name, total_amount_cents, owner_id, department_id, status, version, created_at, updated_at
            """;
    String SCOPE = """
            <choose>
              <when test="scope.name() == 'ALL'"></when>
              <when test="scope.name() == 'DEPARTMENT'">AND department_id = #{department}</when>
              <when test="scope.name() == 'OWN'">AND department_id = #{department} AND owner_id = #{user}</when>
              <otherwise> AND 1 = 0 </otherwise>
            </choose>
            """;
    String FILTER = """
            <if test="status != null"> AND status = #{status} </if>
            """;

    @Select("<script> SELECT " + COLUMNS + " FROM sales_order WHERE id = #{id} " + SCOPE + "</script>")
    OrderRow find(long id, OrderScope scope, long user, long department);

    @Select("<script> SELECT " + COLUMNS + " FROM sales_order WHERE 1 = 1 " + SCOPE + FILTER
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset} </script>")
    List<OrderRow> page(OrderScope scope, long user, long department, String status, int size, long offset);

    @Select("<script> SELECT COUNT(*) FROM sales_order WHERE 1 = 1 " + SCOPE + FILTER + "</script>")
    long count(OrderScope scope, long user, long department, String status);

    @Insert("""
            INSERT INTO sales_order(customer_name, total_amount_cents, owner_id, department_id, status,
                version, created_at, updated_at)
            VALUES (#{customer}, #{amount}, #{owner}, #{department}, 'DRAFT', 1, #{now}, #{now})
            """)
    void create(String customer, int amount, long owner, long department, long now);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Update("<script> UPDATE sales_order SET customer_name = #{customer}, total_amount_cents = #{amount}, "
            + "status = #{target}, version = version + 1, updated_at = #{now} WHERE id = #{id} "
            + "AND version = #{version} AND status = #{source} " + SCOPE + "</script>")
    int update(long id, int version, String source, String target, String customer, int amount,
            long now, OrderScope scope, long user, long department);

    @Insert("""
            INSERT INTO sales_order_history(order_id, actor_id, action, from_status, to_status, version, occurred_at)
            VALUES (#{id}, #{actor}, #{action}, #{from}, #{to}, #{version}, #{now})
            """)
    void append(long id, long actor, String action, String from, String to, int version, long now);

    @Select("""
            SELECT actor_id, action, from_status, to_status, version, occurred_at
            FROM sales_order_history WHERE order_id = #{id} ORDER BY version
            """)
    List<HistoryRow> history(long id);

    record OrderRow(long id, String customerName, int totalAmountCents, long ownerId, long departmentId,
            String status, int version, long createdAt, long updatedAt) {
    }

    record HistoryRow(long actorId, String action, String fromStatus, String toStatus, int version, long occurredAt) {
    }
}
