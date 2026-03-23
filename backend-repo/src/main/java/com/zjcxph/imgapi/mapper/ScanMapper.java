package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.pojo.PathDO;
import com.zjcxph.imgapi.pojo.Scan;
import com.zjcxph.imgapi.pojo.ScanRequest;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ScanMapper {
    @Select("select * from mr_scan where BAH = #{BAH} ORDER BY pages")
    List<Scan> findBAH(String bah);


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
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Scan scan);

    // 删除
    @Delete("DELETE FROM mr_scan WHERE id = #{id}")
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
    List<Scan> findByBah(String bah);

    // 根据病人序号查询
    @Select("SELECT * FROM mr_scan WHERE BRXH = #{brxh} ORDER BY id")
    List<Scan> findByBrxh(String brxh);

    // 分页查询
    @Select("SELECT * FROM mr_scan ORDER BY id LIMIT #{offset}, #{limit}")
    List<Scan> findAllWithPagination(@Param("offset") int offset, @Param("limit") int limit);

    // 根据条件动态查询
    @Select("<script>" +
            "SELECT * FROM mr_scan " +
            "<where>" +
            "<if test='brxh != null and brxh != \"\"'>AND BRXH LIKE CONCAT('%', #{brxh}, '%')</if>" +
            "<if test='bah != null and bah != \"\"'>AND BAH LIKE CONCAT('%', #{bah}, '%')</if>" +
            "<if test='folder != null and folder != \"\"'>AND folder LIKE CONCAT('%', #{folder}, '%')</if>" +
            "<if test='btype != null'>AND btype = #{btype}</if>" +
            "<if test='uploadFlag != null'>AND uploadflag = #{uploadFlag}</if>" +
            "</where>" +
            "ORDER BY id" +
            "</script>")
    List<Scan> findByCondition(ScanRequest request);
}
