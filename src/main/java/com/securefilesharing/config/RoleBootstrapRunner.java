package com.securefilesharing.config;

import com.securefilesharing.entity.RoleDefinition;
import com.securefilesharing.repository.RoleDefinitionRepository;
import com.securefilesharing.security.Permission;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class RoleBootstrapRunner implements ApplicationRunner {

    private final RoleDefinitionRepository roleDefinitionRepository;

    public RoleBootstrapRunner(RoleDefinitionRepository roleDefinitionRepository) {
        this.roleDefinitionRepository = roleDefinitionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureRole("ROLE_SUPER_ADMIN", true, Set.of(
                Permission.ADMIN_ACCESS,
                Permission.USER_MANAGEMENT,
                Permission.ROLE_MANAGEMENT,
                Permission.VIEW_AUDIT_LOGS,
                Permission.REPORTS,
                Permission.SETTINGS_MANAGEMENT,
                Permission.FILE_DOWNLOAD,
                Permission.FILE_SHARE,
                Permission.FILE_UPLOAD
        ));

        ensureRole("ROLE_ADMIN", false, Set.of(
                Permission.ADMIN_ACCESS,
                Permission.USER_MANAGEMENT,
                Permission.VIEW_AUDIT_LOGS,
                Permission.REPORTS,
                Permission.SETTINGS_MANAGEMENT,
                Permission.FILE_DOWNLOAD,
                Permission.FILE_SHARE
        ));

        ensureRole("ROLE_AUDITOR", false, Set.of(
                Permission.ADMIN_ACCESS,
                Permission.VIEW_AUDIT_LOGS,
                Permission.REPORTS
        ));

        ensureRole("ROLE_USER", false, Set.of(
                Permission.FILE_UPLOAD,
                Permission.FILE_DOWNLOAD,
                Permission.FILE_SHARE
        ));
    }

    private void ensureRole(String name, boolean critical, Set<Permission> permissions) {
        if (roleDefinitionRepository.existsByName(name)) {
            return;
        }
        RoleDefinition role = new RoleDefinition();
        role.setName(name);
        role.setCritical(critical);

        LinkedHashSet<String> perms = new LinkedHashSet<>();
        for (Permission p : permissions) {
            perms.add(p.name());
        }
        role.setPermissions(perms);
        roleDefinitionRepository.save(role);
    }
}
