package com.orangetv.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private boolean ok;
    private String token;
    private String username;
    private String role;
    private String avatar;
    private boolean machineCodeBound;
    private Long expiresIn;
}
