package ru.subsidy.common.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants.ComponentModel;
import org.mapstruct.ReportingPolicy;

/**
 * Общая конфигурация MapStruct: Spring-бины, незамапленное поле — ошибка компиляции.
 */
@MapperConfig(
        componentModel = ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface BaseMapperConfig {
}
