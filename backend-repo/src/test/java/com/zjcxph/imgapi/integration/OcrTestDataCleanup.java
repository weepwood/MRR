package com.zjcxph.imgapi.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Remove only a test's UUID-named synthetic archive; never truncate shared test tables. */
public final class OcrTestDataCleanup {
    private OcrTestDataCleanup() {}
    public static void purge(JdbcTemplate jdbc,PlatformTransactionManager manager,Long archive,String batch) {
        if(archive==null) return;
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            String code=jdbc.queryForObject("SELECT bah FROM app.mr_archive WHERE id=?",String.class,archive);
            if(code==null||!code.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
                throw new IllegalStateException("Refusing cleanup of non-synthetic archive");
            jdbc.update("DELETE FROM app.mr_image_type_audit WHERE scan_id IN (SELECT id FROM app.mr_scan WHERE archive_id=?)",archive);
            jdbc.update("DELETE FROM app.mr_smart_classification_job WHERE archive_id=?",archive);
            jdbc.update("DELETE FROM app.mr_scan_ocr_history WHERE scan_id IN (SELECT id FROM app.mr_scan WHERE archive_id=?)",archive);
            jdbc.update("DELETE FROM app.mr_scan WHERE archive_id=?",archive);
            jdbc.update("DELETE FROM app.mr_archive WHERE id=?",archive);
            if(batch!=null) jdbc.update("DELETE FROM app.mr_ocr_batch WHERE id=?",batch);
        });
    }
}
