package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.dto.req.ArchiveBoxRecordRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveBoxGroupDTO;
import com.zjcxph.imgapi.dto.resp.ArchiveBoxSummaryDTO;
import com.zjcxph.imgapi.entity.ArchiveBoxRecord;
import com.zjcxph.imgapi.mapper.ArchiveBoxRecordMapper;
import com.zjcxph.imgapi.service.ArchiveBoxRecordService;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import com.zjcxph.imgapi.utils.PaginationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ArchiveBoxRecordServiceImpl implements ArchiveBoxRecordService {

    private static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_MISSING = "MISSING";
    private static final Set<String> SUPPORTED_STATUSES = Set.of(
            STATUS_NORMAL,
            STATUS_MISSING,
            "MISPLACED",
            "CONFLICT",
            "OTHER"
    );

    private final ArchiveBoxRecordMapper mapper;

    public ArchiveBoxRecordServiceImpl(ArchiveBoxRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ArchiveBoxRecord create(ArchiveBoxRecordRequest request) {
        ArchiveBoxRecord record = toEntity(null, request);
        ensureSjhNotDuplicated(record.getSjh(), null);
        mapper.insert(record);
        return mapper.findById(record.getId());
    }

    @Override
    @Transactional
    public ArchiveBoxRecord update(Long id, ArchiveBoxRecordRequest request) {
        ArchiveBoxRecord existing = mapper.findById(id);
        if (existing == null) {
            return null;
        }

        ArchiveBoxRecord record = toEntity(id, request);
        ensureSjhNotDuplicated(record.getSjh(), id);
        mapper.update(record);
        return mapper.findById(id);
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public ArchiveBoxRecord findById(Long id) {
        return mapper.findById(id);
    }

    @Override
    public List<ArchiveBoxRecord> findPage(
            int page,
            int size,
            String keyword,
            String bah,
            String sjh,
            String boxNo,
            String status,
            String sortBy,
            String sortOrder
    ) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return mapper.findPage(
                offset,
                size,
                trimToNull(keyword),
                normalizeCode(bah),
                searchCodeOrNull(bah),
                normalizeCode(sjh),
                searchCodeOrNull(sjh),
                trimToNull(boxNo),
                normalizeStatusFilter(status),
                normalizeSortBy(sortBy),
                normalizeSortOrder(sortOrder)
        );
    }

    @Override
    public long countPage(String keyword, String bah, String sjh, String boxNo, String status) {
        return mapper.countPage(
                trimToNull(keyword),
                normalizeCode(bah),
                searchCodeOrNull(bah),
                normalizeCode(sjh),
                searchCodeOrNull(sjh),
                trimToNull(boxNo),
                normalizeStatusFilter(status)
        );
    }

    @Override
    public List<ArchiveBoxRecord> findByRecordCode(String code) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode == null) {
            return List.of();
        }
        return mapper.findByRecordCode(normalizedCode, MedicalRecordCodeUtils.toSearchTerm(code));
    }

    @Override
    public List<ArchiveBoxRecord> findByBoxNo(String boxNo) {
        String normalizedBoxNo = trimToNull(boxNo);
        if (normalizedBoxNo == null) {
            return List.of();
        }
        return mapper.findByBoxNo(normalizedBoxNo);
    }

    @Override
    public ArchiveBoxSummaryDTO getSummary() {
        return mapper.getSummary();
    }

    @Override
    public List<ArchiveBoxGroupDTO> findBoxGroups(int page, int size, String keyword) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return mapper.findBoxGroups(offset, size, trimToNull(keyword));
    }

    @Override
    public long countBoxGroups(String keyword) {
        return mapper.countBoxGroups(trimToNull(keyword));
    }

    private ArchiveBoxRecord toEntity(Long id, ArchiveBoxRecordRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("装箱记录不能为空");
        }

        String bah = normalizeCode(request.getBah());
        String sjh = normalizeCode(request.getSjh());
        String boxNo = trimToNull(request.getBoxNo());
        String expectedBoxNo = trimToNull(request.getExpectedBoxNo());
        String status = normalizeStatus(request.getStatus());
        String remark = trimToNull(request.getRemark());

        if (bah == null && sjh == null) {
            throw new IllegalArgumentException("病案号和上架号至少填写一项");
        }
        if (!STATUS_MISSING.equals(status) && boxNo == null) {
            throw new IllegalArgumentException("非缺失状态必须填写实际箱号");
        }

        ArchiveBoxRecord record = new ArchiveBoxRecord();
        record.setId(id);
        record.setBah(bah);
        record.setSjh(sjh);
        record.setBoxNo(boxNo);
        record.setExpectedBoxNo(expectedBoxNo);
        record.setStatus(status);
        record.setRemark(remark);
        return record;
    }

    private void ensureSjhNotDuplicated(String sjh, Long currentId) {
        if (sjh == null) {
            return;
        }
        ArchiveBoxRecord duplicate = mapper.findBySjh(sjh);
        if (duplicate != null && !duplicate.getId().equals(currentId)) {
            throw new IllegalArgumentException("上架号已存在装箱记录");
        }
    }

    private String normalizeStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return STATUS_NORMAL;
        }

        String normalized = switch (value) {
            case "正常" -> STATUS_NORMAL;
            case "缺失" -> STATUS_MISSING;
            case "存放在其他箱子" -> "MISPLACED";
            case "正常(箱号冲突)", "正常（箱号冲突）" -> "CONFLICT";
            default -> value.toUpperCase(Locale.ROOT);
        };

        if (!SUPPORTED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的装箱状态");
        }
        return normalized;
    }

    private String normalizeStatusFilter(String status) {
        String value = trimToNull(status);
        return value == null ? null : normalizeStatus(value);
    }

    private String normalizeSortBy(String sortBy) {
        String value = trimToNull(sortBy);
        if (value == null) {
            return "updatedAt";
        }
        return switch (value) {
            case "bah", "sjh", "boxNo", "status", "createdAt", "updatedAt" -> value;
            default -> "updatedAt";
        };
    }

    private String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(trimToNull(sortOrder)) ? "asc" : "desc";
    }

    private String normalizeCode(String value) {
        String normalized = MedicalRecordCodeUtils.normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String searchCodeOrNull(String value) {
        String code = MedicalRecordCodeUtils.toSearchTerm(value);
        return code.isBlank() ? null : code;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
