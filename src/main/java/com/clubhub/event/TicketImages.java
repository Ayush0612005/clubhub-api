package com.clubhub.event;

import com.clubhub.event.EventDtos.TicketResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** HTTP response for a ticket QR image. */
final class TicketImages {

    private static final int SIZE_PX = 400;

    private TicketImages() {
    }

    /** no-store: a ticket is a personal credential and must not sit in shared/proxy caches. */
    static ResponseEntity<byte[]> png(TicketResponse ticket) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(QrCodes.png(ticket.ticket(), SIZE_PX));
    }
}
