package com.example.saveup.config;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;
import java.sql.SQLException;

@Configuration
public class DataSourceConfig {

    // Inyectamos los valores desde application-dev.properties
    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.url}")
    private String url;

    @Bean
    public DataSource dataSource() throws SQLException {
        // 1. Usamos la fábrica de Oracle para crear una instancia del PoolDataSource (UCP).
        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();

        // 2. Le pasamos los parámetros de conexión que leímos del .properties.
        dataSource.setUser(username);
        dataSource.setPassword(password);
        dataSource.setURL(url);
        
        // 3. Establecemos propiedades críticas para el funcionamiento del pool.
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setFastConnectionFailoverEnabled(true);
        dataSource.setInitialPoolSize(5); // Número de conexiones iniciales.
        dataSource.setMinPoolSize(5);
        dataSource.setMaxPoolSize(20); // Número máximo de conexiones.

        System.out.println("DataSource de Oracle UCP configurado manualmente.");

        // 4. Devolvemos el DataSource configurado. Spring Boot lo usará para todo.
        return dataSource;
    }
}


