package com.querypilot.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {

    @NotNull(message = "Connection ID is required")
    private Long connectionId;

    @NotBlank(message = "Question is required")
    private String question;

    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
