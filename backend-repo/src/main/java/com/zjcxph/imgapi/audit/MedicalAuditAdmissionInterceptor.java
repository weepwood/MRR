package com.zjcxph.imgapi.audit;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.utils.AuditPayloadSanitizer;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Set;

@Component
public class MedicalAuditAdmissionInterceptor implements AsyncHandlerInterceptor {
    private static final String ADMISSION=MedicalAuditAdmissionInterceptor.class.getName()+".id";
    private static final String ASYNC_FAILED=MedicalAuditAdmissionInterceptor.class.getName()+".asyncFailed";
    // Explicitly reviewed internal handlers. Public external routes have their own business audit.
    private static final Set<String> MEDICAL=Set.of("PatientController","PatientEditController","ImageController","ImageContentController",
            "ScanController","ArchiveRecordController","ArchiveBoxRecordController","ArchiveManifestController","ImageAuditExportController","ArchiveExportController","ArchiveExportJobController",
            "ArchiveExportJobListController","OcrController","OcrBatchController","DataExchangeController",
            "OssController","OssMigrationManagementController");
    // Narrow mandatory disclosure contract. No general audit engine or spool is started.
    private static final String DIRECT=MedicalAuditAdmissionInterceptor.class.getName()+".direct";
    private static final java.util.Map<String,Set<String>> DISCLOSURE=java.util.Map.ofEntries(
            java.util.Map.entry("ImageContentController",Set.of("getContent")),
            java.util.Map.entry("ImageController",Set.of("getImage","download","getImageUrl","getOssImage")),
            java.util.Map.entry("ArchiveExportController",Set.of("downloadZip","downloadPdf","downloadSelectedPdf")),
            java.util.Map.entry("ArchiveExportJobController",Set.of("download")),
            java.util.Map.entry("DataExchangeController",Set.of("exportPatients","exportStatistics","exportArchives","exportArchiveBoxes","exportScan")),
            java.util.Map.entry("PatientController",Set.of("getByIdCard","exportExcel")),
            java.util.Map.entry("SearchController",Set.of("getBAHByEncryptID","getBAHByEncryptIDLegacy","getArchiveCasesByIdCard","getArchiveCasesByToken","getBAHByiDCard","getBAHByIdCard")),
            java.util.Map.entry("StatisticsController",Set.of("exportCsv")),
            java.util.Map.entry("ImageAuditExportController",Set.of("export")),
            java.util.Map.entry("OssController",Set.of("getOssUrl","getBrowserOssUrl")),
            java.util.Map.entry("NginxBrowserController",Set.of("openFile")));
    private static final org.slf4j.Logger logger=org.slf4j.LoggerFactory.getLogger(MedicalAuditAdmissionInterceptor.class);
    private record Disclosure(String correlation,long actor,String trace,String method,String route,String target){}
    private final ObjectProvider<MedicalAuditAdmission> provider;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final org.springframework.transaction.support.TransactionTemplate transaction;
    public MedicalAuditAdmissionInterceptor(ObjectProvider<MedicalAuditAdmission> provider,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            org.springframework.transaction.PlatformTransactionManager manager){
        this.provider=provider;this.jdbc=jdbc;
        transaction=new org.springframework.transaction.support.TransactionTemplate(manager);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(3);
    }

    private boolean isDisclosure(HandlerMethod method) {
        return DISCLOSURE.getOrDefault(method.getBeanType().getSimpleName(),Set.of()).contains(method.getMethod().getName());
    }

    private boolean admitDisclosure(HttpServletRequest request,HttpServletResponse response)throws java.io.IOException {
        // Redispatch of an admitted stream must not create another INTENT.
        if(request.getAttribute(DIRECT) instanceof Disclosure)return true;
        try {
            Object actor=request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
            Object route=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            Object trace=request.getAttribute("requestId");
            if(!(actor instanceof AuthSession session)||session.getId()==null||session.getId()<=0
                    ||!(trace instanceof String id)||id.isBlank()||id.length()>64||route==null)
                throw new IllegalStateException("Medical audit identity unavailable");
            String query=java.util.Objects.toString(request.getQueryString(),"");
            String target=query.length()>16384?"[REDACTED]":
                    AuditPayloadSanitizer.protectedValue(request.getRequestURI()+"?"+query);
            var event=new Disclosure(java.util.UUID.randomUUID().toString(),session.getId(),id,request.getMethod(),route.toString(),target);
            persistDisclosure(event,false,0,false);
            request.setAttribute(DIRECT,event);
            return true;
        } catch(RuntimeException failure) {
            logger.error("Medical disclosure admission unavailable: errorType={}",failure.getClass().getSimpleName());
            response.setStatus(503);response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control","private, no-store");
            response.getWriter().write("{\"code\":503,\"message\":\"医疗审计暂不可用，请稍后重试\"}");
            return false;
        }
    }

    private void persistDisclosure(Disclosure e,boolean outcome,int status,boolean failed) {
        transaction.executeWithoutResult(tx->{
            int inserted=jdbc.update("""
                INSERT INTO app.medical_audit_event(event_id,correlation_id,request_id,phase,actor_id,method,route,target,observed_at,observation,http_status)
                VALUES (?,?,?,?,?,?,?,?,clock_timestamp(),?,?)
                """,e.correlation()+(outcome?":O":":I"),e.correlation(),e.trace(),outcome?"OUTCOME":"INTENT",
                    e.actor(),e.method(),e.route(),e.target(),outcome?(failed?"HTTP_EXCEPTION":"HTTP_COMPLETED"):"ADMITTED",
                    outcome?Integer.toString(status):null);
            if(inserted!=1)throw new IllegalStateException("Medical audit write not confirmed");
        });
    }


    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws Exception{
        if(handler instanceof HandlerMethod method && isDisclosure(method))
            return admitDisclosure(request,response);
        MedicalAuditAdmission service=provider.getIfAvailable();
        if(service==null||!(handler instanceof HandlerMethod method)||!MEDICAL.contains(method.getBeanType().getSimpleName())
                ||method.getMethod().getName().equals("hello"))return true;
        if(request.getAttribute(ADMISSION)!=null)return true; // async redispatch retains original reservation
        Object actor=request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        Object route=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Object trace=request.getAttribute("requestId");
        try{
            if(!(actor instanceof AuthSession session)||session.getId()==null||route==null||!(trace instanceof String id))
                throw new java.io.IOException("Medical audit identity unavailable");
            // No request body, raw URI, token, query or patient identifier stored. HMAC correlates the requested scope.
            String target=AuditPayloadSanitizer.protectedValue(request.getRequestURI()+"?"+java.util.Objects.toString(request.getQueryString(),""));
            String correlation=service.admit(session.getId(),id,request.getMethod(),route.toString(),target);
            request.setAttribute(ADMISSION,correlation);
            request.setAttribute(ASYNC_FAILED,new java.util.concurrent.atomic.AtomicBoolean());
            return true;
        }catch(java.io.IOException|RuntimeException failure){
            response.setStatus(503);response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control","private, no-store");
            response.getWriter().write("{\"code\":503,\"message\":\"医疗审计暂不可用，请稍后重试\"}");
            return false;
        }
    }
    @Override public void afterConcurrentHandlingStarted(HttpServletRequest request,HttpServletResponse response,Object handler){
        if(!(request.getAttribute(ADMISSION) instanceof String correlation))return;
        // MVC final redispatch normally completes the reservation. The listener also handles
        // an async completion whose redispatch is rejected before this interceptor is reached.
        var failure=(java.util.concurrent.atomic.AtomicBoolean)request.getAttribute(ASYNC_FAILED);
        if(failure==null)return;
        try{
            request.getAsyncContext().addListener(new jakarta.servlet.AsyncListener(){
                @Override public void onError(jakarta.servlet.AsyncEvent event){failure.set(true);}
                @Override public void onTimeout(jakarta.servlet.AsyncEvent event){failure.set(true);}
                @Override public void onStartAsync(jakarta.servlet.AsyncEvent event){event.getAsyncContext().addListener(this);}
                @Override public void onComplete(jakarta.servlet.AsyncEvent event){
                    var service=provider.getIfAvailable();
                    if(service!=null)service.complete(correlation,response.getStatus(),failure.get());
                }
            });
        }catch(IllegalStateException completed){
            // No fabricated outcome when the container has already ended async processing.
        }
    }
    @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception ex){
        if(request.getAttribute(DIRECT) instanceof Disclosure event){
            request.removeAttribute(DIRECT);
            try { persistDisclosure(event,true,response.getStatus(),ex!=null); }
            catch(RuntimeException failure) {
                // An acknowledged INTENT remains durable. No fabricated success or replay.
                logger.error("Medical disclosure outcome unavailable: errorType={}",failure.getClass().getSimpleName());
            }
            return;
        }
        if(request.getAttribute(ADMISSION) instanceof String correlation){
            request.removeAttribute(ADMISSION);
            var failure=(java.util.concurrent.atomic.AtomicBoolean)request.getAttribute(ASYNC_FAILED);
            request.removeAttribute(ASYNC_FAILED);
            MedicalAuditAdmission service=provider.getIfAvailable();
            if(service!=null)service.complete(correlation,response.getStatus(),ex!=null||(failure!=null&&failure.get()));
        }
    }
}
