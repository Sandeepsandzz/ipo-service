package com.ipo.tracker.repository;

import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.model.IpoStatus;
import com.ipo.tracker.model.IpoType;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Repository
public class IpoRepository {

    private final List<Ipo> ipos = new CopyOnWriteArrayList<>();

    public IpoRepository() {
        seedInitialData();
    }

    private void seedInitialData() {
        LocalDate today = LocalDate.now();

        // 1. Actively Open IPOs (Available to apply as of today)
        ipos.add(new Ipo(
            "swiggy-ltd",
            "Swiggy Limited",
            "SWIGGY",
            "NSE, BSE",
            IpoType.MAINBOARD,
            today.minusDays(1),
            today.plusDays(2),
            today.plusDays(6),
            371.0,
            390.0,
            38,
            38 * 390.0,
            "₹11,327 Cr",
            IpoStatus.OPEN,
            "+₹25 (6.41%)",
            "3.59x (Retail: 1.14x, QIB: 6.02x, NII: 1.25x)",
            "Link Intime India Pvt Ltd"
        ));

        ipos.add(new Ipo(
            "sagility-india",
            "Sagility India Limited",
            "SAGILITY",
            "NSE, BSE",
            IpoType.MAINBOARD,
            today,
            today.plusDays(3),
            today.plusDays(7),
            28.0,
            30.0,
            500,
            500 * 30.0,
            "₹2,106 Cr",
            IpoStatus.OPEN,
            "+₹3 (10.0%)",
            "1.85x (Retail: 2.12x, QIB: 1.50x, NII: 1.90x)",
            "KFin Technologies Limited"
        ));

        ipos.add(new Ipo(
            "acme-solar-holdings",
            "ACME Solar Holdings Limited",
            "ACMESOLAR",
            "NSE, BSE",
            IpoType.MAINBOARD,
            today.minusDays(2),
            today.plusDays(1),
            today.plusDays(5),
            275.0,
            289.0,
            51,
            51 * 289.0,
            "₹2,900 Cr",
            IpoStatus.OPEN,
            "+₹12 (4.15%)",
            "2.75x (Retail: 1.45x, QIB: 3.20x, NII: 2.10x)",
            "KFin Technologies Limited"
        ));

        ipos.add(new Ipo(
            "niva-bupa-health-insurance",
            "Niva Bupa Health Insurance Co Ltd",
            "NIVABUPA",
            "NSE, BSE",
            IpoType.MAINBOARD,
            today,
            today.plusDays(2),
            today.plusDays(6),
            70.0,
            74.0,
            200,
            200 * 74.0,
            "₹2,200 Cr",
            IpoStatus.OPEN,
            "+₹5 (6.75%)",
            "0.95x (Retail: 1.05x, QIB: 0.85x, NII: 0.90x)",
            "KFin Technologies Limited"
        ));

        ipos.add(new Ipo(
            "neal-rail-sme",
            "Neal Rail Systems Limited",
            "NEALRAIL",
            "NSE SME",
            IpoType.SME,
            today.minusDays(1),
            today.plusDays(1),
            today.plusDays(5),
            140.0,
            145.0,
            1000,
            1000 * 145.0,
            "₹65.20 Cr",
            IpoStatus.OPEN,
            "+₹45 (31.03%)",
            "12.4x (Retail: 16.5x, Non-Retail: 8.3x)",
            "Bigshare Services Pvt Ltd"
        ));

        // 2. Upcoming IPOs (Opening soon)
        ipos.add(new Ipo(
            "ntpc-green-energy",
            "NTPC Green Energy Limited",
            "NTPCGREEN",
            "NSE, BSE",
            IpoType.MAINBOARD,
            today.plusDays(5),
            today.plusDays(8),
            today.plusDays(12),
            102.0,
            108.0,
            138,
            138 * 108.0,
            "₹10,000 Cr",
            IpoStatus.UPCOMING,
            "+₹18 (16.67%)",
            "Not open yet",
            "KFin Technologies Limited"
        ));

        ipos.add(new Ipo(
            "zinka-logistics",
            "Zinka Logistics Solutions Ltd (BlackBuck)",
            "BLACKBUCK",
            "NSE, BSE",
            IpoType.MAINBOARD,
            today.plusDays(7),
            today.plusDays(10),
            today.plusDays(14),
            259.0,
            273.0,
            54,
            54 * 273.0,
            "₹1,114 Cr",
            IpoStatus.UPCOMING,
            "+₹10 (3.66%)",
            "Not open yet",
            "KFin Technologies Limited"
        ));

        // 3. Recently Closed IPOs
        ipos.add(new Ipo(
            "hyundai-motor-india",
            "Hyundai Motor India Limited",
            "HYUNDAI",
            "NSE, BSE",
            IpoType.MAINBOARD,
            today.minusDays(10),
            today.minusDays(7),
            today.minusDays(2),
            1865.0,
            1960.0,
            7,
            7 * 1960.0,
            "₹27,870 Cr",
            IpoStatus.CLOSED,
            "-₹15 (-0.76%)",
            "2.37x (Retail: 0.50x, QIB: 6.97x, NII: 0.60x)",
            "KFin Technologies Limited"
        ));

        ipos.add(new Ipo(
            "waaree-energies",
            "Waaree Energies Limited",
            "WAAREE",
            "NSE, BSE",
            IpoType.MAINBOARD,
            today.minusDays(8),
            today.minusDays(5),
            today.minusDays(1),
            1427.0,
            1503.0,
            9,
            9 * 1503.0,
            "₹4,321 Cr",
            IpoStatus.CLOSED,
            "+₹1400 (93.1%)",
            "76.34x (Retail: 10.79x, QIB: 208.63x, NII: 62.49x)",
            "Link Intime India Pvt Ltd"
        ));
    }

    public List<Ipo> findAll(LocalDate asOfDate) {
        return ipos.stream()
                .map(ipo -> ipo.withComputedStatus(asOfDate))
                .collect(Collectors.toList());
    }

    public List<Ipo> findActive(LocalDate asOfDate) {
        return ipos.stream()
                .filter(ipo -> ipo.isOpenOn(asOfDate))
                .map(ipo -> ipo.withComputedStatus(asOfDate))
                .collect(Collectors.toList());
    }

    public List<Ipo> findUpcoming(LocalDate asOfDate) {
        return ipos.stream()
                .filter(ipo -> ipo.computeStatus(asOfDate) == IpoStatus.UPCOMING)
                .map(ipo -> ipo.withComputedStatus(asOfDate))
                .collect(Collectors.toList());
    }

    public List<Ipo> findClosed(LocalDate asOfDate) {
        return ipos.stream()
                .filter(ipo -> ipo.computeStatus(asOfDate) == IpoStatus.CLOSED)
                .map(ipo -> ipo.withComputedStatus(asOfDate))
                .collect(Collectors.toList());
    }

    public Optional<Ipo> findById(String id, LocalDate asOfDate) {
        return ipos.stream()
                .filter(ipo -> ipo.id().equalsIgnoreCase(id) || ipo.symbol().equalsIgnoreCase(id))
                .findFirst()
                .map(ipo -> ipo.withComputedStatus(asOfDate));
    }

    public List<Ipo> search(IpoStatus status, IpoType type, String query, LocalDate asOfDate) {
        return ipos.stream()
                .map(ipo -> ipo.withComputedStatus(asOfDate))
                .filter(ipo -> status == null || ipo.status() == status)
                .filter(ipo -> type == null || ipo.issueType() == type)
                .filter(ipo -> query == null || query.isBlank() ||
                        ipo.companyName().toLowerCase().contains(query.toLowerCase()) ||
                        ipo.symbol().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
    }

    public Ipo save(Ipo ipo) {
        ipos.removeIf(existing -> existing.id().equalsIgnoreCase(ipo.id()));
        ipos.add(ipo);
        return ipo;
    }
}
