/**
 * @author 阿狸
 * @date 2026/1/16
 */
package com.yy.yyapigateway.filter;

import com.yy.yyapiinterface.api.InnerInterfaceInfoService;
import com.yy.yyapiinterface.api.InnerUserService;
import com.yy.yyapimodel.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

//1. 用户发送请求到API网关
//2. 请求日志
//3. （黑白名单）
//4. 用户鉴权（判断ak、sk是否合法）
//5. 请求的模拟接口是否存在
//6. 请求转发、调用模拟接口
//7. 响应日志
//8. 调用成功、接口调用次数 + 1
//9. 调用失败，返回一个规范的错误码

@Slf4j
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    @DubboReference(check = false)
    private InnerUserService innerUserService;

    @DubboReference(check = false)
    private InnerInterfaceInfoService innerInterfaceInfoService;

    private static final List<String> blackList = new ArrayList<>();
    public static final List<Pattern> whitePathList = new ArrayList<>();

    static {
        blackList.add("12568.5454");
        whitePathList.add(Pattern.compile("^/api(/.*)?$"));
    }

    @Override
    public int getOrder() {
        return -20; // 在路由前执行
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        // === 第一步：黑白名单（示例：黑名单IP拦截，此处略）===

        // === 第二步：判断是否需要处理（比如只处理 /api 开头的）===
        boolean isApiRequest = whitePathList.stream().anyMatch(p -> p.matcher(path).matches());
        if (isApiRequest) {
            return chain.filter(exchange); // 非API请求直接放行
        }

        // === 第三步：【请求染色】从 Cookie 提取值，注入 Header ===
        ServerHttpRequest mutatedRequest = addCookieToHeader(request);

        try {
            // === 第四步：获取关键 Header 用于鉴权 ===
            HttpHeaders headers = mutatedRequest.getHeaders();
            String accessKey = getFirstHeader(headers, "accessKey");

            // === 第六步：接口是否存在 ===
            HttpMethod method = mutatedRequest.getMethod();
            if (!innerInterfaceInfoService.validateInterfaceAccess(path, String.valueOf(method))) {
                return forbidden(exchange, "接口不存在或不可访问");
            }

            String secretKey = innerUserService.getSecretKey(accessKey);
            String sign = SignUtils.sign(secretKey, path, String.valueOf(method));

            // === 第五步：用户鉴权 ===
            if (!validateAccess(accessKey, path, String.valueOf(method), sign)) {
                return unauthorized(exchange, "无效的 accessKey 或签名");
            }


            // === 第七步：调用次数 +1（异步，不影响主流程）===
            increaseInvokeCountAsync(accessKey, path);

            // === 第八步：记录请求日志（可选）===
            logRequest(mutatedRequest, path);

        } catch (Exception e) {
            return writeErrorResponse(exchange, 500, e.getMessage());
        }
        // 继续链路（使用染色后的请求）
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    // 👇 核心：Cookie 染色
    private ServerHttpRequest addCookieToHeader(ServerHttpRequest request) {
        // 示例：从 Cookie 中提取 JSESSIONID，放入 X-Session-ID
        String sessionId = Optional.ofNullable(request.getCookies().getFirst("JSESSIONID"))
                .map(cookie -> cookie.getValue())
                .orElse("");

        // 也可以提取其他 Cookie，如 user_id
        String userId = Optional.ofNullable(request.getCookies().getFirst("user_id"))
                .map(cookie -> cookie.getValue())
                .orElse("");

        ServerHttpRequest.Builder builder = request.mutate();

        if (!sessionId.isEmpty()) {
            builder.header("X-Session-ID", sessionId);
        }
        if (!userId.isEmpty()) {
            builder.header("X-User-ID", userId);
        }

        // 流量染色
        builder.header("auth", "yy-api-gateway-secret");

        return builder.build();
    }

    private String getFirstHeader(HttpHeaders headers, String name) {
        return Optional.ofNullable(headers.getFirst(name)).orElse("");
    }

    private boolean validateAccess(String accessKey, String path, String method, String signed) {
        if (accessKey.isEmpty()) {
            return false;
        }
        // 注意：这里不要传整个 body（避免重复读），可考虑用请求路径+方法+时间戳等生成签名
        // 如果必须用 body，需提前缓存（复杂，建议改用 header 签名）
        return innerUserService.isAccessible(accessKey, path, method, signed);
    }

    private void increaseInvokeCountAsync(String accessKey, String path) {
        innerInterfaceInfoService.increaseInvokeCount(accessKey, path);
    }

    private void logRequest(ServerHttpRequest request, String path) {
        String method = request.getMethodValue();
        String params = getQueryParams(request);
        String headers = formatHeaders(request.getHeaders());
        log.info("[GATEWAY] 请求: {} {} | Params: {} | Headers: {}", method, path, params, headers);
    }

    private String getQueryParams(ServerHttpRequest request) {
        return request.getQueryParams().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(v -> entry.getKey() + "=" + v))
                .collect(Collectors.joining("&"));
    }

    private String formatHeaders(HttpHeaders headers) {
        return headers.entrySet().stream()
                .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                .collect(Collectors.joining(" | "));
    }

    // 返回 401
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeErrorResponse(exchange, 401, message);
    }

    // 返回 403
    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeErrorResponse(exchange, 403, message);
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, int status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(org.springframework.http.HttpStatus.valueOf(status));
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        byte[] bytes = ("{\"code\":" + status + ",\"message\":\"" + message + "\"}").getBytes();
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}