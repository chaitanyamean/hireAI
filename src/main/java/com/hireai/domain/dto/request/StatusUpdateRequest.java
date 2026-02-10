package com.hireai.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class StatusUpdateRequest {

    @NotBlank(message = "Status is required")
    private String status;
}
