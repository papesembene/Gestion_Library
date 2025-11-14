package com.example.demo.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.UniqueElements;

public class RoleDto {
    Long id;

    @NotBlank(message="Le nom est obligatoire !")
    @UniqueElements(message = "le nom doit etre unique")
    String nom;



}
