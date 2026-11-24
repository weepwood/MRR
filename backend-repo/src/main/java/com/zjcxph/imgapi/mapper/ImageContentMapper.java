package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.Scan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface ImageContentMapper {

    @Select("SELECT * FROM mr_scan WHERE id = #{id} AND uploadflag != 0")
    Scan findActiveById(@Param("id") Integer id);

    // Exact stored path fields; existing folder/filename index, no unbounded result or alias expansion.
    @Select("SELECT * FROM mr_scan WHERE folder = #{folder} AND filename = #{filename} "
            + "AND bah = #{bah} AND brxh = #{brxh} AND uploadflag != 0 LIMIT 2")
    @Options(timeout = 3)
    List<Scan> findActiveLegacyCandidates(@Param("bah") String bah, @Param("brxh") String brxh,
                                          @Param("folder") String folder, @Param("filename") String filename);
}
