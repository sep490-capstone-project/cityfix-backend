package com.cityfix.backend.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true)
    private String phoneNumber;

    @Column(nullable = false)
    private String province;

    @Column(nullable = false)
    private String ward;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.cityfix.backend.model.enums.UserStatus status = com.cityfix.backend.model.enums.UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.cityfix.backend.model.enums.UserRole role = com.cityfix.backend.model.enums.UserRole.RESIDENT;

    @Column(nullable = false)
    private boolean emailVerified = false;
}
