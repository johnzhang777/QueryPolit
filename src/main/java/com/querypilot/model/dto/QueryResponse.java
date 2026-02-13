package com.querypilot.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryResponse {

    private String sql;
    private List<Map<String, Object>> result;
    private String safetyCheck;

    public String getSql() { return sql; }
    public List<Map<String, Object>> getResult() { return result; }
    public String getSafetyCheck() { return safetyCheck; }
    public void setSql(String sql) { this.sql = sql; }
    public void setResult(List<Map<String, Object>> result) { this.result = result; }
    public void setSafetyCheck(String safetyCheck) { this.safetyCheck = safetyCheck; }
}
