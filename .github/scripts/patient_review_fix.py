from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"未找到预期内容：{path}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8", newline="\n")


replace_once(
    "frontend-fantastic-admin/src/views/patients/components/PatientImportDialog.vue",
    """  const example = '00789124,示例患者,330000199001011234,2026-07-01,2026-07-01 08:30,内科,一病区,12A'\n  const blob = new Blob([`\\uFEFF${header}\\r\\n${example}\\r\\n`], { type: 'text/csv;charset=utf-8' })""",
    """  const blob = new Blob([`\\uFEFF${header}\\r\\n`], { type: 'text/csv;charset=utf-8' })""",
)

replace_once(
    "backend-repo/src/main/java/com/zjcxph/imgapi/controller/PatientController.java",
    """import org.apache.poi.ss.usermodel.Workbook;\nimport org.apache.poi.xssf.usermodel.XSSFWorkbook;""",
    """import org.apache.poi.xssf.streaming.SXSSFWorkbook;""",
)

old_export = '''        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("患者列表");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("病案号");
            header.createCell(2).setCellValue("姓名");
            header.createCell(3).setCellValue("身份证号");
            header.createCell(4).setCellValue("科室");
            header.createCell(5).setCellValue("入院时间");

            for (int i = 0; i < patients.size(); i++) {
                Patient patient = patients.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(patient.getId() != null ? patient.getId() : 0);
                row.createCell(1).setCellValue(patient.getBah() != null ? patient.getBah() : "");
                row.createCell(2).setCellValue(patient.getName() != null ? patient.getName() : "");
                row.createCell(3).setCellValue(patient.getIdCard() != null ? patient.getIdCard() : "");
                row.createCell(4).setCellValue(patient.getDepartment() != null ? patient.getDepartment() : "");
                row.createCell(5).setCellValue(patient.getAdmissiontime() != null ? patient.getAdmissiontime() : "");
            }

            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=patients.xlsx");
            workbook.write(response.getOutputStream());
        }'''

new_export = '''        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook) {
            Sheet sheet = workbook.createSheet("患者列表");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("病案号");
            header.createCell(2).setCellValue("姓名");
            header.createCell(3).setCellValue("身份证号");
            header.createCell(4).setCellValue("入院日期");
            header.createCell(5).setCellValue("入院时间");
            header.createCell(6).setCellValue("科室");
            header.createCell(7).setCellValue("病区");
            header.createCell(8).setCellValue("床位");

            for (int i = 0; i < patients.size(); i++) {
                Patient patient = patients.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(patient.getId() != null ? patient.getId() : 0);
                row.createCell(1).setCellValue(patient.getBah() != null ? patient.getBah() : "");
                row.createCell(2).setCellValue(patient.getName() != null ? patient.getName() : "");
                row.createCell(3).setCellValue(patient.getIdCard() != null ? patient.getIdCard() : "");
                row.createCell(4).setCellValue(patient.getRuyuan() != null ? patient.getRuyuan().toString() : "");
                row.createCell(5).setCellValue(patient.getAdmissiontime() != null ? patient.getAdmissiontime() : "");
                row.createCell(6).setCellValue(patient.getDepartment() != null ? patient.getDepartment() : "");
                row.createCell(7).setCellValue(patient.getBingqu() != null ? patient.getBingqu() : "");
                row.createCell(8).setCellValue(patient.getChuangwei() != null ? patient.getChuangwei() : "");
            }

            int[] widths = {12, 18, 14, 24, 14, 22, 20, 18, 14};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=patients.xlsx");
            workbook.write(response.getOutputStream());
        } finally {
            workbook.dispose();
        }'''
replace_once(
    "backend-repo/src/main/java/com/zjcxph/imgapi/controller/PatientController.java",
    old_export,
    new_export,
)

replace_once(
    "backend-repo/src/main/java/com/zjcxph/imgapi/service/PatientImportService.java",
    """        return new PatientRow(bah, name, idCard, ruyuan, admissionTime, department, bingqu, chuangwei);""",
    """        return new PatientRow(rowNumber, bah, name, idCard, ruyuan, admissionTime, department, bingqu, chuangwei);""",
)

replace_once(
    "backend-repo/src/main/java/com/zjcxph/imgapi/service/PatientImportService.java",
    """        private final List<PatientImportError> errors = new ArrayList<>();
        private final Set<Integer> errorRows = new LinkedHashSet<>();
        private boolean truncated;""",
    """        private final List<PatientImportError> errors = new ArrayList<>();
        private final Set<Integer> errorRows = new LinkedHashSet<>();
        private int errorCount;
        private boolean truncated;""",
)

replace_once(
    "backend-repo/src/main/java/com/zjcxph/imgapi/service/PatientImportService.java",
    """        void add(int rowNumber, String field, String message, String rawValue) {
            errorRows.add(rowNumber);
            if (errors.size() >= MAX_REPORTED_ERRORS) {""",
    """        void add(int rowNumber, String field, String message, String rawValue) {
            errorCount++;
            errorRows.add(rowNumber);
            if (errors.size() >= MAX_REPORTED_ERRORS) {""",
)

replace_once(
    "backend-repo/src/main/java/com/zjcxph/imgapi/service/PatientImportService.java",
    """        int size() {
            return errors.size();
        }""",
    """        int size() {
            return errorCount;
        }""",
)

replace_once(
    "backend-repo/src/main/java/com/zjcxph/imgapi/service/PatientImportService.java",
    """    private record PatientRow(
            String bah,
            String name,
            String idCard,
            LocalDate ruyuan,
            String admissionTime,
            String department,
            String bingqu,
            String chuangwei
    ) {
        private static int nextSequence = 0;
        private final static Object SEQUENCE_LOCK = new Object();

        int sequence() {
            synchronized (SEQUENCE_LOCK) {
                return ++nextSequence;
            }
        }

        String fingerprint() {""",
    """    private record PatientRow(
            int sequence,
            String bah,
            String name,
            String idCard,
            LocalDate ruyuan,
            String admissionTime,
            String department,
            String bingqu,
            String chuangwei
    ) {
        String fingerprint() {""",
)

test_path = "backend-repo/src/test/java/com/zjcxph/imgapi/unit/service/PatientImportServiceTest.java"
test_marker = """    private MockMultipartFile csv(String content, Charset charset) {"""
test_block = '''    @Test
    @DisplayName("错误超过展示上限时仍按真实错误行计数")
    void countsErrorsBeyondDisplayLimit() throws Exception {
        StringBuilder content = new StringBuilder(
                "bah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei\\n"
        );
        for (int i = 0; i < 205; i++) {
            content.append(",错误行,,,,,,\\n");
        }
        content.append("00789508,有效患者,,,,,,\\n");
        MockMultipartFile file = csv(content.toString(), StandardCharsets.UTF_8);

        PatientImportResult result = patientImportService.importPatients(file, true);

        assertThat(result.canImport()).isFalse();
        assertThat(result.errorRows()).isEqualTo(205);
        assertThat(result.validRows()).isEqualTo(1);
        assertThat(result.errors()).hasSize(200);
        assertThat(result.errorsTruncated()).isTrue();
        verifyNoInteractions(jdbcTemplate);
    }

'''
replace_once(test_path, test_marker, test_block + test_marker)
