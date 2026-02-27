package com.socialsea.model;

import java.util.Collections;
import java.util.Set;

public enum Role {

    USER(Collections.emptySet()),

    MODERATOR(Set.of(
        Permission.POST_REVIEW,
        Permission.REPORT_RESOLVE
    )),

    ADMIN(Set.of(
        Permission.USER_READ,
        Permission.USER_BAN,
        Permission.POST_REVIEW,
        Permission.REPORT_RESOLVE,
        Permission.DASHBOARD_VIEW
    )),

    SUPER_ADMIN(Set.of(Permission.values()));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}