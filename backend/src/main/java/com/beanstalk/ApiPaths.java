package com.beanstalk;

/** Path clean-up used by {@link AuthFilter}. Kept separate so it can be unit-tested without a servlet container. */
final class ApiPaths {
    private ApiPaths() {}

    /** Mirror how Spring matches paths (path parameters and duplicate slashes ignored) so none of them can dodge the check. */
    static String normalise(String uri) {
        String p = uri.replaceAll(";[^/]*", "").replaceAll("/{2,}", "/");
        return p.isEmpty() ? "/" : p;
    }
}
