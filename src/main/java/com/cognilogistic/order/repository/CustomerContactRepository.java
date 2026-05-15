package com.cognilogistic.order.repository;

import com.cognilogistic.order.model.ContactType;
import com.cognilogistic.order.model.CustomerContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CustomerContact}.
 *
 * <p>The notification module reads from this repo to route order updates to the right
 * person — a status change goes to PRIMARY, an invoice goes to FINANCE, etc.
 */
public interface CustomerContactRepository extends JpaRepository<CustomerContact, String> {

    /**
     * Returns every contact attached to the given customer.
     *
     * @param customerId the customer's UUID
     * @return all contacts; empty if none
     */
    List<CustomerContact> findByCustomerId(String customerId);

    /**
     * Returns the customer's contact of a specific type, if one exists. Used by the
     * notification module to route messages — order updates → PRIMARY, invoices →
     * FINANCE, dispatch coordination → LOGISTICS.
     *
     * @param customerId  the customer's UUID
     * @param contactType the role to look up
     * @return the contact if one exists for that type
     */
    Optional<CustomerContact> findFirstByCustomerIdAndContactType(
            String customerId, ContactType contactType);

    /**
     * Returns the row marked {@code is_primary=TRUE} for this customer — the
     * notification fallback when no type-specific contact exists.
     *
     * @param customerId the customer's UUID
     * @return the primary contact if marked; empty otherwise
     */
    Optional<CustomerContact> findFirstByCustomerIdAndIsPrimaryTrue(String customerId);
}
