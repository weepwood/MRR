package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.dto.req.ScanRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ScanMapper {
    @Select("select * from mr_scan where BAH = #{bah} ORDER BY pages")
    List<Scan> findBAH(@Param("bah") String bah);

    // 根据 ID 列表查询图片路径 - XML 实现
    List<PathDO> getImagePathList(@Param("ids") List<String> ids);

    @Update("UPDATE mr_scan SET btype = #{type} WHERE id = #{id}")
    int updateImageType(@Param("id") Integer id, @Param("type") Integer type);

    // 新增
    @Insert("INSERT INTO mr_scan (BRXH, BAH, filename, btype, pages, openerno, uploaddate, uploadflag, folder) " +
            "VALUES (#{brxh}, #{bah}, #{filename}, #{btype}, #{pages}, #{openerNo}, #{uploadDate}, #{uploadFlag}, #{folder})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Scan scan);

    // 删除
    @Update("UPDATE mr_scan SET uploadflag = 0 WHERE id = #{id} AND uploadflag <> 0")
    int softDeleteById(Integer id);

    // 动态更新 - XML 实现
    int update(Scan scan);

    // 查询所有
    @Select("SELECT * FROM mr_scan ORDER BY id")
    List<Scan> findAll();

    // 根据 ID 查询
    @Select("SELECT * FROM mr_scan WHERE id = #{id}")
    Scan findById(Integer id);

    // 根据病案号查询（不分页）
    @Select("SELECT * FROM mr_scan WHERE BAH = #{bah} ORDER BY pages")
    List<Scan> findByBah(@Param("bah") String bah);

    // 根据病人序号查询
    @Select("SELECT * FROM mr_scan WHERE BRXH = #{brxh} ORDER BY id")
    List<Scan> findByBrxh(@Param("brxh") String brxh);

    // 分页查询
    @Select("SELECT * FROM mr_scan ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<Scan> findAllWithPagination(@Param("offset") int offset, @Param("limit") int limit);

    // 根据条件动态查询 - XML 实现
    List<Scan> findByCondition(ScanRequest request);

    // 根据条件动态查询（带分页）- XML 实现
    List<Scan> findByConditionWithPagination(@Param("request") ScanRequest request, @Param("offset") int offset, @Param("limit") int limit);

    // 根据条件统计数量 - XML 实现
    int countByCondition(@Param("request") ScanRequest request);

    // OSS migration methods
    @Update("UPDATE mr_scan SET oss_url = #{ossUrl}, file_size = #{fileSize}, " +
            "checksum_md5 = #{checksumMd5}, migration_status = #{migrationStatus}, " +
            "migrated_at = NOW() WHERE id = #{id}")
    int updateOssInfo(@Param("id") Integer id, @Param("ossUrl") String ossUrl,
                      @Param("fileSize") Long fileSize, @Param("checksumMd5") String checksumMd5,
                      @Param("migrationStatus") String migrationStatus);

    @Select("SELECT * FROM mr_scan WHERE uploadflag != 0 AND " +
            "(oss_url IS NULL OR oss_url = '') ORDER BY id LIMIT #{limit}")
    List<Scan> findPendingMigration(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM mr_scan WHERE migration_status = #{status}")
    long countByMigrationStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM mr_scan WHERE uploadflag != 0")
    long countTotalUploadedScans();
}
