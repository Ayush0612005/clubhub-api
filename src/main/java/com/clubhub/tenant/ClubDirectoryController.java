package com.clubhub.tenant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/** Directory of active clubs for any logged-in student ("explore clubs"). Only public fields. */
@RestController
public class ClubDirectoryController {

    public record ClubCard(String slug, String name) {
    }

    private final TenantRepository tenants;

    public ClubDirectoryController(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @GetMapping("/api/clubs")
    public List<ClubCard> directory() {
        return tenants.findAllByStatus(TenantStatus.ACTIVE).stream()
                .map(t -> new ClubCard(t.getSlug(), t.getName()))
                .sorted(Comparator.comparing(ClubCard::name))
                .toList();
    }
}
