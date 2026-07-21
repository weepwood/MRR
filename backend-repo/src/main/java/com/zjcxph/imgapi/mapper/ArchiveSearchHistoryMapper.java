package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.ArchiveSearchHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArchiveSearchHistoryMapper {

    @Insert("""
            INSERT INTO mr_archive_search_history
                (user_id, bah, sjh, success, image_count, query_count, failure_reason, favorite, searched_at)
            VALUES
                (#{userId}, #{bah}, #{sjh}, #{success}, #{imageCount}, #{queryCount}, #{failureReason}, #{favorite}, #{searchedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ArchiveSearchHistory history);

    @Select("""
            SELECT id, user_id AS userId, bah, sjh, success, image_count AS imageCount, query_count AS queryCount,
                   failure_reason AS failureReason, favorite, searched_at AS searchedAt,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM mr_archive_search_history
            WHERE user_id = #{userId}
            ORDER BY searched_at DESC, id DESC
            """)
    List<ArchiveSearchHistory> findByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE mr_archive_search_history
            SET favorite = #{favorite}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int updateFavorite(@Param("id") Long id, @Param("userId") Long userId, @Param("favorite") boolean favorite);
}
