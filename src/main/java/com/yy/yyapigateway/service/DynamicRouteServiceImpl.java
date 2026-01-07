package com.yy.yyapigateway.service;

import com.yy.yyapiinterface.api.DynamicRouteService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.net.URI;

/**
 * @author 阿狸
 * @date 2026-01-07
 */
@DubboService
public class DynamicRouteServiceImpl implements DynamicRouteService {

    @Resource
    private RouteDefinitionWriter routeDefinitionWriter;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;


    @Override
    public void addRoute(String pathName) {
        RouteDefinition routeDefinition = new RouteDefinition();
        routeDefinition.setId(pathName);
        routeDefinition.setUri(URI.create("http://yupi.icu"));

        // 设置predicates
        PredicateDefinition predicateDefinition = new PredicateDefinition();
        predicateDefinition.setName("Path");

        predicateDefinition.addArg("pattern", "/api1/**");

        routeDefinition.getPredicates().add(predicateDefinition);

        // 保存发布
        routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe();
        // 发布事件
        applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
    }
}
