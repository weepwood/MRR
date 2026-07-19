package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.ExternalArchiveStoredGrant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ExternalArchiveAccessMapper {

    @Insert("""
            INSERT INTO mr_external_archive_nonce (client_id, nonce_hash, expires_at)
            VALUES (#{clientId}, #{nonceHash}, #{expiresAt})
            ON CONFLICT (client_id, nonce_hash) DO NOTHING
            """)
    int insertNonce(
            @Param("clientId") String clientId,
            @Param("nonceHash") String nonceHash,
            @Param("expiresAt") LocalDateTime expiresAt
    );

    @Delete("""
            DELETE FROM mr_external_archive_nonce
            WHERE expires_at < NOW()
            """)
    int deleteExpiredNonces();

    @Insert("""
            INSERT INTO mr_external_archive_ticket (
                ticket_hash, client_id, external_user_id, allow_download,
                grant_json, expires_at, created_ip
            )
            VALUES (
                #{ticketHash}, #{clientId}, #{externalUserId}, #{allowDownload},
                CAST(#{grantJson} AS JSONB), #{expiresAt}, #{createdIp}
            )
            """)
    int insertTicket(
            @Param("ticketHash") String ticketHash,
            @Param("clientId") String clientId,
            @Param("externalUserId") String externalUserId,
            @Param("allowDownload") boolean allowDownload,
            @Param("grantJson") String grantJson,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("createdIp") String createdIp
    );

    @Select("""
            WITH consumed AS (
                UPDATE mr_external_archive_ticket
                SET used_at = NOW()
                WHERE ticket_hash = #{ticketHash}
                  AND used_at IS NULL
                  AND expires_at > NOW()
                RETURNING client_id, external_user_id, allow_download, grant_json, expires_at
            )
            SELECT client_id AS clientId,
                   external_user_id AS externalUserId,
                   allow_download AS allowDownload,
                   grant_json::TEXT AS grantJson,
                   expires_at AS expiresAt
            FROM consumed
            """)
    ExternalArchiveStoredGrant consumeTicket(@Param("ticketHash") String ticketHash);

    @Insert("""
            INSERT INTO mr_external_archive_session (
                session_hash, client_id, external_user_id, allow_download,
                grant_json, expires_at, created_ip
            )
            VALUES (
                #{sessionHash}, #{clientId}, #{externalUserId}, #{allowDownload},
                CAST(#{grantJson} AS JSONB), #{expiresAt}, #{createdIp}
            )
            """)
    int insertSession(
            @Param("sessionHash") String sessionHash,
            @Param("clientId") String clientId,
            @Param("externalUserId") String externalUserId,
            @Param("allowDownload") boolean allowDownload,
            @Param("grantJson") String grantJson,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("createdIp") String createdIp
    );

    @Select("""
            SELECT client_id AS clientId,
                   external_user_id AS externalUserId,
                   allow_download AS allowDownload,
                   grant_json::TEXT AS grantJson,
                   expires_at AS expiresAt
            FROM mr_external_archive_session
            WHERE session_hash = #{sessionHash}
              AND revoked_at IS NULL
              AND expires_at > NOW()
            """)
    ExternalArchiveStoredGrant findSession(@Param("sessionHash") String sessionHash);

    @Update("""
            UPDATE mr_external_archive_session
            SET last_access_at = NOW()
            WHERE session_hash = #{sessionHash}
              AND revoked_at IS NULL
              AND expires_at > NOW()
            """)
    int touchSession(@Param("sessionHash") String sessionHash);

    @Update("""
            UPDATE mr_external_archive_session
            SET revoked_at = NOW()
            WHERE session_hash = #{sessionHash}
              AND revoked_at IS NULL
            """)
    int revokeSession(@Param("sessionHash") String sessionHash);

    @Insert("""
            INSERT INTO mr_external_archive_access_log (
                client_id, external_user_id, bah, sjh, action, image_id,
                client_ip, user_agent, result, request_id, detail
            )
            VALUES (
                #{clientId}, #{externalUserId}, #{bah}, #{sjh}, #{action}, #{imageId},
                #{clientIp}, #{userAgent}, #{result}, #{requestId}, #{detail}
            )
            """)
    int insertAccessLog(
            @Param("clientId") String clientId,
            @Param("externalUserId") String externalUserId,
            @Param("bah") String bah,
            @Param("sjh") String sjh,
            @Param("action") String action,
            @Param("imageId") Integer imageId,
            @Param("clientIp") String clientIp,
            @Param("userAgent") String userAgent,
            @Param("result") String result,
            @Param("requestId") String requestId,
            @Param("detail") String detail
    );
}
