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
    private final ObjectProvider<MedicalAuditAdmission> provider;
    public MedicalAuditAdmissionInterceptor(ObjectProvider<MedicalAuditAdmission> provider){this.provider=provider;}

    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws Exception{
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
        if(request.getAttribute(ADMISSION) instanceof String correlation){
            request.removeAttribute(ADMISSION);
            var failure=(java.util.concurrent.atomic.AtomicBoolean)request.getAttribute(ASYNC_FAILED);
            request.removeAttribute(ASYNC_FAILED);
            MedicalAuditAdmission service=provider.getIfAvailable();
            if(service!=null)service.complete(correlation,response.getStatus(),ex!=null||(failure!=null&&failure.get()));
        }
    }
}
