package com.ipo.tracker.service;

import com.ipo.tracker.client.NseLiveIpoClient;
import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.model.IpoStatus;
import com.ipo.tracker.model.IpoType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class IpoService {

    private final NseLiveIpoClient nseLiveClient;

    public IpoService(NseLiveIpoClient nseLiveClient) {
        this.nseLiveClient = nseLiveClient;
    }

    /**
     * Get all real-time IPOs currently available to apply as of today directly from NSE India.
     */
    public List<Ipo> getActiveIpos() {
        return nseLiveClient.getLiveActiveIpos();
    }

    public List<Ipo> getActiveIpos(LocalDate asOfDate) {
        return nseLiveClient.getLiveActiveIpos();
    }

    /**
     * Get real-time forthcoming/upcoming IPOs directly from NSE India.
     */
    public List<Ipo> getUpcomingIpos() {
        return nseLiveClient.getLiveUpcomingIpos();
    }

    /**
     * Get real-time recently closed IPOs directly from NSE India.
     */
    public List<Ipo> getClosedIpos() {
        return nseLiveClient.getLivePastIpos();
    }

    /**
     * Get all IPOs across active, upcoming, and closed with optional filtering.
     */
    public List<Ipo> getAllIpos(IpoStatus status, IpoType type, String query) {
        List<Ipo> combined = new ArrayList<>();
        combined.addAll(getActiveIpos());
        combined.addAll(getUpcomingIpos());
        combined.addAll(getClosedIpos());

        return combined.stream()
                .filter(ipo -> status == null || ipo.status() == status)
                .filter(ipo -> type == null || ipo.issueType() == type)
                .filter(ipo -> query == null || query.isBlank() ||
                        ipo.companyName().toLowerCase().contains(query.toLowerCase()) ||
                        ipo.symbol().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Find single IPO by ID or symbol.
     */
    public Optional<Ipo> getIpoById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String cleanId = id.trim().toLowerCase();

        return getAllIpos(null, null, null).stream()
                .filter(ipo -> ipo.id().equalsIgnoreCase(cleanId) || ipo.symbol().equalsIgnoreCase(cleanId))
                .findFirst();
    }
}
