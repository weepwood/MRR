package com.zjcxph.imgapi.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 病案影像类型的后端唯一事实源。
 *
 * <p>0 表示暂未分类，1-15 与现行业务字典保持一致。OCR 分类、人工改类、
 * API 返回与后续数据治理均应复用该定义，禁止在业务代码中再次硬编码范围。</p>
 */
public enum MedicalRecordType {

    UNCLASSIFIED(0, "00-暂未分类"),
    FRONT_PAGE(1, "01-病案首页"),
    PROGRESS_NOTE(2, "02-病程录"),
    OPERATION_RECORD(3, "03-手术记录"),
    POSTOPERATIVE_PROGRESS_NOTE(4, "04-术后病程录"),
    NURSING_RECORD(5, "05-护理记录"),
    CONSULTATION_FORM(6, "06-会诊单"),
    SPECIAL_EXAMINATION(7, "07-特殊检查"),
    LABORATORY_REPORT(8, "08-检验单"),
    MEDICAL_ORDER(9, "09-医嘱"),
    TEMPERATURE_CHART(10, "10-体温单"),
    NEWBORN_RECORD(11, "11-新生儿"),
    DISCHARGE_RECORD(12, "12-出院记录"),
    MAJOR_MEDICAL_HISTORY(13, "13-大病史"),
    OTHER(14, "14-其它"),
    DELIVERY_RECORD(15, "15-分娩记录");

    private final int code;
    private final String label;

    MedicalRecordType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<MedicalRecordType> fromCode(Integer code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.code == code)
                .findFirst();
    }

    public static boolean isSupported(Integer code) {
        return fromCode(code).isPresent();
    }

    public static List<MedicalRecordType> orderedValues() {
        return Arrays.stream(values())
                .sorted((left, right) -> Integer.compare(left.code, right.code))
                .toList();
    }
}
