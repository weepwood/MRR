package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.DataQualityRepairOrderService;
import com.zjcxph.imgapi.utils.AuthContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system/data-quality/repair-orders")
@RequirePermissions({"system:manage","record:manage"})
public class DataQualityRepairOrderController {
    private final DataQualityRepairOrderService service;
    public DataQualityRepairOrderController(DataQualityRepairOrderService service) { this.service = service; }

    @GetMapping("/{id}")
    public Result<DataQualityRepairOrderService.Order> get(@PathVariable long id, HttpServletResponse response) {
        noStore(response);
        return Result.success(service.get(id));
    }

    @PostMapping
    public Result<DataQualityRepairOrderService.Order> propose(@RequestParam long issueId, HttpServletResponse response) {
        noStore(response);
        return Result.success(service.propose(issueId,actor()));
    }

    @PostMapping("/{id}/approve")
    public Result<DataQualityRepairOrderService.Order> approve(@PathVariable long id,HttpServletResponse response) {
        noStore(response);
        return Result.success(service.approve(id,actor()));
    }

    @PostMapping("/{id}/apply")
    public Result<DataQualityRepairOrderService.Order> apply(@PathVariable long id,HttpServletResponse response) {
        noStore(response);
        return Result.success(service.apply(id,actor()));
    }

    @PostMapping("/{id}/verify")
    public Result<DataQualityRepairOrderService.Order> verify(@PathVariable long id,HttpServletResponse response) {
        noStore(response);
        return Result.success(service.verify(id,actor()));
    }

    @PostMapping("/{id}/cancel")
    public Result<DataQualityRepairOrderService.Order> cancel(@PathVariable long id,HttpServletResponse response) {
        noStore(response);
        return Result.success(service.cancel(id,actor()));
    }

    @PostMapping("/{id}/compensate")
    public Result<DataQualityRepairOrderService.Order> compensate(@PathVariable long id,HttpServletResponse response) {
        noStore(response);
        return Result.success(service.compensate(id,actor()));
    }

    private static void noStore(HttpServletResponse response) { response.setHeader("Cache-Control","private, no-store"); }
    private static long actor() {
        var session=AuthContext.getCurrentUser();
        if(session==null || session.getId()==null) throw new BusinessException(401,"未登录");
        return session.getId();
    }
}
