package com.zjcxph.imgapi.service;

import java.util.List;
import java.util.Map;

public interface DataQualityService {

    Map<String, Object> getSummary();

    List<Map<String, Object>> getIssues(int limit);

    Map<String, Object> getIssue(long issueId);

    Map<String, Object> previewRepair(long issueId);

    Map<String, Object> runChecks(String triggeredBy);
}
