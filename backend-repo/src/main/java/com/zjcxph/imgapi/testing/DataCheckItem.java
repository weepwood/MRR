package com.zjcxph.imgapi.testing;

import java.util.List;

public class DataCheckItem {
    private String checkName;
    private String status;
    private long issueCount;
    private String summary;
    private List<String> details;

    public String getCheckName() { return checkName; }
    public void setCheckName(String checkName) { this.checkName = checkName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getIssueCount() { return issueCount; }
    public void setIssueCount(long issueCount) { this.issueCount = issueCount; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<String> getDetails() { return details; }
    public void setDetails(List<String> details) { this.details = details; }
}
