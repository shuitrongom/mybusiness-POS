package com.mybusinesssilva.support.domain.port.in;

import com.mybusinesssilva.support.domain.model.AuthorType;
import com.mybusinesssilva.support.domain.model.SupportTicketMessage;
import java.util.UUID;

/** Caso de uso: publicar un mensaje en el hilo de un ticket visible para el solicitante. */
public interface PostMessageUseCase {

    /** Author lleva los campos derivados del controller; el servicio solo los persiste. */
    record Author(AuthorType type, String role, String userId, String email,
                  String appVersion, String screenPath) {
    }

    SupportTicketMessage postMessage(UUID ticketPublicId, String body,
                                     Author author, RequesterScope scope);
}
