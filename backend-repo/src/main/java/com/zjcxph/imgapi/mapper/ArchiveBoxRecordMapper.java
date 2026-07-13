package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.dto.resp.ArchiveBoxGroupDTO;
import com.zjcxph.imgapi.dto.resp.ArchiveBoxSummaryDTO;
import com.zjcxph.imgapi.entity.ArchiveBoxRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArchiveBoxRecordMapper {

    @Insert("""
            INSERT INTO mr_archive_box_record
                (bah, sjh, box_no, expected_box_no, status, remark)
            VALUES
                (#{bah}, #{sjh}, #{boxNo}, #{expectedBoxNo}, #{status}, #{remark})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ArchiveBoxRecord record);

    @Update("""
            UPDATE mr_archive_box_record
            SET bah = #{bah},
                sjh = #{sjh},
                box_no = #{boxNo},
                expected_box_no = #{expectedBoxNo},
                status = #{status},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(ArchiveBoxRecord record);

    @Delete("DELETE FROM mr_archive_box_record WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   bah,
                   sjh,
                   box_no AS boxNo,
                   expected_box_no AS expectedBoxNo,
                   status,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM mr_archive_box_record
            WHERE id = #{id}
            """)
    ArchiveBoxRecord findById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   bah,
                   sjh,
                   box_no AS boxNo,
                   expected_box_no AS expectedBoxNo,
                   status,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM mr_archive_box_record
            WHERE sjh = #{sjh}
            LIMIT 1
            """)
    ArchiveBoxRecord findBySjh(@Param("sjh") String sjh);

    List<ArchiveBoxRecord> findPage(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("keyword") String keyword,
            @Param("bah") String bah,
            @Param("bahSearchCode") String bahSearchCode,
            @Param("sjh") String sjh,
            @Param("sjhSearchCode") String sjhSearchCode,
            @Param("boxNo") String boxNo,
            @Param("status") String status,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder
    );

    long countPage(
            @Param("keyword") String keyword,
            @Param("bah") String bah,
            @Param("bahSearchCode") String bahSearchCode,
            @Param("sjh") String sjh,
            @Param("sjhSearchCode") String sjhSearchCode,
            @Param("boxNo") String boxNo,
            @Param("status") String status
    );

    List<ArchiveBoxRecord> findByRecordCode(
            @Param("normalizedCode") String normalizedCode,
            @Param("searchCode") String searchCode
    );

    List<ArchiveBoxRecord> findByBoxNo(@Param("boxNo") String boxNo);

    ArchiveBoxSummaryDTO getSummary();

    List<ArchiveBoxGroupDTO> findBoxGroups(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("keyword") String keyword
    );

    long countBoxGroups(@Param("keyword") String keyword);
}
