package com.example.demo.service;

import com.example.demo.exception.RoleNotFoundException;
import com.example.demo.model.entity.Role;
import com.example.demo.repository.RoleRepository;
import com.example.demo.service.interfaces.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;

    @Override
    public Role getByName(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleName));
    }
}