package com.cambers.auth.repository;

import com.cambers.auth.entity.Role;
import com.cambers.auth.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, RoleName> {
}
