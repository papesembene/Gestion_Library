package com.example.demo.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    Long id;

    @NotBlank(message="Le nom est obligatoire !")
    String nom;

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est requis")
    String email;

    @NotBlank(message = "Le mot de passe est requis")
    String password;

    @NotNull(message = "Le rôle est obligatoire !")
    Long roleId;
}
