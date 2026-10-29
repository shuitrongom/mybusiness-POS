package com.mybusinesssilva.support.application;

import com.mybusinesssilva.support.adapters.in.rest.dto.ResendInboundEvent;

/**
 * Caso de uso de ingesta de un correo INBOUND al hilo del ticket (Fase 2). La barrera 1 (firma) ya
 * se resolvió aguas arriba (controller + {@code ResendWebhookVerifier}); este caso de uso orquesta
 * las barreras 2–4 (allowlist, correlación, parseo/saneo), la deduplicación transaccional y la
 * reutilización de {@code postMessage}.
 */
public interface IngestInboundMessageUseCase {

    /**
     * Procesa un evento inbound ya verificado.
     *
     * @param eventId clave de deduplicación ({@code svix-id} o {@code sha256:<hex>})
     * @param event   payload deserializado del correo
     * @return desenlace ({@code INSERTED} | {@code DISCARDED_*}) con los ids internos si aplica
     */
    InboundResult ingest(String eventId, ResendInboundEvent event);
}
