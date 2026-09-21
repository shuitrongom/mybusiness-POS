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
}
