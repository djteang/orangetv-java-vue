package com.orangetv.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangetv.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestControllerAdvice
@RequiredArgsConstructor
public class ResponseWrapper implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 跳过带 @SkipWrapper 的方法或类
        if (returnType.hasMethodAnnotation(SkipWrapper.class)) {
            return false;
        }
        if (returnType.getDeclaringClass().isAnnotationPresent(SkipWrapper.class)) {
            return false;
        }
        return true;
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // 已经是 ApiResponse，直接放行
        if (body instanceof ApiResponse) {
            return body;
        }
        // 二进制/流式类型跳过
        if (body instanceof byte[] || body instanceof Resource || body instanceof SseEmitter) {
            return body;
        }
        // String 类型需要特殊处理（Spring 用 StringHttpMessageConverter）
        if (body instanceof String) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return objectMapper.writeValueAsString(ApiResponse.success(body));
        }
        return ApiResponse.success(body);
    }
}
