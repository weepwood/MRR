package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.resp.PatientAnalyticsSummary;
import com.zjcxph.imgapi.dto.resp.PatientMultiRecordGroup;
import com.zjcxph.imgapi.entity.Patient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PatientAnalyticsService {

    private static final String EVENT_DATE_EXPRESSION =
            "coalesce(ruyuan, app.try_parse_date(left(btrim(admissiontime), 10)))";

    private final JdbcTemplate jdbcTemplate;

    public PatientAnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PatientAnalyticsSummary getSummary(Integer requestedYear) {
        int currentYear = LocalDate.now().getYear();
        int year = requestedYear == null ? currentYear : requestedYear;
        if (year < 1900 || year > currentYear) {
            throw new IllegalArgumentException("统计年份必须在 1900 到 " + currentYear + " 之间");
        }

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = startDate.plusYears(1);

        long totalRecords = count("select count(*) from mr_patient");
        long totalArchives = count("""
                select count(distinct btrim(bah))
                from mr_patient
                where nullif(btrim(bah), '') is not null
                """);
        long yearArchives = count("""
                select count(distinct btrim(bah))
                from mr_patient
                where nullif(btrim(bah), '') is not null
                  and %s >= ?
                  and %s < ?
                """.formatted(EVENT_DATE_EXPRESSION, EVENT_DATE_EXPRESSION), startDate, endDate);
        long missingIdCardRecords = count("""
                select count(*)
                from mr_patient
                where nullif(btrim(idcard), '') is null
                """);
        long confirmedGroups = countConfirmedGroups();
        long suspectedGroups = countSuspectedGroups();

        List<PatientAnalyticsSummary.DateCount> dateCounts = loadDateCounts(year, startDate, endDate);
        List<PatientAnalyticsSummary.DepartmentCount> departmentCounts = loadDepartmentCounts(startDate, endDate);

        return new PatientAnalyticsSummary(
                year,
                totalRecords,
                totalArchives,
                yearArchives,
                missingIdCardRecords,
                confirmedGroups,
                suspectedGroups,
                dateCounts,
                departmentCounts
        );
    }

    public List<Patient> findMissingIdCardRecords(int page, int size) {
        int offset = (page - 1) * size;
        String sql = """
                select id, idcard, bah, name, ruyuan, admissiontime, department, bingqu, chuangwei
                from mr_patient
                where nullif(btrim(idcard), '') is null
                order by %s desc nulls last, id desc
                limit ? offset ?
                """.formatted(EVENT_DATE_EXPRESSION);
        return jdbcTemplate.query(sql, this::mapPatient, size, offset);
    }

    public long countMissingIdCardRecords() {
        return count("""
                select count(*)
                from mr_patient
                where nullif(btrim(idcard), '') is null
                """);
    }

    public List<PatientMultiRecordGroup> findMultiRecordGroups(
            int page,
            int size,
            boolean includeSuspected
    ) {
        int offset = (page - 1) * size;
        String sql = multiRecordGroupCte() + """
                select match_type, confidence, patient_name, idcard, record_count,
                       archive_count, archive_numbers, first_admission_date, last_admission_date
                from all_groups
                where match_type = 'IDCARD' or ?
                order by sort_order, archive_count desc, patient_name
                limit ? offset ?
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> mapMultiRecordGroup(resultSet), includeSuspected, size, offset);
    }

    public long countMultiRecordGroups(boolean includeSuspected) {
        String sql = multiRecordGroupCte() + """
                select count(*)
                from all_groups
                where match_type = 'IDCARD' or ?
                """;
        return count(sql, includeSuspected);
    }

    private List<PatientAnalyticsSummary.DateCount> loadDateCounts(
            int year,
            LocalDate startDate,
            LocalDate endDate
    ) {
        String sql = """
                select event_date, count(distinct bah) as archive_count
                from (
                    select btrim(bah) as bah, %s as event_date
                    from mr_patient
                    where nullif(btrim(bah), '') is not null
                ) patient_dates
                where event_date >= ? and event_date < ?
                group by event_date
                order by event_date
                """.formatted(EVENT_DATE_EXPRESSION);

        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        jdbcTemplate.query(sql, resultSet -> {
            LocalDate date = resultSet.getObject("event_date", LocalDate.class);
            counts.put(date, resultSet.getLong("archive_count"));
        }, startDate, endDate);

        LocalDate lastDate = year == LocalDate.now().getYear() ? LocalDate.now() : endDate.minusDays(1);
        List<PatientAnalyticsSummary.DateCount> result = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            result.add(new PatientAnalyticsSummary.DateCount(date.toString(), counts.getOrDefault(date, 0L)));
        }
        return result;
    }

    private List<PatientAnalyticsSummary.DepartmentCount> loadDepartmentCounts(
            LocalDate startDate,
            LocalDate endDate
    ) {
        String sql = """
                select coalesce(nullif(btrim(department), ''), '未填写') as department,
                       count(distinct btrim(bah)) as archive_count
                from mr_patient
                where nullif(btrim(bah), '') is not null
                  and %s >= ?
                  and %s < ?
                group by coalesce(nullif(btrim(department), ''), '未填写')
                order by archive_count desc, department
                limit 30
                """.formatted(EVENT_DATE_EXPRESSION, EVENT_DATE_EXPRESSION);
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> new PatientAnalyticsSummary.DepartmentCount(
                resultSet.getString("department"),
                resultSet.getLong("archive_count")
        ), startDate, endDate);
    }

    private long countConfirmedGroups() {
        return count("""
                select count(*)
                from (
                    select btrim(idcard)
                    from mr_patient
                    where nullif(btrim(idcard), '') is not null
                      and nullif(btrim(bah), '') is not null
                    group by btrim(idcard)
                    having count(distinct btrim(bah)) > 1
                ) confirmed_groups
                """);
    }

    private long countSuspectedGroups() {
        return count("""
                select count(*)
                from (
                    select lower(btrim(name))
                    from mr_patient
                    where nullif(btrim(idcard), '') is null
                      and nullif(btrim(name), '') is not null
                      and nullif(btrim(bah), '') is not null
                    group by lower(btrim(name))
                    having count(distinct btrim(bah)) > 1
                ) suspected_groups
                """);
    }

    private String multiRecordGroupCte() {
        return """
                with confirmed_groups as (
                    select
                        0 as sort_order,
                        'IDCARD' as match_type,
                        'HIGH' as confidence,
                        coalesce(
                            string_agg(distinct nullif(btrim(name), ''), ' / '
                                order by nullif(btrim(name), '')),
                            '未填写'
                        ) as patient_name,
                        btrim(idcard) as idcard,
                        count(*) as record_count,
                        count(distinct btrim(bah)) as archive_count,
                        string_agg(distinct btrim(bah), '、' order by btrim(bah)) as archive_numbers,
                        min(%s) as first_admission_date,
                        max(%s) as last_admission_date
                    from mr_patient
                    where nullif(btrim(idcard), '') is not null
                      and nullif(btrim(bah), '') is not null
                    group by btrim(idcard)
                    having count(distinct btrim(bah)) > 1
                ),
                suspected_groups as (
                    select
                        1 as sort_order,
                        'NAME_ONLY' as match_type,
                        'LOW' as confidence,
                        min(btrim(name)) as patient_name,
                        null::text as idcard,
                        count(*) as record_count,
                        count(distinct btrim(bah)) as archive_count,
                        string_agg(distinct btrim(bah), '、' order by btrim(bah)) as archive_numbers,
                        min(%s) as first_admission_date,
                        max(%s) as last_admission_date
                    from mr_patient
                    where nullif(btrim(idcard), '') is null
                      and nullif(btrim(name), '') is not null
                      and nullif(btrim(bah), '') is not null
                    group by lower(btrim(name))
                    having count(distinct btrim(bah)) > 1
                ),
                all_groups as (
                    select * from confirmed_groups
                    union all
                    select * from suspected_groups
                )
                """.formatted(
                EVENT_DATE_EXPRESSION,
                EVENT_DATE_EXPRESSION,
                EVENT_DATE_EXPRESSION,
                EVENT_DATE_EXPRESSION
        );
    }

    private Patient mapPatient(ResultSet resultSet, int rowNum) throws SQLException {
        return new Patient(
                resultSet.getInt("id"),
                resultSet.getString("idcard"),
                resultSet.getString("bah"),
                resultSet.getString("name"),
                resultSet.getObject("ruyuan", LocalDate.class),
                resultSet.getString("admissiontime"),
                resultSet.getString("department"),
                resultSet.getString("bingqu"),
                resultSet.getString("chuangwei")
        );
    }

    private PatientMultiRecordGroup mapMultiRecordGroup(ResultSet resultSet) throws SQLException {
        String archiveNumbers = resultSet.getString("archive_numbers");
        List<String> archiveNumberList = archiveNumbers == null || archiveNumbers.isBlank()
                ? List.of()
                : List.of(archiveNumbers.split("、"));
        return new PatientMultiRecordGroup(
                resultSet.getString("match_type"),
                resultSet.getString("confidence"),
                resultSet.getString("patient_name"),
                maskIdCard(resultSet.getString("idcard")),
                resultSet.getLong("record_count"),
                resultSet.getLong("archive_count"),
                archiveNumberList,
                resultSet.getObject("first_admission_date", LocalDate.class),
                resultSet.getObject("last_admission_date", LocalDate.class)
        );
    }

    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return null;
        }
        String value = idCard.trim();
        if (value.length() <= 7) {
            return "***";
        }
        return value.substring(0, 3)
                + "*".repeat(value.length() - 7)
                + value.substring(value.length() - 4);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
