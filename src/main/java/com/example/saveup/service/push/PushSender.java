package com.example.saveup.service.push;

import java.util.Map;

/**
 * Puerto de salida hacia el proveedor de notificaciones (T9.6). El dominio no
 * conoce FCM: solo pide enviar un título, un cuerpo y un payload de datos.
 *
 * <p>La implementación reporta dos cosas distintas: si el envío se aceptó y si
 * el token quedó inutilizable. Lo segundo permite desactivar el dispositivo sin
 * interpretar códigos de error del proveedor fuera de su adaptador.
 */
public interface PushSender {

    /**
     * Envía una notificación a un dispositivo.
     *
     * @param token  token de FCM del dispositivo
     * @param titulo título visible de la notificación
     * @param cuerpo cuerpo visible de la notificación
     * @param datos  payload de datos para que la app decida a dónde navegar
     * @return resultado del intento; nunca lanza por un fallo de entrega
     */
    ResultadoEnvio enviar(String token, String titulo, String cuerpo, Map<String, String> datos);

    /** Resultado de un intento de envío. */
    record ResultadoEnvio(boolean enviado, boolean tokenInvalido) {

        public static ResultadoEnvio ok() {
            return new ResultadoEnvio(true, false);
        }

        public static ResultadoEnvio fallo() {
            return new ResultadoEnvio(false, false);
        }

        public static ResultadoEnvio tokenMuerto() {
            return new ResultadoEnvio(false, true);
        }
    }
}
