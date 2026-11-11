package com.beanstalk;

/**
 * What the signed session cookie says about the visitor.
 * passwordOk = got through the site-password gate; sub != null = also signed in with Google.
 */
public record AuthSession(boolean passwordOk, String sub, String email, String name, String picture, long exp) {

    public static AuthSession anonymous() { return new AuthSession(false, null, null, null, null, 0); }

    public boolean signedIn() { return passwordOk && sub != null && !sub.isBlank(); }
}
