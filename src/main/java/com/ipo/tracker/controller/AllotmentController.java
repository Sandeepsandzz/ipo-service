package com.ipo.tracker.controller;

import com.ipo.tracker.model.AllotmentResult;
import com.ipo.tracker.model.IpoApiResponse;
import com.ipo.tracker.service.IpoAllotmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/allotment")
@CrossOrigin(origins = "*")
public class AllotmentController {

    private final IpoAllotmentService allotmentService;

    public AllotmentController(IpoAllotmentService allotmentService) {
        this.allotmentService = allotmentService;
    }

    /**
     * Check allotment status for a given symbol and PAN.
     * GET /api/v1/allotment/check?symbol=swiggy&pan=ABCDE1234F&label=Self
     */
    @GetMapping("/check")
    public ResponseEntity<IpoApiResponse<AllotmentResult>> checkAllotment(
            @RequestParam String symbol,
            @RequestParam String pan,
            @RequestParam(required = false) String label) {

        AllotmentResult result = allotmentService.checkAllotment(symbol, pan, label);
        return ResponseEntity.ok(IpoApiResponse.of(
                "Allotment query completed for symbol " + symbol,
                1,
                result
        ));
    }

    /**
     * Validate and mask a PAN card.
     * GET /api/v1/allotment/validate-pan?pan=ABCDE1234F
     */
    @GetMapping("/validate-pan")
    public ResponseEntity<IpoApiResponse<Map<String, Object>>> validatePan(
            @RequestParam String pan) {

        boolean valid = allotmentService.isValidPan(pan);
        String masked = valid ? allotmentService.maskPan(pan) : "INVALID";

        return ResponseEntity.ok(IpoApiResponse.of(
                valid ? "PAN is valid" : "Invalid PAN format",
                valid ? 1 : 0,
                Map.of("pan", masked, "isValid", valid)
        ));
    }
}
