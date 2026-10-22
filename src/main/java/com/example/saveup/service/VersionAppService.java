package com.example.saveup.service;

import com.example.saveup.dto.AppVersionResponseDTO;
import com.example.saveup.model.enums.Plataforma;
import com.example.saveup.service.version.SemVerUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Política de versión mínima de la app móvil (T9.4): decide si una versión
 * está bloqueada (< min-*) o solo sugerida (< latest-*), con umbrales y URL
 * de tienda por plataforma. Publicar una release = subir latest-* en
 * application.properties; activar el bloqueo por contrato roto = subir min-*.
 */
@Service
public class VersionAppService {

    /** Mensaje que la app muestra cuando el bloqueo impide continuar. */
    private static final String MSG_OBLIGATORIA =
            "Tu versión ya no es compatible con SaveUp Lite. Actualiza la app desde la tienda para continuar.";
    /** Mensaje cuando hay versión nueva pero la app todavía funciona. */
    private static final String MSG_SUGERIDA =
            "Hay una nueva versión de SaveUp Lite disponible. Te recomendamos actualizarla.";
    /** Mensaje cuando la versión reportada está vigente. */
    private static final String MSG_AL_DIA = "Tu app está al día.";

    @Value("${app.version.min-android:1.0.0}")
    private String minAndroid;

    @Value("${app.version.latest-android:1.0.0}")
    private String latestAndroid;

    @Value("${app.version.min-ios:1.0.0}")
    private String minIos;

    @Value("${app.version.latest-ios:1.0.0}")
    private String latestIos;

    @Value("${app.version.url-tienda-android:}")
    private String urlTiendaAndroid;

    @Value("${app.version.url-tienda-ios:}")
    private String urlTiendaIos;

    @Value("${app.version.terminos:1}")
    private String versionTerminos;

    @Value("${app.version.privacidad:1}")
    private String versionPrivacidad;

    /**
     * Resuelve la política para la plataforma y versión recibidas. La versión
     * debe ser SemVer (400 DATOS_INVALIDOS si no); parámetro ausente o
     * plataforma desconocida los rechaza Spring antes (PARAMETRO_INVALIDO).
     */
    public AppVersionResponseDTO obtenerVersion(Plataforma plataforma, String version) {
        String versionValida = SemVerUtils.normalizada(version);
        SemVerUtils.exigirFormato(versionValida);

        Umbrales umbrales = umbralesDe(plataforma);
        boolean obligatoria = SemVerUtils.comparar(versionValida, umbrales.minima) < 0;
        boolean sugerida = !obligatoria && SemVerUtils.comparar(versionValida, umbrales.reciente) < 0;

        String mensaje;
        if (obligatoria) {
            mensaje = MSG_OBLIGATORIA;
        } else if (sugerida) {
            mensaje = MSG_SUGERIDA;
        } else {
            mensaje = MSG_AL_DIA;
        }

        return new AppVersionResponseDTO(obligatoria, sugerida, mensaje,
                umbrales.urlTienda, versionTerminos, versionPrivacidad);
    }

    private Umbrales umbralesDe(Plataforma plataforma) {
        return switch (plataforma) {
            case ANDROID -> new Umbrales(minAndroid, latestAndroid, urlTiendaAndroid);
            case IOS -> new Umbrales(minIos, latestIos, urlTiendaIos);
        };
    }

    private record Umbrales(String minima, String reciente, String urlTienda) {
    }
}
