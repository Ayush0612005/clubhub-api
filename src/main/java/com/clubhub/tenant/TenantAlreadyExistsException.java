package com.clubhub.tenant;

public class TenantAlreadyExistsException extends RuntimeException {

    public TenantAlreadyExistsException(String slug) {
        super("Club with slug '" + slug + "' already exists");
    }
}
