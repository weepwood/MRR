package com.zjcxph.imgapi.utils;

import com.zjcxph.imgapi.entity.ImageMigrationLog;

/** Output projection only. Do not overwrite retained diagnostic evidence in the database. */
public final class MigrationLogPrivacy {
    private MigrationLogPrivacy(){}
    private static final java.util.Set<String> SAFE_CODES=java.util.Set.of(
            "INVALID_OSS_KEY","OBJECT_CONFLICT","MIGRATION_COMMIT_CONFLICT","SOURCE_FILE_UNAVAILABLE",
            "SOURCE_OR_OSS_TIMEOUT","SOURCE_OR_OSS_CONNECTION","SOURCE_OR_OSS_PERMISSION","SOURCE_OR_OSS_IO_FAILED",
            "MISSING_SJH","SOURCE_CHANGED","APPLICATION_RESTART");
    public static void sanitize(ImageMigrationLog log){
        if(log==null)return;
        String source=log.getLocalPath();
        if(source!=null&&!source.matches("(AUTO|LOCAL|NAS|NGINX|OSS|HTTP):scan:[0-9]{1,10}"))
            log.setLocalPath("[SOURCE OMITTED]");
        String message=log.getErrorMessage();
        if(message!=null&&!message.isBlank()){
            String code=message.startsWith("迁移失败：")?message.substring("迁移失败：".length()):
                    message.startsWith("迁移诊断：")?message.substring("迁移诊断：".length()):message;
            log.setErrorMessage(SAFE_CODES.contains(code)?"迁移诊断："+code:"迁移诊断已保护，请按记录ID核对");
        }
    }
    public static void sanitizeJob(com.zjcxph.imgapi.entity.MigrationJob job){
        if(job==null)return;
        if(job.getErrorMessage()!=null&&!job.getErrorMessage().isBlank())job.setErrorMessage("迁移诊断已保护，请按任务ID核对");
        if(job.getScopeValue()!=null&&!job.getScopeValue().isBlank())job.setScopeValue("[SCOPE OMITTED]");
    }
    /** Never mutate a mapper/cache-owned scan: only allow fields used by migration UI. */
    public static com.zjcxph.imgapi.entity.Scan migrationRecord(com.zjcxph.imgapi.entity.Scan source){
        var row=new com.zjcxph.imgapi.entity.Scan();
        row.setId(source.getId());row.setBah(source.getBah());row.setSjh(source.getSjh());
        row.setFilename(source.getFilename());row.setFolder(source.getFolder());
        row.setBtype(source.getBtype());row.setPages(source.getPages());row.setUploadDate(source.getUploadDate());
        row.setUploadFlag(source.getUploadFlag());row.setMigrationStatus(source.getMigrationStatus());
        row.setMigrationAttempts(source.getMigrationAttempts());
        String code=source.getMigrationErrorCode();
        row.setMigrationErrorCode(code!=null&&SAFE_CODES.contains(code)?code:null);
        row.setMigrationNextRetryAt(source.getMigrationNextRetryAt());row.setMigrationUpdatedAt(source.getMigrationUpdatedAt());
        return row;
    }
}
