package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.ArchiveRecord;
import com.zjcxph.imgapi.entity.Scan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArchiveRecordMapper {

    String SUMMARY_COLUMNS = "id, sjh, bah, "
            + "patient_id AS \"patientId\", "
            + "patient_name AS \"patientName\", "
            + "inpatient_department AS \"inpatientDepartment\", "
            + "archive_date AS \"archiveDate\", "
            + "discharge_date AS \"dischargeDate\", "
            + "archive_type AS \"archiveType\", "
            + "page_count AS \"pageCount\", "
            + "scan_count AS \"scanCount\", "
            + "scan_page_count AS \"scanPageCount\", "
            + "created_at AS \"createdAt\", "
            + "updated_at AS \"updatedAt\"";

    @Select("SELECT " + SUMMARY_COLUMNS + " FROM v_archive_summary WHERE id = #{id}")
    ArchiveRecord findById(@Param("id") Long id);

    @Select("SELECT " + SUMMARY_COLUMNS + " FROM v_archive_summary "
            + "WHERE id = app.resolve_archive_id(#{bah}, #{sjh}, FALSE)")
    ArchiveRecord findByCode(@Param("bah") String bah, @Param("sjh") String sjh);

    @Select("SELECT app.resolve_archive_id(#{bah}, #{sjh}, #{createWhenSjhPresent})")
    Long resolveArchiveId(
            @Param("bah") String bah,
            @Param("sjh") String sjh,
            @Param("createWhenSjhPresent") boolean createWhenSjhPresent
    );

    @Select("SELECT * FROM mr_scan WHERE archive_id = #{archiveId} "
            + "ORDER BY pages NULLS LAST, id")
    List<Scan> findScans(@Param("archiveId") Long archiveId);
}
