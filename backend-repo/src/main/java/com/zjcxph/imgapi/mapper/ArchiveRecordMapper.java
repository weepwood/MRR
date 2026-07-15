package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.ArchiveRecord;
import com.zjcxph.imgapi.entity.Scan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArchiveRecordMapper {

    String DETAIL_SELECT = "SELECT a.id, a.sjh, a.bah, "
            + "a.patient_id AS \"patientId\", "
            + "a.patient_name AS \"patientName\", "
            + "a.inpatient_department AS \"inpatientDepartment\", "
            + "a.device_id AS \"deviceId\", "
            + "a.operator_no AS \"operatorNo\", "
            + "a.archive_date AS \"archiveDate\", "
            + "a.discharge_date AS \"dischargeDate\", "
            + "a.archive_type AS \"archiveType\", "
            + "a.page_count AS \"pageCount\", "
            + "a.source_statistics_id AS \"sourceStatisticsId\", "
            + "(SELECT COUNT(*) FROM mr_scan s "
            + "  WHERE s.archive_id = a.id AND s.uploadflag <> 0) AS \"scanCount\", "
            + "(SELECT COALESCE(SUM(s.pages), 0) FROM mr_scan s "
            + "  WHERE s.archive_id = a.id AND s.uploadflag <> 0) AS \"scanPageCount\", "
            + "a.created_at AS \"createdAt\", "
            + "a.updated_at AS \"updatedAt\" "
            + "FROM mr_archive a ";

    @Select(DETAIL_SELECT + "WHERE a.id = #{id}")
    ArchiveRecord findById(@Param("id") Long id);

    @Select(DETAIL_SELECT
            + "WHERE a.id = app.resolve_archive_id(#{bah}, #{sjh}, FALSE)")
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
