package com.adavis.mdm.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleUserCountDto {
    private String roleId;
    private String roleCode;
    private String label;
    private long value;
}
