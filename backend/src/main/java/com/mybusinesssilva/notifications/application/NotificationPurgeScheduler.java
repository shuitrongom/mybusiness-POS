package com.mybusinesssilva.notifications.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Purga diaria de notificaciones leídas con más de 30 días de antigüedad. */
@Component
public class NotificationPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationPurgeScheduler.class);

    private final JdbcClient jdbc;

    public NotificationPurgeScheduler(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Purga diaria a las 03:00 hora México (America/Mexico_City). Borra notificaciones leídas > 30 días. */
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Mexico_City")
    public void purgeOldRead() {
        int deleted = jdbc.sql("""
                DELETE FROM admin.notification
                WHERE read_at IS NOT NULL
                  AND read_at < now() - INTERVAL '30 days'
                """).update();
        if (deleted > 0) {
            log.info("[NOTIF-PURGE] {} notificaciones leídas > 30 días eliminadas.", deleted);
        }
    }
}
