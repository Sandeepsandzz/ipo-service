package com.ipo.tracker.service;

import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.model.IpoStatus;
import com.ipo.tracker.model.IpoType;
import com.ipo.tracker.repository.IpoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class IpoService {

    private final IpoRepository ipoRepository;

    public IpoService(IpoRepository ipoRepository) {
        this.ipoRepository = ipoRepository;
    }

    public List<Ipo> getActiveIpos() {
        return ipoRepository.findActive(LocalDate.now());
    }

    public List<Ipo> getActiveIpos(LocalDate asOfDate) {
        return ipoRepository.findActive(asOfDate != null ? asOfDate : LocalDate.now());
    }

    public List<Ipo> getUpcomingIpos() {
        return ipoRepository.findUpcoming(LocalDate.now());
    }

    public List<Ipo> getClosedIpos() {
        return ipoRepository.findClosed(LocalDate.now());
    }

    public List<Ipo> getAllIpos(IpoStatus status, IpoType type, String query) {
        LocalDate today = LocalDate.now();
        if (status == null && type == null && (query == null || query.isBlank())) {
            return ipoRepository.findAll(today);
        }
        return ipoRepository.search(status, type, query, today);
    }

    public Optional<Ipo> getIpoById(String id) {
        return ipoRepository.findById(id, LocalDate.now());
    }
}
