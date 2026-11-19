package dev.javafornestjs.capstone.system;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface AccountsMapper {
    String USER_COLUMNS = "id, username, display_name, department_id, password_hash, status, "
            + "credential_version, platform_admin";

    @Select("SELECT " + USER_COLUMNS + " FROM sys_user WHERE id = #{id}")
    Account find(long id);

    @Select("SELECT " + USER_COLUMNS + " FROM sys_user WHERE username = #{username}")
    Account byUsername(String username);

    @Select("SELECT " + USER_COLUMNS + " FROM sys_user WHERE id = #{id} FOR UPDATE")
    Account lock(long id);

    @Select("SELECT " + USER_COLUMNS + " FROM sys_user ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Account> page(int size, long offset);

    @Select("SELECT COUNT(*) FROM sys_user")
    long count();

    @Select("SELECT COUNT(*) FROM sys_user WHERE platform_admin AND status = 'ACTIVE'")
    long countActiveAdministrators();

    @Select("SELECT id FROM sys_admin_guard WHERE id = 1 FOR UPDATE")
    int lockAdministration();

    @Select("SELECT id, code, name FROM sys_department ORDER BY id")
    List<DepartmentRow> departments();

    @Select("SELECT COUNT(*) FROM sys_department WHERE id = #{id}")
    int countDepartment(long id);

    @Select("SELECT id FROM sys_department WHERE code = 'operations'")
    long initialDepartment();

    @Insert("""
            INSERT INTO sys_user(username, display_name, department_id, password_hash, platform_admin)
            VALUES (#{username}, #{displayName}, #{departmentId}, #{passwordHash}, #{admin})
            """)
    void insert(String username, String displayName, long departmentId, String passwordHash, boolean admin);

    @Update("""
            UPDATE sys_user
            SET credential_version = credential_version + IF(status <> #{status}, 1, 0),
                display_name = #{displayName}, department_id = #{departmentId}, status = #{status}
            WHERE id = #{id}
            """)
    void update(long id, String displayName, long departmentId, String status);

    @Update("""
            UPDATE sys_user SET password_hash = #{passwordHash}, credential_version = credential_version + 1
            WHERE id = #{id}
            """)
    void replacePassword(long id, String passwordHash);

    record Account(long id, String username, String displayName, long departmentId, String passwordHash,
            String status, long credentialVersion, boolean platformAdmin) {
        boolean enabled() {
            return status.equals("ACTIVE");
        }
    }

    record DepartmentRow(long id, String code, String name) {
    }
}
