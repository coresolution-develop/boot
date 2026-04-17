package com.coresolution.pe.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** JSON 바인딩용 요청 DTO */
public record CustomUpsertReq(
        @NotBlank String targetId,
        @Pattern(regexp = "[A-Z]") String dataEv,
        @Pattern(regexp = "AA|AB") String dataType,
        @NotNull Long formId,
        @Size(max = 200) String reason) {
}