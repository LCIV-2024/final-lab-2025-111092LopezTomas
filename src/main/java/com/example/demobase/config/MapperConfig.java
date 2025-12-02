package com.example.demobase.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clase de configuración para ModelMapper.
 *
 * ModelMapper se usa para convertir automáticamente entidades ↔ DTOs,
 * evitando escribir código manual de mapeo.
 */
@Configuration
public class MapperConfig {

    /**
     * Registra un único bean de ModelMapper en el contexto de Spring.
     *
     * Esto permite inyectarlo en cualquier clase con @Autowired o @RequiredArgsConstructor.
     */
    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }
}

