package com.ipo.tracker.controller;

import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.model.IpoApiResponse;
import com.ipo.tracker.model.IpoStatus;
import com.ipo.tracker.model.IpoType;
import com.ipo.tracker.service.IpoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ipos")
@CrossOrigin(origins = "*")
public class IpoController {

    private final IpoService ipoService;

    public IpoController(IpoService ipoService) {
        this.ipoService = ipoService;
    }

    /**
     * Primary endpoint:
     * Returns list of IPOs currently available to apply as of today.
     * GET /api/v1/ipos/active
     */
    @GetMapping("/active")
    public ResponseEntity<IpoApiResponse<List<Ipo>>> getActiveIpos(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {

        LocalDate targetDate = asOfDate != null ? asOfDate : LocalDate.now();
        List<Ipo> activeIpos = ipoService.getActiveIpos(targetDate);

        return ResponseEntity.ok(IpoApiResponse.of(
                "Successfully fetched " + activeIpos.size() + " IPO(s) open to apply as of " + targetDate,
                activeIpos.size(),
                activeIpos
        ));
    }

    /**
     * Returns all IPOs with optional query filters.
     * GET /api/v1/ipos?status=OPEN&type=MAINBOARD&query=swiggy
     */
    @GetMapping
    public ResponseEntity<IpoApiResponse<List<Ipo>>> getAllIpos(
            @RequestParam(required = false) IpoStatus status,
            @RequestParam(required = false) IpoType type,
            @RequestParam(required = false) String query) {

        List<Ipo> ipos = ipoService.getAllIpos(status, type, query);
        return ResponseEntity.ok(IpoApiResponse.of(
                "Successfully retrieved " + ipos.size() + " IPO(s)",
                ipos.size(),
                ipos
        ));
    }

    /**
     * Returns upcoming IPOs opening in the future.
     * GET /api/v1/ipos/upcoming
     */
    @GetMapping("/upcoming")
    public ResponseEntity<IpoApiResponse<List<Ipo>>> getUpcomingIpos() {
        List<Ipo> upcoming = ipoService.getUpcomingIpos();
        return ResponseEntity.ok(IpoApiResponse.of(
                "Successfully retrieved " + upcoming.size() + " upcoming IPO(s)",
                upcoming.size(),
                upcoming
        ));
    }

    /**
     * Returns details of a specific IPO by ID or symbol.
     * GET /api/v1/ipos/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<IpoApiResponse<Ipo>> getIpoById(@PathVariable String id) {
        return ipoService.getIpoById(id)
                .map(ipo -> ResponseEntity.ok(IpoApiResponse.of("IPO found", ipo)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
