package com.orangetv.security;

import com.orangetv.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String role;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.role = user.getRole();
        this.enabled = user.getEnabled();
        this.authorities = buildAuthorities(user);
    }

    private Collection<? extends GrantedAuthority> buildAuthorities(User user) {
        Stream<String> roleStream = Stream.of("ROLE_" + user.getRole().toUpperCase());

        // 如果是 owner，也添加 admin 权限
        if ("owner".equals(user.getRole())) {
            roleStream = Stream.concat(roleStream, Stream.of("ROLE_ADMIN"));
        }

        // 添加用户组权限
        Stream<String> groupStream = user.getGroups().stream()
                .map(group -> "GROUP_" + group.getName().toUpperCase());

        return Stream.concat(roleStream, groupStream)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isOwner() {
        return "owner".equals(role);
    }

    public boolean isAdmin() {
        return "admin".equals(role) || isOwner();
    }
}
