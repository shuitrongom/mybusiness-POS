package com.mybusinesssilva.licensing.application;

import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.BusinessRepository;
import com.mybusinesssilva.platform.notifications.NotificationPort;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada que gestiona el ciclo de prueba de los negocios:
 * <ul>
 *   <li>Notifica al dueño cuando faltan pocos días para el vencimiento de su prueba.</li>
 *   <li>Expira las pruebas ya vencidas (bloqueo de acceso), delegando en {@link LicensingService}.</li>
 * </ul>
 *
 * <p>Se ejecuta periódicamente (por defecto una vez al día).
 */
@Component
public class TrialExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrialExpirationScheduler.class);
    private static final long NOTICE_DAYS = 3;

    private final BusinessRepository businessRepository;
    private final LicensingService licensingService;
    private final NotificationPort notificationPort;
    private final Clock clock;

    public TrialExpirationScheduler(BusinessRepository businessRepository,
                                    LicensingService licensingService,
                                    NotificationPort notificationPort,
                                    Clock clock) {
        this.businessRepository = businessRepository;
        this.licensingService = licensingService;
        this.notificationPort = notificationPort;
        this.clock = clock;
    }

    /**
     * Corre diariamente: avisa próximos vencimientos y expira las pruebas vencidas.
     * La expresión cron se puede ajustar por configuración.
     */
    @Scheduled(cron = "${app.trial.check-cron:0 0 6 * * *}")
    public void checkTrials() {
        notifyUpcomingExpirations();
        int expired = licensingService.expireDueTrials();
        if (expired > 0) {
            log.info("Pruebas expiradas en esta ejecución: {}", expired);
        }
    }

    /**
     * Notifica a los negocios en prueba cuyo vencimiento está dentro de los próximos días.
     */
    public void notifyUpcomingExpirations() {
        Instant now = Instant.now(clock);
        Instant limit = now.plus(NOTICE_DAYS, ChronoUnit.DAYS);

        List<Business> all = businessRepository.findAll();
        for (Business b : all) {
            if (b.getStatus() == com.mybusinesssilva.licensing.domain.model.BusinessStatus.TRIAL
                    && b.getTrialEndsAt() != null
                    && b.getTrialEndsAt().isAfter(now)
                    && !b.getTrialEndsAt().isAfter(limit)) {
                notificationPort.sendEmail(
                        "dueño@" + b.getSchemaName(),
                        "Tu periodo de prueba está por vencer",
                        "El negocio '" + b.getName() + "' vence su prueba el "
                                + b.getTrialEndsAt() + ". Contacta al administrador para adquirir "
                                + "tu licencia y seguir disfrutando de MyBusiness Silva.",
                        null, null);
            }
        }
    }
}
