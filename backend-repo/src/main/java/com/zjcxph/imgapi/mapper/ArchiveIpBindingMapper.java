package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.ArchiveIpBinding;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface ArchiveIpBindingMapper {

    @Insert("""
            INSERT INTO mr_archive_ip_binding (
                access_date, userid, bound_ip, ip_change_count, first_access_at, last_access_at
            )
            VALUES (#{accessDate}, #{userid}, #{boundIp}, 0, NOW(), NOW())
            ON CONFLICT (access_date, userid) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("accessDate") LocalDate accessDate,
            @Param("userid") String userid,
            @Param("boundIp") String boundIp
    );

    @Select("""
            SELECT id,
                   access_date AS accessDate,
                   userid,
                   bound_ip AS boundIp,
                   ip_change_count AS ipChangeCount,
                   first_access_at AS firstAccessAt,
                   last_access_at AS lastAccessAt
            FROM mr_archive_ip_binding
            WHERE access_date = #{accessDate}
              AND userid = #{userid}
            FOR UPDATE
            """)
    ArchiveIpBinding findForUpdate(
            @Param("accessDate") LocalDate accessDate,
            @Param("userid") String userid
    );

    @Update("""
            UPDATE mr_archive_ip_binding
            SET last_access_at = NOW()
            WHERE id = #{id}
            """)
    int touch(@Param("id") Long id);

    @Update("""
            UPDATE mr_archive_ip_binding
            SET bound_ip = #{boundIp},
                ip_change_count = ip_change_count + 1,
                last_access_at = NOW()
            WHERE id = #{id}
            """)
    int changeIp(
            @Param("id") Long id,
            @Param("boundIp") String boundIp
    );
}
