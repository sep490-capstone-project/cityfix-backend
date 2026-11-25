package com.cityfix.backend.model.entity;

import com.cityfix.backend.model.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity implements Principal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserProfile userProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserAddress userAddress;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserDepartment> userDepartments = new ArrayList<>();

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @PrePersist
    @PreUpdate
    protected void syncSatelliteEntities() {
        if (userProfile != null && userProfile.getUser() != this) {
            userProfile.setUser(this);
        }
        if (userAddress != null && userAddress.getUser() != this) {
            userAddress.setUser(this);
        }
    }

    public void setUserProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
        if (userProfile != null && userProfile.getUser() != this) {
            userProfile.setUser(this);
        }
    }

    public void setUserAddress(UserAddress userAddress) {
        this.userAddress = userAddress;
        if (userAddress != null && userAddress.getUser() != this) {
            userAddress.setUser(this);
        }
    }

    private UserProfile ensureUserProfile() {
        if (this.userProfile == null) {
            this.userProfile = new UserProfile();
            this.userProfile.setUser(this);
        } else if (this.userProfile.getUser() != this) {
            this.userProfile.setUser(this);
        }
        return this.userProfile;
    }

    private UserAddress ensureUserAddress() {
        if (this.userAddress == null) {
            this.userAddress = new UserAddress();
            this.userAddress.setUser(this);
        } else if (this.userAddress.getUser() != this) {
            this.userAddress.setUser(this);
        }
        return this.userAddress;
    }

    // ─── Delegating Getters & Setters for UserProfile ─────────────────────────

    public String getFullName() {
        return userProfile != null ? userProfile.getFullName() : null;
    }

    public void setFullName(String fullName) {
        ensureUserProfile().setFullName(fullName);
    }

    public String getPhoneNumber() {
        return userProfile != null ? userProfile.getPhoneNumber() : null;
    }

    public void setPhoneNumber(String phoneNumber) {
        ensureUserProfile().setPhoneNumber(phoneNumber);
    }

    public Role getRole() {
        return userProfile != null ? userProfile.getRole() : null;
    }

    public void setRole(Role role) {
        ensureUserProfile().setRole(role);
    }

    // ─── Delegating Getters & Setters for UserAddress ─────────────────────────

    public String getProvince() {
        return userAddress != null ? userAddress.getProvince() : null;
    }

    public void setProvince(String province) {
        ensureUserAddress().setProvince(province);
    }

    public String getWard() {
        return userAddress != null ? userAddress.getWard() : null;
    }

    public void setWard(String ward) {
        ensureUserAddress().setWard(ward);
    }

    public String getAddress() {
        return userAddress != null ? userAddress.getAddress() : null;
    }

    public void setAddress(String address) {
        ensureUserAddress().setAddress(address);
    }

    // ─── Convenience In-Memory Helpers for N-N UserDepartment ─────────────────

    public Department getDepartment() {
        if (this.userDepartments == null || this.userDepartments.isEmpty()) {
            return null;
        }
        for (UserDepartment ud : this.userDepartments) {
            if (ud != null && ud.getDepartment() != null && !ud.getDepartment().isDeleted()) {
                return ud.getDepartment();
            }
        }
        for (UserDepartment ud : this.userDepartments) {
            if (ud != null && ud.getDepartment() != null) {
                return ud.getDepartment();
            }
        }
        return null;
    }

    public void setDepartment(Department department) {
        if (this.userDepartments == null) {
            this.userDepartments = new ArrayList<>();
        }
        if (department == null) {
            this.userDepartments.clear();
            return;
        }
        boolean alreadyExists = this.userDepartments.stream()
                .anyMatch(ud -> ud != null && ud.getDepartment() != null
                        && ((department.getDepartmentId() != null && department.getDepartmentId().equals(ud.getDepartment().getDepartmentId()))
                            || ud.getDepartment() == department));
        if (!alreadyExists) {
            this.userDepartments.clear();
            this.userDepartments.add(UserDepartment.builder()
                    .user(this)
                    .department(department)
                    .build());
        }
    }

    @Override
    public String getName() {
        return this.email;
    }

    public static class UserBuilder {
        private UserProfile ensureBuilderProfile() {
            if (this.userProfile == null) {
                this.userProfile = new UserProfile();
            }
            return this.userProfile;
        }

        private UserAddress ensureBuilderAddress() {
            if (this.userAddress == null) {
                this.userAddress = new UserAddress();
            }
            return this.userAddress;
        }

        public UserBuilder fullName(String fullName) {
            ensureBuilderProfile().setFullName(fullName);
            return this;
        }

        public UserBuilder phoneNumber(String phoneNumber) {
            ensureBuilderProfile().setPhoneNumber(phoneNumber);
            return this;
        }

        public UserBuilder role(Role role) {
            ensureBuilderProfile().setRole(role);
            return this;
        }

        public UserBuilder province(String province) {
            ensureBuilderAddress().setProvince(province);
            return this;
        }

        public UserBuilder ward(String ward) {
            ensureBuilderAddress().setWard(ward);
            return this;
        }

        public UserBuilder address(String address) {
            ensureBuilderAddress().setAddress(address);
            return this;
        }

        public UserBuilder department(Department department) {
            if (!this.userDepartments$set || this.userDepartments$value == null) {
                this.userDepartments$value = new ArrayList<>();
                this.userDepartments$set = true;
            }
            this.userDepartments$value.clear();
            if (department != null) {
                this.userDepartments$value.add(UserDepartment.builder()
                        .department(department)
                        .build());
            }
            return this;
        }

        public User build() {
            User user = new User(
                    this.userId,
                    this.userProfile,
                    this.userAddress,
                    this.userDepartments$set ? this.userDepartments$value : new ArrayList<>(),
                    this.email,
                    this.passwordHash,
                    this.status$set ? this.status$value : UserStatus.ACTIVE
            );
            if (user.userProfile != null) {
                user.userProfile.setUser(user);
            }
            if (user.userAddress != null) {
                user.userAddress.setUser(user);
            }
            if (user.userDepartments != null) {
                for (UserDepartment ud : user.userDepartments) {
                    if (ud != null && ud.getUser() == null) {
                        ud.setUser(user);
                    }
                }
            }
            return user;
        }
    }
}
