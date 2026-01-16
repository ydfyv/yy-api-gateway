package com.yy.yyapigateway.service;

import com.yy.yyapiinterface.api.DynamicRouteService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.InMemoryRouteDefinitionRepository;
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
@Slf4j
public class DynamicRouteServiceImpl implements DynamicRouteService {

    @Resource
    private RouteDefinitionWriter routeDefinitionWriter;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Resource
    private InMemoryRouteDefinitionRepository routeDefinitionRepository;


    @Override
    public void addRoute(String id, String uri, String patternPath) {
        RouteDefinition routeDefinition = new RouteDefinition();
        routeDefinition.setId(id);
        routeDefinition.setUri(URI.create(uri));

        // 设置predicates
        PredicateDefinition predicateDefinition = new PredicateDefinition();
        predicateDefinition.setName("Path");

        predicateDefinition.addArg("pattern", patternPath + "/**");

        routeDefinition.getPredicates().add(predicateDefinition);

        // 保存发布
        routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe();

        System.out.printf("发布路由， id为%s，uri为%s， 匹配字符串为%s\n", id, uri, patternPath);
        log.info("发布路由， id为{}，uri为{}， 匹配字符串为{}", id, uri, patternPath);
        // 发布事件
        publishEvent(new RefreshRoutesEvent(this));
    }

    @Override
    public void deleteRoute(String id) {
        routeDefinitionWriter.delete(Mono.just(id)).subscribe();
        System.out.printf("删除路由， id为%s\n", id);
        publishEvent(new RefreshRoutesEvent(this));
    }

    @Override
    public void updateRoute(String id, String uri, String patternPath) {
        deleteRoute(id);
        addRoute(id, uri, patternPath);
        System.out.printf("更新路由， id为%s，uri为%s， 匹配字符串为%s\n", id, uri, patternPath);
        log.info("更新路由， id为{}，uri为{}， 匹配字符串为{}", id, uri, patternPath);
    }

    private void publishEvent(RefreshRoutesEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
