package org.example.disciplinaryrecordsmanagement.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import static org.example.disciplinaryrecordsmanagement.constant.RequestConstants.LOGIN_AUTHORITY;

public final class SessionUser extends User {
    private final long passwordVersion;

    public SessionUser(String username, String hash, long passwordVersion) {
        super(username, hash, List.of(new SimpleGrantedAuthority("ROLE_" + LOGIN_AUTHORITY)));
        this.passwordVersion = passwordVersion;
    }

    public long passwordVersion() { return passwordVersion; }
}
