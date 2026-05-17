package com.smarthire;

import com.smarthire.model.User;
import com.smarthire.security.JwtService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public class JwtTestHelper {

    public static String generateTestToken(JwtService jwtService, String username) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password("testpassword")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_RECRUITER")))
                .build();

        return jwtService.generateToken(userDetails);
    }

    public static String generateTestTokenForUser(JwtService jwtService, User user) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();

        return jwtService.generateToken(userDetails);
    }
}
