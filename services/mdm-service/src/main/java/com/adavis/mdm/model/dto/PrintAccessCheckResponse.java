package com.adavis.mdm.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintAccessCheckResponse {

    private boolean allowed;
    private String action;
    private String userId;
    private String module;
    private String reportType;
    private String message;
    private String source;
}

