package com.example.saveup.dto;

import com.example.saveup.model.enums.ModoPago;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PagoDeudaDTO {

    /**
     * Obligatorio solo en MONTO_PERSONALIZADO. En CUOTA_CALCULADA se ignora:
     * el monto lo determina el cronograma de la deuda.
     */
    @Digits(integer = 17, fraction = 2, message = "El monto del pago debe tener como máximo 17 enteros y 2 decimales")
    private BigDecimal monto;

    @NotBlank(message = "La descripción del pago es obligatoria")
    private String descripcion;

    /**
     * Cómo se determina el monto. Por defecto MONTO_PERSONALIZADO, para no
     * romper clientes que solo envían monto y descripción.
     */
    private ModoPago modoPago = ModoPago.MONTO_PERSONALIZADO;
}
