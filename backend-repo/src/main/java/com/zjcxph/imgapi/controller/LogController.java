package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.pojo.Log;
import com.zjcxph.imgapi.pojo.Result;
import com.zjcxph.imgapi.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v2/logs")
public class LogController {

    @Autowired
    private LogService logService;

    @GetMapping("/")
    public Result<List<Log>> getAllLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Log> logs = logService.getAllLogs(page, size);
        int total = logService.getTotalLogCount();
        return new Result<>(200, "success", logs, total);
    }

    @GetMapping("/ip/{ip}")
    public Result<List<Log>> getLogsByClientIp(
            @PathVariable String ip,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Log> logs = logService.getLogsByClientIp(ip, page, size);
        int total = logService.getLogCountByClientIp(ip);
        return new Result<>(200, "success", logs, total);
    }

    @GetMapping("/uri")
    public Result<List<Log>> getLogsByRequestUri(
            @RequestParam String uri,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Log> logs = logService.getLogsByRequestUri(uri, page, size);
        int total = logService.getLogCountByRequestUri(uri);
        return new Result<>(200, "success", logs, total);
    }
}