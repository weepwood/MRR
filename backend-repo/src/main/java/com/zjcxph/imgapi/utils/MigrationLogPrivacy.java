package com.zjcxph.imgapi.utils;

import com.zjcxph.imgapi.entity.ImageMigrationLog;

/** Output projection only. Do not overwrite retained diagnostic evidence in the database. */
public final class MigrationLogPrivacy {
    private MigrationLogPrivacy(){}
    public static void sanitize(ImageMigrationLog log){
        if(log==null)return;
        String source=log.getLocalPath();
        if(source!=null&&!source.matches("(AUTO|LOCAL|NAS|NGINX|OSS|HTTP):scan:[0-9]{1,10}"))
            log.setLocalPath("[SOURCE OMITTED]");
        if(log.getErrorMessage()!=null&&!log.getErrorMessage().isBlank())
            log.setErrorMessage("迁移诊断已保护，请按记录ID核对");
    }
}
