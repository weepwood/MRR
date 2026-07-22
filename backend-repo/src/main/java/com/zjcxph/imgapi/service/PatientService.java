package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.req.PatientUpdateRequest;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.mapper.SearchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 患者信息维护服务。
 */
@Service
public class PatientService {

    private static final DateTimeFormatter NORMALIZED_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-M-d H:m"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            NORMALIZED_DATE_TIME
    );

    private final SearchMapper searchMapper;

    public PatientService(SearchMapper searchMapper) {
        this.searchMapper = searchMapper;
    }

    @Transactional
    public Patient update(Integer id, PatientUpdateRequest request) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("患者 ID 无效");
        }
        if (searchMapper.findPatientById(id) == null) {
            return null;
        }

        String bah = normalizeText(request.getBah());
        if (bah == null) {
            throw new IllegalArgumentException("病案号不能为空");
        }
        if (looksLikeScientificNotation(bah)) {
            throw new IllegalArgumentException("病案号疑似被表格软件转换为科学计数法");
        }

        String idCard = normalizeText(request.getIdCard());
        if (idCard != null && looksLikeScientificNotation(idCard)) {
            throw new IllegalArgumentException("身份证号疑似被表格软件转换为科学计数法");
        }

        Patient patient = new Patient();
        patient.setId(id);
        patient.setBah(bah);
        patient.setName(normalizeText(request.getName()));
        patient.setIdCard(idCard);
        patient.setRuyuan(request.getRuyuan());
        patient.setAdmissiontime(normalizeAdmissionTime(request.getAdmissiontime()));
        patient.setDepartment(normalizeText(request.getDepartment()));
        patient.setBingqu(normalizeText(request.getBingqu()));
        patient.setChuangwei(normalizeText(request.getChuangwei()));

        int updated = searchMapper.updatePatient(patient);
        return updated == 0 ? null : searchMapper.findPatientById(id);
    }

    private String normalizeAdmissionTime(String rawValue) {
        String value = normalizeText(rawValue);
        if (value == null) {
            return null;
        }
        String normalized = value.replace('/', '-').replace('.', '-').replace('T', ' ');
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter).format(NORMALIZED_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种受支持格式。
            }
        }
        throw new IllegalArgumentException("入院时间必须是 YYYY-MM-DD HH:MM 或 YYYY-MM-DD HH:MM:SS");
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean looksLikeScientificNotation(String value) {
        return value.matches("[+-]?\\d+(?:\\.\\d+)?[eE][+-]?\\d+");
    }
}
