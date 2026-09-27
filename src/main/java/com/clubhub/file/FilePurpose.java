package com.clubhub.file;

import java.util.Map;
import java.util.Set;

/** What a file is for decides which types are allowed. Only images for now (posters, later logos). */
public enum FilePurpose {
    EVENT_POSTER(Map.of("image/png", "png", "image/jpeg", "jpg", "image/webp", "webp"));

    private final Map<String, String> extensionByContentType;

    FilePurpose(Map<String, String> extensionByContentType) {
        this.extensionByContentType = extensionByContentType;
    }

    public boolean allows(String contentType) {
        return extensionByContentType.containsKey(contentType);
    }

    public String extensionFor(String contentType) {
        return extensionByContentType.get(contentType);
    }

    public Set<String> allowedContentTypes() {
        return extensionByContentType.keySet();
    }
}
