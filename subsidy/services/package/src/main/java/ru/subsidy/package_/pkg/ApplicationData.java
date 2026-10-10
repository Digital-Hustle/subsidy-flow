package ru.subsidy.package_.pkg;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Снимок заявки ООО: сведения принадлежат пакету, а не общей карточке организации.
 *
 * @param orgInn ИНН ООО
 * @param orgName наименование
 * @param ogrn ОГРН
 * @param address адрес с назначением
 * @param project проект
 * @param requestedAmount запрашиваемая сумма
 * @param applicationDate дата заявки
 * @param signatory подписант
 * @param property участок, если применимо
 */
public record ApplicationData(
        @NotBlank @Pattern(regexp = "[0-9]{10}") String orgInn,
        @NotBlank @Size(max = 500) String orgName,
        @NotBlank @Pattern(regexp = "[0-9]{13}") String ogrn,
        @NotNull @Valid Address address,
        @NotBlank @Size(max = 4000) String project,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2)
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal requestedAmount,
        @NotNull LocalDate applicationDate,
        @NotNull @Valid Signatory signatory,
        @Valid Property property) {

    /**
     * Адрес заявки.
     *
     * @param value адрес
     * @param purpose назначение: юридический, фактический или адрес проекта
     */
    public record Address(@NotBlank @Size(max = 2000) String value,
                          @NotBlank @Pattern(regexp = "LEGAL|ACTUAL|PROJECT") String purpose) {
    }

    /**
     * Подписант заявки.
     *
     * @param fullName ФИО
     * @param position должность
     * @param authority основание полномочий
     */
    public record Signatory(@NotBlank @Size(max = 500) String fullName,
                            @NotBlank @Size(max = 500) String position,
                            @NotBlank @Size(max = 2000) String authority) {
    }

    /**
     * Участок, используемый проектом.
     *
     * @param cadastralNumber кадастровый номер
     * @param address адрес участка
     * @param rightType собственность, аренда или иное пользование
     */
    public record Property(@NotBlank @Pattern(regexp = "[0-9]{2}:[0-9]{2}:[0-9]{6,7}:[0-9]+") String cadastralNumber,
                           @NotBlank @Size(max = 2000) String address,
                           @NotBlank @Pattern(regexp = "OWNERSHIP|LEASE|USE") String rightType) {
    }
}
