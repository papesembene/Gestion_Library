package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Builder
@Getter
@Setter
@NoArgsConstructor // Requis par JPA pour avoir un constructeur sans arguments
@AllArgsConstructor // Nécessaire avec @Builder pour la bonne génération des constructeurs
public class User {
    @Id // Suppression des parenthèses
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Renommé en "id" pour suivre la convention camelCase

    @Column(name = "nom", length = 30, nullable = false)
    private String nomComplet;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @ManyToOne(fetch = FetchType.LAZY) // Type de fetch recommandé pour la performance
    @JoinColumn(name = "role_id")
    private Role role;
}
