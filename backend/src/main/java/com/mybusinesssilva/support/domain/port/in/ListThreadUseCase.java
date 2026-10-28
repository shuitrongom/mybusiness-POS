package com.mybusinesssilva.support.domain.port.in;

import com.mybusinesssilva.support.domain.model.SupportTicketMessage;
import java.util.List;
import java.util.UUID;

/** Caso de uso: listar el hilo de mensajes de un ticket visible para el solicitante. */
public interface ListThreadUseCase {

    List<SupportTicketMessage> listThread(UUID ticketPublicId, RequesterScope scope);
}
