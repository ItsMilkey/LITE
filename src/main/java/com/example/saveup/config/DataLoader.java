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
                    new Categoria(null, "Sueldo", "ic_work", "#009688"),
                    new Categoria(null, "Comida", "ic_fastfood", "#FFC107"),
                    new Categoria(null, "Transporte", "ic_directions_car", "#2196F3"),
                    new Categoria(null, "Cuentas y Servicios", "ic_receipt_long", "#4CAF50"),
                    new Categoria(null, "Ocio y Entretenimiento", "ic_sports_esports", "#E91E63"),
                    new Categoria(null, "Salud y Bienestar", "ic_medical_services", "#F44336"),
                    new Categoria(null, "Ropa y Accesorios", "ic_checkroom", "#9C27B0"),
                    new Categoria(null, "Deudas", "ic_payment", "#795548"),
                    new Categoria(null, "Otro", "ic_label", "#607D8B")
                );

                categoriaRepository.saveAll(categorias);
                System.out.println("Categorías iniciales guardadas con éxito.");
            } else {
                System.out.println("La tabla de categorías ya tiene datos. No se requiere población inicial.");
            }
        }
    }
    