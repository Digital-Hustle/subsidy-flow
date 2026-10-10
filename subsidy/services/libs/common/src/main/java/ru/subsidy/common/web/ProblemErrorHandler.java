package ru.subsidy.common.web;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import ru.subsidy.common.exception.ErrorCode;

/**
 * Ошибки самого фреймворка (разбор запроса, валидация, размер загрузки) — тоже
 * {@code application/problem+json} с кодом из docs/API.md, а не стандартный ответ Spring (FR-06).
 *
 * <p>Порядок — самый низкий: доменные обработчики сервисов срабатывают первыми.</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProblemErrorHandler {

    /**
     * Тело запроса не прошло валидацию.
     *
     * @param exception исключение
     * @return problem+json со списком полей
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalidBody(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Запрос заполнен неверно", "Проверьте поля запроса");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Параметры метода не прошли валидацию.
     *
     * @param exception исключение
     * @return problem+json
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalidParameters(HandlerMethodValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Параметры запроса заполнены неверно",
                "Проверьте параметры запроса");
    }

    /**
     * Не передан обязательный параметр.
     *
     * @param exception исключение
     * @return problem+json
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail missingParameter(MissingServletRequestParameterException exception) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Не хватает параметра «" + exception.getParameterName() + "»",
                "Добавьте параметр «" + exception.getParameterName() + "» в запрос");
    }

    /**
     * Параметр не того типа (например, не UUID).
     *
     * @param exception исключение
     * @return problem+json
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail wrongParameterType(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Значение параметра «" + exception.getName() + "» не подходит", "Проверьте формат значения");
    }

    /**
     * Тело запроса не разобрано.
     *
     * @param exception исключение
     * @return problem+json
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail unreadableBody(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Тело запроса не разобрано",
                "Отправьте корректный JSON");
    }

    /**
     * Загрузка больше разрешённого размера.
     *
     * @param exception исключение
     * @return problem+json
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException exception) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE, "Файл больше разрешённого размера",
                "Разделите документ или уменьшите разрешение скана");
    }

    /**
     * Создаёт problem+json.
     *
     * @param status статус
     * @param code код из docs/API.md
     * @param detail текст для человека
     * @param hint что сделать
     * @return problem+json
     */
    public static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail, String hint) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(code.code());
        problem.setDetail(detail);
        problem.setProperty("code", code.code());
        problem.setProperty("hint", hint);
        return problem;
    }
}
