package com.example.api_gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleCorpoInvalido(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        logger.warn("Corpo de requisicao invalido em {}", request.getRequestURI());
        return resposta(HttpStatus.BAD_REQUEST, "Requisicao invalida.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMetodoNaoSuportado(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED, "Metodo nao permitido.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleTipoNaoSuportado(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return resposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteudo nao suportado.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        logger.error("Unexpected error while handling request {}", request.getRequestURI(), exception);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno. Tente novamente mais tarde.", request);
    }

    private ResponseEntity<ApiErrorResponse> resposta(HttpStatus status, String mensagem,
            HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}