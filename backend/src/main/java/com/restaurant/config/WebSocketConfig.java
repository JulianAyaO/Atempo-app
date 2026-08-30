package com.restaurant.config;

import com.restaurant.auth.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String[] ALLOWED_ORIGIN_PATTERNS = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "http://10.*:*",
        "http://172.16.*:*",
        "http://172.17.*:*",
        "http://172.18.*:*",
        "http://172.19.*:*",
        "http://172.20.*:*",
        "http://172.21.*:*",
        "http://172.22.*:*",
        "http://172.23.*:*",
        "http://172.24.*:*",
        "http://172.25.*:*",
        "http://172.26.*:*",
        "http://172.27.*:*",
        "http://172.28.*:*",
        "http://172.29.*:*",
        "http://172.30.*:*",
        "http://172.31.*:*",
        "http://192.168.*:*",
        "https://*.loca.lt",
        "https://*.ngrok-free.app",
        "https://*.ngrok.io"
    };

    private final JwtUtil jwtUtil;

    public WebSocketConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS)
                .withSockJS();

        // Endpoint sin SockJS para clientes nativos
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    if (accessor.getUser() == null) {
                        authenticate(accessor);
                    }
                    authorizeSubscription(accessor);
                }

                return message;
            }
        });
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return;

        String token = header.substring(7);
        if (!jwtUtil.isValid(token)) return;

        Claims claims = jwtUtil.parseToken(token);
        String role = claims.get("role", String.class);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
            claims.getSubject(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role))
        ));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        if (destination.startsWith("/topic/kitchen/")) {
            requireAnyRole(accessor, "KITCHEN", "ADMIN");
        } else if (destination.startsWith("/topic/waiters/")) {
            requireAnyRole(accessor, "WAITER", "ADMIN");
        } else if (destination.startsWith("/topic/admin/")) {
            requireAnyRole(accessor, "ADMIN");
        }
    }

    private void requireAnyRole(StompHeaderAccessor accessor, String... roles) {
        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)) {
            throw new AccessDeniedException("Token requerido para suscribirse a este tópico");
        }

        for (String role : roles) {
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role))) {
                return;
            }
        }
        throw new AccessDeniedException("Rol insuficiente para suscribirse a este tópico");
    }
}
