package com.cognilogistic.order.repository;

import com.cognilogistic.order.model.AddressType;
import com.cognilogistic.order.model.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CustomerAddress}.
 *
 * <p>All queries are customer-scoped — a customer's addresses don't leak to a different
 * tenant via wrong filtering because the customer itself is tenant-scoped (via
 * {@code customers.created_by_tp_id}).
 */
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, String> {

    /**
     * Returns every address attached to the given customer. Used by order creation to
     * populate the pickup / drop dropdown when the user picks a customer.
     *
     * @param customerId the customer's UUID
     * @return all addresses, in insertion order; empty if none
     */
    List<CustomerAddress> findByCustomerId(String customerId);

    /**
     * Returns the customer's default address of a specific type (BILLING / SHIPPING / BOTH).
     * Used by order creation to auto-fill the relevant location field. The application
     * maintains the "exactly one default per type" invariant on update.
     *
     * @param customerId  the customer's UUID
     * @param addressType the type to look up
     * @return the default address if one exists; empty otherwise
     */
    Optional<CustomerAddress> findFirstByCustomerIdAndAddressTypeAndIsDefaultTrue(
            String customerId, AddressType addressType);
}
