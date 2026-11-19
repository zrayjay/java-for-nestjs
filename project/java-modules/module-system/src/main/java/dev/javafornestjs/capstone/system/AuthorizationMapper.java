package dev.javafornestjs.capstone.system;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface AuthorizationMapper {
    @Select("""
            SELECT DISTINCT g.permission, g.scope
            FROM sys_role_grant g JOIN sys_user_role ur ON g.role_id = ur.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<GrantRow> grantsForUser(long userId);

    @Select("""
            <script>
            SELECT user_id, role_id FROM sys_user_role WHERE user_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY role_id
            </script>
            """)
    List<UserRole> rolesForUsers(List<Long> ids);

    @Select("SELECT id, code, name FROM sys_role ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<RoleRow> pageRoles(int size, long offset);

    @Select("SELECT COUNT(*) FROM sys_role")
    long countRoles();

    @Select("SELECT id, code, name FROM sys_role WHERE id = #{id}")
    RoleRow findRole(long id);

    @Select("SELECT id, code, name FROM sys_role WHERE code = #{code}")
    RoleRow roleByCode(String code);

    @Select("""
            <script>
            SELECT role_id, permission, scope FROM sys_role_grant WHERE role_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY permission
            </script>
            """)
    List<RoleGrant> grantsForRoles(List<Long> ids);

    @Insert("INSERT INTO sys_role(code, name) VALUES (#{code}, #{name})")
    void insertRole(String code, String name);

    @Update("UPDATE sys_role SET name = #{name} WHERE id = #{id}")
    void renameRole(long id, String name);

    @Delete("DELETE FROM sys_role_grant WHERE role_id = #{roleId}")
    void deleteGrants(long roleId);

    @Insert("INSERT INTO sys_role_grant(role_id, permission, scope) VALUES (#{roleId}, #{permission}, #{scope})")
    void insertGrant(long roleId, String permission, String scope);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteUserRoles(long userId);

    @Insert("INSERT INTO sys_user_role(user_id, role_id) VALUES (#{userId}, #{roleId})")
    void insertUserRole(long userId, long roleId);

    @Update("UPDATE sys_user SET platform_admin = #{enabled} WHERE id = #{userId}")
    void changeAdministrator(long userId, boolean enabled);

    @Select("SELECT id, parent_id, label, route_key, permission, sort_order FROM sys_menu ORDER BY sort_order, id")
    List<MenuRow> menus();

    @Select("SELECT id, parent_id, label, route_key, permission, sort_order FROM sys_menu WHERE id = #{id}")
    MenuRow menu(long id);

    @Select("SELECT COUNT(*) FROM sys_menu WHERE parent_id = #{id}")
    int childCount(long id);

    @Insert("""
            INSERT INTO sys_menu(parent_id, label, route_key, permission, sort_order)
            VALUES (#{parentId}, #{label}, #{routeKey}, #{permission}, #{sortOrder})
            """)
    void insertMenu(Long parentId, String label, String routeKey, String permission, int sortOrder);

    @Update("""
            UPDATE sys_menu SET parent_id = #{parentId}, label = #{label}, route_key = #{routeKey},
                permission = #{permission}, sort_order = #{sortOrder} WHERE id = #{id}
            """)
    void updateMenu(long id, Long parentId, String label, String routeKey, String permission, int sortOrder);

    @Delete("DELETE FROM sys_menu WHERE id = #{id}")
    void deleteMenu(long id);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    record GrantRow(String permission, String scope) {
    }

    record UserRole(long userId, long roleId) {
    }

    record RoleRow(long id, String code, String name) {
    }

    record RoleGrant(long roleId, String permission, String scope) {
    }

    record MenuRow(long id, Long parentId, String label, String routeKey, String permission, int sortOrder) {
    }
}
