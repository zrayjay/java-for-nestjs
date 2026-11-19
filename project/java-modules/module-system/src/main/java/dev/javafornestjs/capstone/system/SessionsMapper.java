package dev.javafornestjs.capstone.system;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface SessionsMapper {
    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM sys_session_family f JOIN sys_user u ON f.user_id = u.id
                WHERE u.id = #{userId} AND f.id = #{familyId} AND u.status = 'ACTIVE'
                  AND u.credential_version = #{version} AND f.credential_version = #{version}
                  AND f.revoked_at IS NULL AND f.expires_at > #{now}
            )
            """)
    boolean active(long userId, String familyId, long version, long now);

    @Select("""
            SELECT f.user_id FROM sys_refresh_session t JOIN sys_session_family f ON t.family_id = f.id
            WHERE t.token_hash = #{digest}
            """)
    Long tokenOwner(String digest);

    @Select("""
            SELECT f.id, f.user_id, f.credential_version, f.expires_at, f.revoked_at, t.consumed_at
            FROM sys_refresh_session t JOIN sys_session_family f ON t.family_id = f.id
            WHERE t.token_hash = #{digest} FOR UPDATE
            """)
    RefreshState lockToken(String digest);

    @Insert("""
            INSERT INTO sys_session_family(id, user_id, credential_version, expires_at)
            VALUES (#{id}, #{userId}, #{version}, #{expiry})
            """)
    void insertFamily(String id, long userId, long version, long expiry);

    @Insert("INSERT INTO sys_refresh_session(token_hash, family_id) VALUES (#{digest}, #{familyId})")
    void insertToken(String digest, String familyId);

    @Update("UPDATE sys_refresh_session SET consumed_at = #{now} WHERE token_hash = #{digest}")
    void consume(String digest, long now);

    @Update("UPDATE sys_session_family SET revoked_at = #{now} WHERE id = #{id} AND revoked_at IS NULL")
    void revoke(String id, long now);

    record RefreshState(String id, long userId, long credentialVersion, long expiresAt, Long revokedAt,
            Long consumedAt) {
    }
}
