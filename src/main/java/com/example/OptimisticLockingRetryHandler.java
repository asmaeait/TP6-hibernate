package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;

import javax.persistence.OptimisticLockException;
import java.util.Optional;
import java.util.function.Consumer;

public class OptimisticLockingRetryHandler {

    private final ReservationService reservationService;
    private final int maxRetries;

    public OptimisticLockingRetryHandler(ReservationService reservationService,
                                         int maxRetries) {
        this.reservationService = reservationService;
        this.maxRetries = maxRetries;
    }

    //  Réessaie automatiquement en cas de conflit
    public void executeWithRetry(Long reservationId,
                                 Consumer<Reservation> operation) {
        int attempts = 0;
        boolean success = false;

        while (!success && attempts < maxRetries) {
            attempts++;
            try {
                // Relit toujours la dernière version depuis la BDD
                Optional<Reservation> opt = reservationService.findById(reservationId);
                if (!opt.isPresent()) {
                    System.out.println(" Réservation non trouvée !");
                    return;
                }

                Reservation reservation = opt.get();
                System.out.println("  Tentative " + attempts
                        + " : version actuelle = " + reservation.getVersion());

                // Appliquer la modification demandée
                operation.accept(reservation);

                // Sauvegarder
                reservationService.update(reservation);
                success = true;
                System.out.println("Opération réussie à la tentative " + attempts);

            } catch (OptimisticLockException e) {
                System.out.println("Conflit détecté à la tentative "
                        + attempts + " !");
                if (attempts >= maxRetries) {
                    System.out.println(" Abandon après " + maxRetries
                            + " tentatives.");
                    throw e;
                }
                // Attendre avant de réessayer (backoff)
                try {
                    Thread.sleep(100L * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                // RollbackException wrapping OptimisticLockException
                if (e.getCause() instanceof OptimisticLockException) {
                    System.out.println("   Conflit détecté (wrapped) à la tentative "
                            + attempts + " !");
                    if (attempts >= maxRetries) {
                        System.out.println(" Abandon après " + maxRetries
                                + " tentatives.");
                        return;
                    }
                    try {
                        Thread.sleep(100L * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    throw e;
                }
            }
        }
    }
}
