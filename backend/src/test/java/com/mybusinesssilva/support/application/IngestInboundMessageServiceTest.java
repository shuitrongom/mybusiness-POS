package com.mybusinesssilva.support.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mybusinesssilva.support.adapters.in.rest.dto.ResendInboundEvent;
import com.mybusinesssilva.support.config.NotificationsProperties;
import com.mybusinesssilva.support.domain.model.AuthorType;
import com.mybusinesssilva.support.domain.model.SupportTicket;
import com.mybusinesssilva.support.domain.model.SupportTicketMessage;
import com.mybusinesssilva.support.domain.model.TicketStatus;
import com.mybusinesssilva.support.domain.model.TicketType;
import com.mybusinesssilva.support.domain.port.in.PostMessageUseCase;
import com.mybusinesssilva.support.domain.port.out.ResendInboundContentPort;
import com.mybusinesssilva.support.domain.port.out.ResendInboundContentPort.InboundContent;
import com.mybusinesssilva.support.domain.port.out.SupportInboundEventRepository;
import com.mybusinesssilva.support.domain.port.out.SupportMessageRepository;
import com.mybusinesssilva.support.domain.port.out.SupportTicketRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Pruebas unitarias de la ingesta inbound con la causa raíz corregida: el webhook {@code
 * email.received} de Resend NO trae el cuerpo (solo metadatos + {@code email_id}); el cuerpo se trae
 * por la Receiving API SOLO tras pasar firma (controller), allowlist y correlación.
 *
 * <p>Casos: (a) metadatos + email_id, la API devuelve text → se inserta; (b) la API solo devuelve html
 * → se deriva a texto y se inserta; (c) la API devuelve cuerpo realmente vacío → {@code
 * DISCARDED_EMPTY}; (d) la API falla/timeout → {@code DISCARDED_CONTENT_FETCH_FAILED} (distinguible),
 * no se inserta y no rompe. Además se verifica que el fetch del cuerpo NO ocurre si el remitente no
 * está en la allowlist ni si no hay correlación.
 */
class IngestInboundMessageServiceTest {

    private static final UUID PUBLIC_ID = UUID.fromString("bbb61424-1557-4b51-91ff-b7d9a5b7fff7");
    private static final String ALLOWED_SENDER = "shuitrongomez05@gmail.com";
    private static final String EMAIL_ID = "741c65f7-ca9c-4242-9230-f1ffd6059eb8";

    private SupportInboundEventRepository inboundEventRepo;
    private SupportTicketRepository ticketRepo;
    private SupportMessageRepository messageRepo;
    private PostMessageUseCase postMessageUseCase;
    private ResendInboundContentPort inboundContentPort;
    private NotificationsProperties props;
    private IngestInboundMessageService service;

    @BeforeEach
    void setUp() {
        inboundEventRepo = mock(SupportInboundEventRepository.class);
        ticketRepo = mock(SupportTicketRepository.class);
        messageRepo = mock(SupportMessageRepository.class);
        postMessageUseCase = mock(PostMessageUseCase.class);
        inboundContentPort = mock(ResendInboundContentPort.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

        // allowlist con el remitente autorizado; topes por defecto.
        var resend = new NotificationsProperties.Resend(
                "re_fake_key", "soporte@puntonubepos.com", "whsec_x", ALLOWED_SENDER,
                "puntonubepos.com", "svix", 1_048_576, 65_536);
        props = new NotificationsProperties(resend, new NotificationsProperties.Support("inbox@x.com"));

        service = new IngestInboundMessageService(inboundEventRepo, ticketRepo, messageRepo,
                postMessageUseCase, inboundContentPort, props, txManager);

        // Claim ganado y ticket existente por defecto (las barreras previas pasan).
        when(inboundEventRepo.tryClaim(anyString())).thenReturn(true);
        when(ticketRepo.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(ticket()));
    }

    /** Payload REAL de Resend: solo metadatos (sin text/html), con email_id y received_for. */
    private static ResendInboundEvent metadataOnlyEvent() {
        return new ResendInboundEvent(
                ALLOWED_SENDER,
                List.of("reply+" + PUBLIC_ID + "@puntonubepos.com"),
                List.of("reply+" + PUBLIC_ID + "@puntonubepos.com"),
                null,
                "Re: [Soporte PuntoNube] [PN-" + PUBLIC_ID + "] ERROR: Teststs",
                EMAIL_ID,
                null,
                null);
    }

    private static SupportTicket ticket() {
        return new SupportTicket(42L, PUBLIC_ID, 9L, "tenant_9", "reporter@x.com", "reporter@x.com",
                "OWNER", TicketType.ERROR, "Título", "Descripción", null, null, null, "desktop",
                TicketStatus.NUEVO, null, null, null, null, null, List.of());
    }

    private static SupportTicketMessage savedMessage() {
        return new SupportTicketMessage(100L, UUID.randomUUID(), 42L, AuthorType.SUPER_ADMIN,
                "SUPER_ADMIN", ALLOWED_SENDER, ALLOWED_SENDER, "cuerpo", null, null, null, null);
    }

    @Test
    void insertsWhenReceivingApiReturnsText() {
        when(inboundContentPort.fetchContent(EMAIL_ID))
                .thenReturn(Optional.of(new InboundContent("Ya lo revisamos, gracias", null)));
        when(postMessageUseCase.postMessage(any(), anyString(), any(), any()))
                .thenReturn(savedMessage());

        InboundResult result = service.ingest("msg_abc", metadataOnlyEvent());

        assertThat(result.outcome()).isEqualTo(InboundResult.INSERTED);
        verify(inboundContentPort).fetchContent(EMAIL_ID);
        verify(postMessageUseCase).postMessage(eq(PUBLIC_ID), eq("Ya lo revisamos, gracias"),
                any(), any());
        verify(messageRepo).markSource(100L, "INBOUND_EMAIL");
    }

    @Test
    void derivesTextFromHtmlWhenApiReturnsOnlyHtml() {
        when(inboundContentPort.fetchContent(EMAIL_ID))
                .thenReturn(Optional.of(new InboundContent(null, "<p>Hola equipo</p><br>segunda línea")));
        when(postMessageUseCase.postMessage(any(), anyString(), any(), any()))
                .thenReturn(savedMessage());

        InboundResult result = service.ingest("msg_abc", metadataOnlyEvent());

        assertThat(result.outcome()).isEqualTo(InboundResult.INSERTED);
        verify(postMessageUseCase).postMessage(eq(PUBLIC_ID),
                org.mockito.ArgumentMatchers.argThat(body ->
                        body.contains("Hola equipo") && !body.contains("<p>")),
                any(), any());
    }

    @Test
    void discardsEmptyWhenApiReturnsBlankBody() {
        when(inboundContentPort.fetchContent(EMAIL_ID))
                .thenReturn(Optional.of(new InboundContent("   \n  ", null)));

        InboundResult result = service.ingest("msg_abc", metadataOnlyEvent());

        assertThat(result.outcome()).isEqualTo(InboundResult.DISCARDED_EMPTY);
        verify(postMessageUseCase, never()).postMessage(any(), anyString(), any(), any());
        verify(inboundEventRepo).finalizeEvent(eq("msg_abc"), eq(InboundResult.DISCARDED_EMPTY),
                anyString(), any(), any());
    }

    @Test
    void discardsWithFetchFailedWhenApiFails() {
        // La API falla/timeout → el puerto devuelve empty (nunca lanza). Outcome distinguible.
        when(inboundContentPort.fetchContent(EMAIL_ID)).thenReturn(Optional.empty());

        InboundResult result = service.ingest("msg_abc", metadataOnlyEvent());

        assertThat(result.outcome()).isEqualTo(InboundResult.DISCARDED_CONTENT_FETCH_FAILED);
        verify(postMessageUseCase, never()).postMessage(any(), anyString(), any(), any());
        verify(inboundEventRepo).finalizeEvent(eq("msg_abc"),
                eq(InboundResult.DISCARDED_CONTENT_FETCH_FAILED), anyString(), any(), any());
    }

    @Test
    void doesNotCallReceivingApiWhenSenderNotAllowed() {
        var fromEvil = new ResendInboundEvent("extrano@evil.com",
                List.of("reply+" + PUBLIC_ID + "@puntonubepos.com"), null, null,
                "Re: [PN-" + PUBLIC_ID + "]", EMAIL_ID, null, null);

        InboundResult result = service.ingest("msg_abc", fromEvil);

        assertThat(result.outcome()).isEqualTo(InboundResult.DISCARDED_ALLOWLIST);
        verifyNoInteractions(inboundContentPort);
    }

    @Test
    void doesNotCallReceivingApiWhenNoCorrelation() {
        var noToken = new ResendInboundEvent(ALLOWED_SENDER, List.of("soporte@puntonubepos.com"),
                null, null, "Re: sin token", EMAIL_ID, null, null);

        InboundResult result = service.ingest("msg_abc", noToken);

        assertThat(result.outcome()).isEqualTo(InboundResult.DISCARDED_NO_TOKEN);
        verifyNoInteractions(inboundContentPort);
    }
}
