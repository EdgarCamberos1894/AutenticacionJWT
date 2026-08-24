package com.cambers.auth.entity;

import com.cambers.auth.enums.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "name", length = 50, nullable = false, updatable = false)
    private RoleName name;

    protected Role() {
    }

    public Role(RoleName name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    public RoleName getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Role role)) {
            return false;
        }
        return name == role.name;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
