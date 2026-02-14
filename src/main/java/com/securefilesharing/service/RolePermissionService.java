package com.securefilesharing.service;

import com.securefilesharing.entity.RoleDefinition;
import com.securefilesharing.repository.RoleDefinitionRepository;
import com.securefilesharing.security.Permission;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class RolePermissionService {

    public static final String ROLE_PREFIX = "ROLE_";
    public static final String PERM_PREFIX = "PERM_";

    private final RoleDefinitionRepository roleDefinitionRepository;

    public RolePermissionService(RoleDefinitionRepository roleDefinitionRepository) {
        this.roleDefinitionRepository = roleDefinitionRepository;
    }

    public String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "ROLE_USER";
        }
        String name = roleName.trim().toUpperCase(Locale.ROOT);
        if (!name.startsWith(ROLE_PREFIX)) {
            name = ROLE_PREFIX + name;
        }
        return name;
    }

    public Set<String> getPermissionsForRole(String roleName) {
        String normalized = normalizeRoleName(roleName);
        return roleDefinitionRepository.findByName(normalized)
                .map(RoleDefinition::getPermissions)
                .map(LinkedHashSet::new)
                .orElseGet(LinkedHashSet::new);
    }

    public Collection<? extends GrantedAuthority> buildAuthorities(String roleName) {
        String normalizedRole = normalizeRoleName(roleName);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority(normalizedRole));

        for (String permission : getPermissionsForRole(normalizedRole)) {
            String perm = permission;
            if (perm == null || perm.isBlank()) continue;
            perm = perm.trim().toUpperCase(Locale.ROOT);
            if (!perm.startsWith(PERM_PREFIX)) {
                perm = PERM_PREFIX + perm;
            }
            authorities.add(new SimpleGrantedAuthority(perm));
        }

        // Ensure baseline permissions for USER role even if DB not initialized yet.
        if ("ROLE_USER".equals(normalizedRole) && authorities.size() == 1) {
            authorities.add(new SimpleGrantedAuthority(PERM_PREFIX + Permission.FILE_UPLOAD.name()));
            authorities.add(new SimpleGrantedAuthority(PERM_PREFIX + Permission.FILE_DOWNLOAD.name()));
            authorities.add(new SimpleGrantedAuthority(PERM_PREFIX + Permission.FILE_SHARE.name()));
        }

        return new ArrayList<>(authorities);
    }
}
