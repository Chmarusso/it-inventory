package com.example.inventory.common

import com.example.inventory.allocation.ConflictException
import com.example.inventory.allocation.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiError(
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun validation(exception: MethodArgumentNotValidException): ApiError =
        ApiError(
            message = "Validation failed",
            fieldErrors = exception.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid value") },
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun malformed() = ApiError("The request body contains an invalid or missing value")

    @ExceptionHandler(NotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(exception: NotFoundException) = ApiError(exception.message ?: "Not found")

    @ExceptionHandler(ConflictException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun conflict(exception: ConflictException) = ApiError(exception.message ?: "Conflict")
}
