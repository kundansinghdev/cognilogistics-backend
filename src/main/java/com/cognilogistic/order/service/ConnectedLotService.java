package com.cognilogistic.order.service;

import com.cognilogistic.order.repository.ConnectedLotRepository;
import com.cognilogistic.order.repository.ConnectedLotRepository.ConnectedLotRow;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for querying Connected Lots — a derived grouping of two or more orders
 * that share the same vehicle registration on the same pickup date.
 *
 * <p>Connected Lots have no database table; they are computed on-the-fly from the
 * {@code orders} table by {@link ConnectedLotRepository}. This service acts as a
 * thin facade to keep the controller decoupled from repository internals.
 */
@Service
public class ConnectedLotService {

    private final ConnectedLotRepository repo;

    public ConnectedLotService(ConnectedLotRepository repo) {
        this.repo = repo;
    }

    /**
     * Returns connected lots for the given TP account, optionally filtered by date.
     *
     * @param tpAccountId the owning TP account ID
     * @param date        optional pickup date filter; {@code null} returns all dates
     * @return list of connected lot rows, each representing a vehicle+date group of >= 2 orders
     */
    public List<ConnectedLotRow> getConnectedLots(String tpAccountId, LocalDate date) {
        return repo.findConnectedLots(tpAccountId, date);
    }
}
