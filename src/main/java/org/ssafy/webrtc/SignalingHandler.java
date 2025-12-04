package org.ssafy.webrtc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SignalingHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // roomId → Set<Peer>
    private final Map<String, Set<Peer>> rooms = new ConcurrentHashMap<>();

    // session → peer name
    private final Map<WebSocketSession, String> sessionNameMap = new ConcurrentHashMap<>();

    @Data
    @AllArgsConstructor
    static class Peer {
        private String name;
        private WebSocketSession session;
    }


    //처음 연결할때
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
    }


    //정보 정송
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        JsonNode json = objectMapper.readTree(message.getPayload());
        String type = json.get("type").asText();
        String roomId = json.get("roomId").asText();

        switch (type) {

            case "join" -> handleJoin(session, json, roomId);

            case "offer", "answer", "ice", "chat" ->
                    relayToOthers(session, roomId, json);

            default ->
                    log.warn("Unknown type {}", type);
        }
    }


    //입장처리
    private void handleJoin(WebSocketSession session, JsonNode json, String roomId) throws IOException {

        String name = json.get("name").asText();

        rooms.putIfAbsent(roomId, ConcurrentHashMap.newKeySet());
        Set<Peer> peers = rooms.get(roomId);

        // 1) 방의 기존 유저 목록을 join 한 사람에게 보내기
        List<String> existingNames = peers.stream()
                .map(Peer::getName)
                .toList();

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of(
                        "type", "peers",
                        "roomId", roomId,
                        "peers", existingNames
                )
        )));

        // 2) 기존 유저들에게 "peer-joined" 브로드캐스트
        for (Peer p : peers) {
            p.getSession().sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of(
                            "type", "peer-joined",
                            "roomId", roomId,
                            "peer", name
                    )
            )));
        }

        // 3) 자기 자신을 방에 등록
        peers.add(new Peer(name, session));
        sessionNameMap.put(session, name);

        log.info("Peer {} joined room {}", name, roomId);
    }


    //중계
    private void relayToOthers(WebSocketSession sender, String roomId, JsonNode json) throws IOException {
        Set<Peer> peers = rooms.getOrDefault(roomId, Collections.emptySet());
        String senderName = sessionNameMap.get(sender);

        for (Peer p : peers) {
            if (!p.getSession().isOpen()) continue;
            if (p.getSession().getId().equals(sender.getId())) continue;

            p.getSession().sendMessage(new TextMessage(objectMapper.writeValueAsString(json)));
        }
    }


    //종료
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        String name = sessionNameMap.remove(session);

        rooms.forEach((roomId, peers) -> {

            boolean removed = peers.removeIf(p -> p.getSession().equals(session));

            if (removed) {
                // 모든 사람에게 peer-left 브로드캐스트
                for (Peer p : peers) {
                    try {
                        p.getSession().sendMessage(new TextMessage(objectMapper.writeValueAsString(
                                Map.of("type", "peer-left", "peer", name)
                        )));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });

        log.info("WebSocket closed {}, peer={}", session.getId(), name);
    }
}
