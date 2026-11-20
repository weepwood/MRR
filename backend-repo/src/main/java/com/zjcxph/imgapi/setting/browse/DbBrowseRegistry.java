package com.zjcxph.imgapi.setting.browse;

import com.zjcxph.imgapi.setting.browse.catalog.AnalyticsCatalog;
import com.zjcxph.imgapi.setting.browse.catalog.OpsCatalog;
import com.zjcxph.imgapi.setting.browse.catalog.PlatformCatalog;
import com.zjcxph.imgapi.setting.browse.catalog.RecordCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 可浏览表的注册表，是可见性的唯一来源。
 *
 * <p>未在此登记的表一律不可见（fail closed）。表定义按领域放在
 * {@code setting.browse.catalog} 下的目录类里，本类只负责装配与容错：
 * 单张表定义非法时只禁用该表并告警，不阻断应用启动，避免运维功能拖垮业务启动。</p>
 *
 * <p>注意：{@link #register} 会吞掉定义错误，所以「少了一张表」不会让启动失败。
 * {@code DbBrowseRegistryTest} 用固定清单断言登记结果，把这种静默丢失变成测试失败。</p>
 */
@Component
public class DbBrowseRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DbBrowseRegistry.class);

    private final Map<String, DbBrowseTable> tables;

    public DbBrowseRegistry() {
        Map<String, DbBrowseTable> built = new LinkedHashMap<>();
        Stream.of(
                        PlatformCatalog.entries(),
                        RecordCatalog.entries(),
                        AnalyticsCatalog.entries(),
                        OpsCatalog.entries()
                )
                .flatMap(List::stream)
                .forEach(entry -> register(built, entry));
        this.tables = Collections.unmodifiableMap(built);
        logger.info("数据库浏览注册表已加载 {} 张表（启用 {} 张）",
                tables.size(), tables.values().stream().filter(DbBrowseTable::enabled).count());
    }

    private static void register(Map<String, DbBrowseTable> target, Supplier<DbBrowseTable> supplier) {
        try {
            DbBrowseTable table = supplier.get();
            if (target.putIfAbsent(table.key(), table) != null) {
                logger.error("数据库浏览注册表存在重复标识，已忽略后者: {}", table.key());
            }
        } catch (RuntimeException e) {
            logger.error("数据库浏览注册表定义无效，已禁用该表: {}", e.getMessage());
        }
    }

    public Optional<DbBrowseTable> find(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(tables.get(key.trim()));
    }

    public List<DbBrowseTable> all() {
        return List.copyOf(tables.values());
    }
}
