package ru.subsidy.package_.pkg;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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
        @NotBlank @Size(max = ApplicationData.NAME_MAX) String orgName,
        @NotBlank @Pattern(regexp = "[0-9]{13}") String ogrn,
        @NotNull @Valid Address address,
        @NotBlank @Size(max = ApplicationData.PROJECT_MAX) String project,
        @NotNull @DecimalMin("0.01") @Digits(integer = ApplicationData.MONEY_DIGITS, fraction = 2)
        @Schema(type = "string", example = "150000.00")
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal requestedAmount,
        @NotNull LocalDate applicationDate,
        @NotNull @Valid Signatory signatory,
        @Valid Property property) {

    private static final int NAME_MAX = 500;
    private static final int TEXT_MAX = 2000;
    private static final int PROJECT_MAX = 4000;
    private static final int MONEY_DIGITS = 17;

    /**
     * Адрес заявки.
     *
     * @param value адрес
     * @param purpose назначение: юридический, фактический или адрес проекта
     */
    public record Address(@NotBlank @Size(max = TEXT_MAX) String value,
                          @NotBlank @Pattern(regexp = "LEGAL|ACTUAL|PROJECT") String purpose) {
    }

    /**
     * Подписант заявки.
     *
     * @param fullName ФИО
     * @param position должность
     * @param authority основание полномочий
     */
    public record Signatory(@NotBlank @Size(max = NAME_MAX) String fullName,
                            @NotBlank @Size(max = NAME_MAX) String position,
                            @NotBlank @Size(max = TEXT_MAX) String authority) {
    }

    /**
     * Участок, используемый проектом.
     *
     * @param cadastralNumber кадастровый номер
     * @param address адрес участка
     * @param rightType собственность, аренда или иное пользование
     */
    public record Property(@NotBlank @Pattern(regexp = "[0-9]{2}:[0-9]{2}:[0-9]{6,7}:[0-9]+") String cadastralNumber,
                           @NotBlank @Size(max = TEXT_MAX) String address,
                           @NotBlank @Pattern(regexp = "OWNERSHIP|LEASE|USE") String rightType) {
    }
}
