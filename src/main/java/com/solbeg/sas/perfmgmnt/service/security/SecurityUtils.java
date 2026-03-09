package com.solbeg.sas.perfmgmnt.service.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SecurityUtils {

    /**
     * Returns the current authenticated user's name (email) in lowercase,
     * or {@code null} when the request is anonymous (unauthenticated).
     *
     * <p>Spring Security always places an {@link AnonymousAuthenticationToken} in the context
     * for unauthenticated requests, so we distinguish by token type rather than
     * {@code isAuthenticated()} (which returns {@code true} for anonymous tokens).
     *
     * @return lowercase username, or {@code null} if the user is anonymous
     */
    public String getUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return StringUtils.lowerCase(authentication.getName());
    }
}
