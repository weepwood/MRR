package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.mapper.MigrationJobMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit offline maintenance only. SQL exclusion is not an object-store worker lease. */
@Component
public final class MigrationMaintenanceRecovery {
    private final JdbcTemplate jdbc;
    private final MigrationJobMapper jobs;
    private final ScanMapper scans;
    private final TransactionTemplate transaction;
    public record Recovered(int jobs,int scans) {}

    public MigrationMaintenanceRecovery(JdbcTemplate jdbc,MigrationJobMapper jobs,ScanMapper scans,
                                        PlatformTransactionManager manager){
        this.jdbc=jdbc;this.jobs=jobs;this.scans=scans;
        this.transaction=new TransactionTemplate(manager);
        transaction.setTimeout(30);
    }

    public Recovered recoverExclusive(){
        return transaction.execute(status->{
            jdbc.execute("SET LOCAL lock_timeout='2s'");
            jdbc.execute("SET LOCAL statement_timeout='25s'");
            // Obtain both SQL write exclusions before any mutation. All external workers
            // must already be stopped by the operator; these locks cannot fence remote PUTs.
            jdbc.execute("LOCK TABLE app.migration_job, app.mr_scan IN SHARE ROW EXCLUSIVE MODE NOWAIT");
            return new Recovered(jobs.interruptActiveJobs(),scans.recoverInterruptedMigrations());
        });
    }
}
