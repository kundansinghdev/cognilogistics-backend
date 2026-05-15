/**
 * Integration-client module — adapters to external Indian government APIs.
 *
 * <p>Owns: Vahan (RTO vehicle registry, with consent log — BR-10), Sarathi (driving
 * licence registry), and GST (GSTIN-based business identity lookup). Each
 * sub-package follows the same shape: a {@code *Client} interface with a
 * {@code Mock*Client} for tests/CI and a {@code Real*Client} for production calls.
 *
 * <p>Depends on: {@code order} (for tenant-scoped order lookups during
 * consent/lookup operations) and {@code platform.api}. Depended on by: the order
 * module via the {@link com.cognilogistic.order.service.OrderService.FleetConfirmGuard}
 * SPI implemented in {@link com.cognilogistic.integrationclient.vahan.VahanService}.
 *
 * <p>Read first: {@link com.cognilogistic.integrationclient.vahan.VahanService}
 * for the canonical "consent gate before external lookup" pattern that BR-10 enforces.
 */
package com.cognilogistic.integrationclient;
