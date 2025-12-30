package com.example.saveup.config;

import com.example.saveup.model.Categoria;
import com.example.saveup.repository.CategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Override
    public void run(String... args) throws Exception {
        // Se ejecuta solo si la tabla de categorías está vacía para evitar duplicados.
        if (categoriaRepository.count() == 0) {
            System.out.println("Poblando la base de datos con categorías iniciales...");

            List<Categoria> categorias = List.of(
                    new Categoria(null, "Sueldo", "ic_work", "#009688",
                            com.example.saveup.model.enums.TipoPresupuesto.OTROS),
                    new Categoria(null, "Comida", "ic_fastfood", "#FFC107",
                            com.example.saveup.model.enums.TipoPresupuesto.NECESIDAD),
                    new Categoria(null, "Transporte", "ic_directions_car", "#2196F3",
                            com.example.saveup.model.enums.TipoPresupuesto.NECESIDAD),
                    new Categoria(null, "Cuentas y Servicios", "ic_receipt_long", "#4CAF50",
                            com.example.saveup.model.enums.TipoPresupuesto.NECESIDAD),
                    new Categoria(null, "Ocio y Entretenimiento", "ic_sports_esports", "#E91E63",
                            com.example.saveup.model.enums.TipoPresupuesto.DESEO),
                    new Categoria(null, "Salud y Bienestar", "ic_medical_services", "#F44336",
                            com.example.saveup.model.enums.TipoPresupuesto.NECESIDAD),
                    new Categoria(null, "Ropa y Accesorios", "ic_checkroom", "#9C27B0",
                            com.example.saveup.model.enums.TipoPresupuesto.DESEO),
                    new Categoria(null, "Deudas", "ic_payment", "#795548",
                            com.example.saveup.model.enums.TipoPresupuesto.NECESIDAD),
                    new Categoria(null, "Otro", "ic_label", "#607D8B",
                            com.example.saveup.model.enums.TipoPresupuesto.OTROS));

            categoriaRepository.saveAll(categorias);
            System.out.println("Categorías iniciales guardadas con éxito.");
        } else {
            // Lógica de migración para categorías existentes sin tipo
            List<Categoria> existing = categoriaRepository.findAll();
            boolean changed = false;
            for (Categoria c : existing) {
                // Forzamos actualización de categorías conocidas para corregir datos antiguos o
                // mal migrados
                if (true) {
                    com.example.saveup.model.enums.TipoPresupuesto oldType = c.getTipoPresupuesto();
                    switch (c.getNombre()) {
                        case "Comida":
                        case "Transporte":
                        case "Cuentas y Servicios":
                        case "Salud y Bienestar":
                        case "Deudas":
                        case "Arriendo": // Agregando común
                        case "Supermercado": // Agregando común
                            c.setTipoPresupuesto(com.example.saveup.model.enums.TipoPresupuesto.NECESIDAD);
                            break;
                        case "Ocio y Entretenimiento":
                        case "Ropa y Accesorios":
                        case "Viajes":
                        case "Regalos":
                            c.setTipoPresupuesto(com.example.saveup.model.enums.TipoPresupuesto.DESEO);
                            break;
                        default:
                            if (c.getTipoPresupuesto() == null) {
                                c.setTipoPresupuesto(com.example.saveup.model.enums.TipoPresupuesto.OTROS);
                            }
                            break;
                    }
                    if (c.getTipoPresupuesto() != oldType) {
                        changed = true;
                    }
                }
            }
            if (changed) {
                categoriaRepository.saveAll(existing);
                System.out.println("Categorías existentes actualizadas con TipoPresupuesto.");
            }
        }
    }
}
