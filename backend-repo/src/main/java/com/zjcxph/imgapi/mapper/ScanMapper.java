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


    @Select("<script>" +
            "SELECT BRXH, BAH, folder, filename FROM mr_scan WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
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
    int deleteById(Integer id);

    // 更新
    @Update("<script>" +
            "UPDATE mr_scan " +
            "<set>" +
            "<if test='brxh != null'>BRXH = #{brxh},</if>" +
            "<if test='bah != null'>BAH = #{bah},</if>" +
            "<if test='filename != null'>filename = #{filename},</if>" +
            "<if test='btype != null'>btype = #{btype},</if>" +
            "<if test='pages != null'>pages = #{pages},</if>" +
            "<if test='openerNo != null'>openerno = #{openerNo},</if>" +
            "<if test='uploadDate != null'>uploaddate = #{uploadDate},</if>" +
            "<if test='uploadFlag != null'>uploadflag = #{uploadFlag},</if>" +
            "<if test='folder != null'>folder = #{folder},</if>" +
            "</set>" +
            "WHERE id = #{id}" +
            "</script>")
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

    // 根据条件动态查询
    @Select("<script>" +
            "SELECT * FROM mr_scan " +
            "<where>" +
            "<if test='brxh != null and brxh != \"\"'>AND BRXH LIKE '%' || #{brxh} || '%'</if>" +
            "<if test='bah != null and bah != \"\"'>AND BAH LIKE '%' || #{bah} || '%'</if>" +
            "<if test='filename != null and filename != \"\"'>AND filename LIKE '%' || #{filename} || '%'</if>" +
            "<if test='folder != null and folder != \"\"'>AND folder LIKE '%' || #{folder} || '%'</if>" +
            "<if test='openerNo != null and openerNo != \"\"'>AND openerno LIKE '%' || #{openerNo} || '%'</if>" +
            "<if test='uploadDate != null and uploadDate != \"\"'>AND uploaddate LIKE '%' || #{uploadDate} || '%'</if>" +
            "<if test='btype != null'>AND btype = #{btype}</if>" +
            "<if test='uploadFlag != null'>AND uploadflag = #{uploadFlag}</if>" +
            "<if test='pages != null'>AND pages = #{pages}</if>" +
            "</where>" +
            "ORDER BY id" +
            "</script>")
    List<Scan> findByCondition(ScanRequest request);

    @Select("<script>" +
            "SELECT * FROM mr_scan " +
            "<where>" +
            "<if test='request.brxh != null and request.brxh != \"\"'>AND BRXH LIKE '%' || #{request.brxh} || '%'</if>" +
            "<if test='request.bah != null and request.bah != \"\"'>AND BAH LIKE '%' || #{request.bah} || '%'</if>" +
            "<if test='request.filename != null and request.filename != \"\"'>AND filename LIKE '%' || #{request.filename} || '%'</if>" +
            "<if test='request.folder != null and request.folder != \"\"'>AND folder LIKE '%' || #{request.folder} || '%'</if>" +
            "<if test='request.openerNo != null and request.openerNo != \"\"'>AND openerno LIKE '%' || #{request.openerNo} || '%'</if>" +
            "<if test='request.uploadDate != null and request.uploadDate != \"\"'>AND uploaddate LIKE '%' || #{request.uploadDate} || '%'</if>" +
            "<if test='request.btype != null'>AND btype = #{request.btype}</if>" +
            "<if test='request.uploadFlag != null'>AND uploadflag = #{request.uploadFlag}</if>" +
            "<if test='request.pages != null'>AND pages = #{request.pages}</if>" +
            "</where>" +
            "ORDER BY id LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<Scan> findByConditionWithPagination(@Param("request") ScanRequest request, @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM mr_scan " +
            "<where>" +
            "<if test='request.brxh != null and request.brxh != \"\"'>AND BRXH LIKE '%' || #{request.brxh} || '%'</if>" +
            "<if test='request.bah != null and request.bah != \"\"'>AND BAH LIKE '%' || #{request.bah} || '%'</if>" +
            "<if test='request.filename != null and request.filename != \"\"'>AND filename LIKE '%' || #{request.filename} || '%'</if>" +
            "<if test='request.folder != null and request.folder != \"\"'>AND folder LIKE '%' || #{request.folder} || '%'</if>" +
            "<if test='request.openerNo != null and request.openerNo != \"\"'>AND openerno LIKE '%' || #{request.openerNo} || '%'</if>" +
            "<if test='request.uploadDate != null and request.uploadDate != \"\"'>AND uploaddate LIKE '%' || #{request.uploadDate} || '%'</if>" +
            "<if test='request.btype != null'>AND btype = #{request.btype}</if>" +
            "<if test='request.uploadFlag != null'>AND uploadflag = #{request.uploadFlag}</if>" +
            "<if test='request.pages != null'>AND pages = #{request.pages}</if>" +
            "</where>" +
            "</script>")
    int countByCondition(@Param("request") ScanRequest request);
}
