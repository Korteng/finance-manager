package ru.korteng.finance_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {

    @NotBlank @Size(min = 3, max = 100)
    private String username;

    @NotBlank @Size(min = 6, max = 100)
    private String password;
}
