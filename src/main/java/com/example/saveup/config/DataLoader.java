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
                    new Categoria(null, "Ahorro", "ic_savings", "#3F51B5",
                            com.example.saveup.model.enums.TipoPresupuesto.AHORRO),
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
                // Forzamos actualización de categorías conocidas para corregir datos antiguos o
                // mal migrados
                if (true) {
                    com.example.saveup.model.enums.TipoPresupuesto oldType = c.getTipoPresupuesto();
                    String nombreNormalizado = c.getNombre().trim().toLowerCase();

                    switch (nombreNormalizado) {
                        case "comida":
                        case "transporte":
                        case "cuentas y servicios":
                        case "salud y bienestar":
                        case "deudas":
                        case "arriendo":
                        case "supermercado":
                        case "educación":
                        case "educacion":
                            c.setTipoPresupuesto(com.example.saveup.model.enums.TipoPresupuesto.NECESIDAD);
                            break;
                        case "ocio y entretenimiento":
                        case "ropa y accesorios":
                        case "viajes":
                        case "regalos":
                            c.setTipoPresupuesto(com.example.saveup.model.enums.TipoPresupuesto.DESEO);
                            break;
                        case "ahorro":
                        case "inversión":
                        case "inversion":
                            c.setTipoPresupuesto(com.example.saveup.model.enums.TipoPresupuesto.AHORRO);
                            break;
                        case "sueldo":
                        case "deposito":
                            // Ingresos usualmente son 'OTROS' en contexto de gasto, o null si no aplica
                            c.setTipoPresupuesto(com.example.saveup.model.enums.TipoPresupuesto.OTROS);
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

            // Asegurar que exista la categoría 'Ahorro'
            if (categoriaRepository.findByNombre("Ahorro").isEmpty()) {
                categoriaRepository.save(new Categoria(null, "Ahorro", "ic_savings", "#3F51B5",
                        com.example.saveup.model.enums.TipoPresupuesto.AHORRO));
                System.out.println("Categoría 'Ahorro' creada.");
            }
        }
    }
}
