package com.mybusinesssilva.platform.notifications;

/**
 * Puerto de salida para enviar notificaciones (correo). Abstrae al proveedor concreto: se puede
 * empezar con un adaptador simple y cambiarlo por un proveedor transaccional real (SendGrid,
 * SES, etc.) implementando otro adaptador, sin tocar el dominio.
 */
public interface NotificationPort {

    /**
     * Envía un correo con un adjunto opcional (por ejemplo el PDF de un comprobante).
     *
     * @param to             destinatario
     * @param subject        asunto
     * @param body           cuerpo del mensaje
     * @param attachmentName nombre del adjunto (nulo si no hay)
     * @param attachment     contenido del adjunto en bytes (nulo si no hay)
     */
    void sendEmail(String to, String subject, String body, String attachmentName, byte[] attachment);

    /**
     * Variante ADITIVA (Fase 2) que permite fijar un {@code Reply-To} y headers de correo extra
     * (p. ej. para correlacionar la respuesta inbound con un ticket). Por defecto IGNORA
     * {@code replyTo}/{@code headers} y delega en el envío clásico de 5 argumentos, de modo que los
     * adaptadores que no lo soporten (p. ej. {@code LoggingNotificationAdapter}) lo heredan sin
     * cambios de comportamiento. {@code ResendNotificationAdapter} lo sobrescribe para inyectar
     * {@code reply_to}/{@code headers} en el payload.
     *
     * @param to             destinatario
     * @param subject        asunto
     * @param body           cuerpo del mensaje
     * @param attachmentName nombre del adjunto (nulo si no hay)
     * @param attachment     contenido del adjunto en bytes (nulo si no hay)
     * @param replyTo        dirección de {@code Reply-To} (nula/en blanco ⇒ no se añade)
     * @param headers        headers de correo extra (nulo/vacío ⇒ no se añaden)
     */
    default void sendEmail(String to, String subject, String body,
                           String attachmentName, byte[] attachment,
                           String replyTo, java.util.Map<String, String> headers) {
        sendEmail(to, subject, body, attachmentName, attachment);
    }

    /**
     * Envía un mensaje por WhatsApp. Como el correo, abstrae al proveedor concreto (Twilio,
     * WhatsApp Cloud API, etc.); el adaptador por defecto solo registra el envío.
     *
     * @param toPhone número de WhatsApp destino (formato internacional, p. ej. +52155...)
     * @param message texto del mensaje
     */
    void sendWhatsApp(String toPhone, String message);
}
