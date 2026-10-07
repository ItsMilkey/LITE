ALTER TABLE DEUDA ADD tipo_deuda VARCHAR(50) DEFAULT 'OTRO' NOT NULL;
ALTER TABLE DEUDA ADD modalidad_calculo VARCHAR(50) DEFAULT 'SIN_INTERES' NOT NULL;
ALTER TABLE DEUDA ADD monto_capital NUMBER(19,2);
ALTER TABLE DEUDA ADD gastos_iniciales NUMBER(19,2) DEFAULT 0 NOT NULL;
ALTER TABLE DEUDA ADD costo_adicional_por_cuota NUMBER(19,2) DEFAULT 0 NOT NULL;
ALTER TABLE DEUDA ADD tasa_mensual NUMBER(12,8) DEFAULT 0 NOT NULL;
ALTER TABLE DEUDA ADD valor_cuota NUMBER(19,2);
ALTER TABLE DEUDA ADD fecha_primera_cuota DATE;
ALTER TABLE DEUDA ADD cuotas_pagadas_previas NUMBER(10,0) DEFAULT 0 NOT NULL;
ALTER TABLE DEUDA ADD monto_pagado_previo NUMBER(19,2) DEFAULT 0 NOT NULL;
ALTER TABLE DEUDA ADD carga_anual_equivalente NUMBER(10,4) DEFAULT 0 NOT NULL;
ALTER TABLE DEUDA ADD costo_total_credito NUMBER(19,2);

ALTER TABLE DEUDA ADD CONSTRAINT chk_deuda_tipo CHECK (tipo_deuda IN ('PERSONAL', 'TIENDA', 'CREDITO_CONSUMO', 'EDUCACION', 'OTRO'));
ALTER TABLE DEUDA ADD CONSTRAINT chk_deuda_modalidad CHECK (modalidad_calculo IN ('SIN_INTERES', 'TASA_CONOCIDA', 'CUOTA_CONOCIDA'));
ALTER TABLE DEUDA ADD CONSTRAINT chk_deuda_cuotas_previas CHECK (cuotas_pagadas_previas >= 0);

UPDATE DEUDA SET 
    monto_capital = monto_total,
    valor_cuota = ROUND(monto_total / cantidad_cuotas, 2),
    costo_total_credito = monto_total;

ALTER TABLE DEUDA MODIFY monto_capital NOT NULL;
ALTER TABLE DEUDA MODIFY valor_cuota NOT NULL;
ALTER TABLE DEUDA MODIFY costo_total_credito NOT NULL;
