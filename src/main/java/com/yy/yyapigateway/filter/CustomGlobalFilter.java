package com.yy.yyapigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author 阿狸
 * @date 2026-01-04
 */
@Slf4j
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return -20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest serverHttpRequest = exchange.getRequest();
        // 获取原始响应对象和数据缓冲工厂
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        // 原始响应对象，用于拦截和修改响应内容
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            /**
             * 重写writeWith方法拦截响应体
             */
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = Flux.from(body);

                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        // 获取一些需要打印的参数
                        long timestamp = System.currentTimeMillis();
                        HttpMethod method = serverHttpRequest.getMethod();
                        String requestUrl = serverHttpRequest.getPath().toString();
//                        String userId = Optional.ofNullable(serverHttpRequest.getHeaders().getFirst(AuthConstants.USER_ID))
//                                .filter(StringUtils::isNotBlank).orElse("未登录");
//                        String ip = IPUtils.getIpAddrByServerHttpRequest(serverHttpRequest);
                        String params = getRequestparams(serverHttpRequest, exchange);
                        String headers = formatHeaders(serverHttpRequest.getHeaders());

                        log.info("{} ========================接口详细日志========================", timestamp);
                        log.info("{} 请求方式：{}  请求路径: {}", timestamp, method, requestUrl);
                        log.info("{} 请求参数: {}", timestamp, params);
                        log.info("{} 请求头: {}", timestamp, headers);

//                        log.info("{} 用户ID: {}  访问IP: {}  访问时间：{}", timestamp, userId, ip, new Date());

//                        // 判断是否需要打印响应
//                        if (isUpdateDate(method, requestUrl)) {
                        // 创建数据缓冲工厂和缓冲区，用于读取响应内容
                        DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
                        DataBuffer buff = dataBufferFactory.join(dataBuffers);
                        byte[] content = new byte[buff.readableByteCount()];
                        buff.read(content);
                        // 释放缓冲区资源
                        DataBufferUtils.release(buff);

                        // 获取响应内容类型
                        MediaType contentType = originalResponse.getHeaders().getContentType();
                        if (!MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                            // 如果不是JSON类型，直接返回原始内容，不进行处理
                            log.info("{} ===============================================================", timestamp);
                            return bufferFactory.wrap(content);
                        }

                        // 将字节数组转换为字符串 对响应体进行统一格式化处理
                        String result = new String(content);
                        log.info("{} 响应结果: {}", timestamp, result);
                        log.info("{} ===============================================================", timestamp);

                        getDelegate().getHeaders().setContentLength(result.getBytes().length);
                        return bufferFactory.wrap(result.getBytes());
//                        } else {
                        // 不需要打印响应结果时，直接合并并返回原始数据
//                            log.info("{} ===============================================================", timestamp);
//                            DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
//                            DataBuffer joinedBuffer = dataBufferFactory.join(dataBuffers);
//                            byte[] content = new byte[joinedBuffer.readableByteCount()];
//                            joinedBuffer.read(content);
//                            DataBufferUtils.release(joinedBuffer);
//                            return bufferFactory.wrap(content);
//                        }
                    }));
                } else {
                    return super.writeWith(body);
                }
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }


    private static String getRouteName(String requestUrl) {
        String serviceUrl = requestUrl.substring(requestUrl.indexOf("/") + 1);
        log.info("getRouteName: " + serviceUrl.substring(0, serviceUrl.indexOf("/")));
        return serviceUrl.substring(0, serviceUrl.indexOf("/"));
    }

//    /**
//     * 获取请求token
//     */
//    private String getToken(ServerHttpRequest request) {
//        String token = request.getHeaders().getFirst(jwtProperties.getHeader());
//        // 如果前端设置了令牌前缀，则裁剪掉前缀
//        if (StringUtils.isNotEmpty(token) && token.startsWith(TokenConstants.PREFIX)) {
//            token = token.replaceFirst(TokenConstants.PREFIX, StringUtils.EMPTY);
//        }
//        return token;
//    }




    /**
     * 获取去除路由后的path
     *
     * @param requestUrl
     * @return
     */
    private static String getPath(String requestUrl) {
        String path = requestUrl.substring(1);
        log.info("getPath: " + path.substring(path.indexOf("/")));
        return path.substring(path.indexOf("/"));
    }


//    /**
//     * 判断是否为增删改接口  只有增删改接口才会打印响应结果
//     */
//    private boolean isUpdateDate(HttpMethod method, String requestUrl){
//        switch (method) {
//            case PUT:
//            case DELETE:
//                return true;
//            case GET:
//                return sysPrintLogService.checkNeedPrint_GET(requestUrl);
//            case POST:
//                return sysPrintLogService.checkNeedPrint_POST(requestUrl);
//            default:
//                return false;
//        }
//    }

    /**
     * 获取请求参数
     */
    private String getRequestparams(ServerHttpRequest serverHttpRequest, ServerWebExchange exchange) {
        HttpMethod method = serverHttpRequest.getMethod();

        // 检查是否为文件上传请求，如果是则不打印参数
        MediaType contentType = serverHttpRequest.getHeaders().getContentType();
        if (contentType != null && (contentType.includes(MediaType.MULTIPART_FORM_DATA)
                || contentType.includes(MediaType.APPLICATION_OCTET_STREAM))) {
            return "";
        }

        if (HttpMethod.GET.equals(method) || HttpMethod.DELETE.equals(method)) {
            StringBuilder params = new StringBuilder();
            serverHttpRequest.getQueryParams().forEach((key, value) -> {
                value.forEach(v -> params.append(key).append("=").append(v).append("&"));
            });
            // 移除末尾的 "&"
            if (params.length() > 0) {
                params.deleteCharAt(params.length() - 1);
            }
            return params.toString();
        } else if (HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method)) {
            return getBodyContent(exchange);
        }
        return "";
    }

    // 从其他filter中copy过来的 目的是获取post请求的body
    private String getBodyContent(ServerWebExchange exchange){
        Flux<DataBuffer> body = exchange.getRequest().getBody();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        // 缓存读取的request body信息
        body.subscribe(dataBuffer -> {
            CharBuffer charBuffer = StandardCharsets.UTF_8.decode(dataBuffer.asByteBuffer());
            DataBufferUtils.release(dataBuffer);
            bodyRef.set(charBuffer.toString());
        });
        //获取request body
        return bodyRef.get();
    }

    /**
     * 修改响应体内容，统一JSON数据格式
     */
//    private String modifyBody(String str){
//        JSONObject json = JSON.parseObject(str, Feature.AllowISO8601DateFormat);
//        JSONObject.DEFFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
//        return JSONObject.toJSONString(json, (ValueFilter) (object, name, value) ->
//                value == null ? "" : value, SerializerFeature.WriteDateUseDateFormat);
//    }

    /**
     * 格式化请求头
     * @param headers 请求托
     * @return 格式化后的请求头
     */
    private String formatHeaders(org.springframework.http.HttpHeaders headers) {
        return headers.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining(" | "));
    }

}