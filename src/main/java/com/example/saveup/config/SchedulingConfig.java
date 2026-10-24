package com.example.saveup.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas (T9.6). Vive en una configuración propia para
 * que el scheduling no quede escondido en el servicio que lo usa y sea trivial
 * de encontrar cuando alguien pregunte "¿qué corre solo en este backend?".
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
