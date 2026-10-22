package com.example.saveup.dto;

import com.example.saveup.model.enums.ModoPago;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Pago a registrar sobre una deuda.")
public class PagoDeudaDTO {

    /**
     * Obligatorio solo en MONTO_PERSONALIZADO. En CUOTA_CALCULADA se ignora:
     * el monto lo determina el cronograma de la deuda.
     */
    @Digits(integer = 13, fraction = 2, message = "El monto del pago debe tener como máximo 13 enteros y 2 decimales")
    @Schema(description = "Monto a pagar, con escala 2. Obligatorio en MONTO_PERSONALIZADO.")
    private BigDecimal monto;

    @NotBlank(message = "La descripción del pago es obligatoria")
    @Schema(description = "Descripción del pago.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String descripcion;

    /**
     * Cómo se determina el monto. Por defecto MONTO_PERSONALIZADO, para no
     * romper clientes que solo envían monto y descripción.
     */
    @Schema(description = "Modo de pago. Por defecto MONTO_PERSONALIZADO.", defaultValue = "MONTO_PERSONALIZADO")
    private ModoPago modoPago = ModoPago.MONTO_PERSONALIZADO;
}
