package com.coresolution.pe.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ReleaseWindow {
    private Long id;
    private Integer evalYear;
    private String dataType; // AA, AB ...
    private String dataEv; // A, D, E ...
    private String cName;
    private String subCode;
    private LocalDateTime openAt;
    private LocalDateTime closeAt;
    private Integer enabled;
    private String delYn;
}