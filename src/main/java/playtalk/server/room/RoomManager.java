package playtalk.server.room;

import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.common.Protocol;
import playtalk.server.ClientHandler;
import playtalk.server.Log;
import playtalk.server.MessageRouter;
import playtalk.server.PlayTalkServer;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 채팅방 생성/입장/퇴장과 채팅 메시지 전달.
 *
 * <p>잠금 순서 규칙: RoomManager의 잠금을 잡은 상태에서는 다른 매니저(GameManager 등)를 호출하지 않는다.
 * 다른 매니저에 알려야 할 일은 잠금을 푼 뒤 {@link #addLeaveListener 퇴장 리스너}로 전달한다.
 */
public class RoomManager {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);

    private final PlayTalkServer server;
    private final Map<Integer, ChatRoom> rooms = new LinkedHashMap<>();
    private final List<BiConsumer<ClientHandler, ChatRoom>> leaveListeners = new CopyOnWriteArrayList<>();
    private int nextId = 1;

    public RoomManager(PlayTalkServer server) {
        this.server = server;
    }

    public void registerHandlers(MessageRouter router) {
        router.on(MessageType.ROOM_CREATE, this::handleCreate);
        router.on(MessageType.ROOM_JOIN, this::handleJoin);
        router.on(MessageType.ROOM_LEAVE, (user, msg) -> leave(user));
        router.on(MessageType.CHAT, this::handleChat);
    }

    /** 사용자가 방을 나갈 때(연결 끊김 포함) 호출될 리스너. 잠금 밖에서 호출된다. */
    public void addLeaveListener(BiConsumer<ClientHandler, ChatRoom> listener) {
        leaveListeners.add(listener);
    }

    // ------------------------------------------------------------------ handlers

    private void handleCreate(ClientHandler user, Message msg) {
        String title = msg.get("title").trim();
        int max = msg.getInt("max", Protocol.ROOM_MAX_USERS);
        if (title.isEmpty() || title.length() > Protocol.ROOM_TITLE_MAX_LENGTH) {
            fail(user, "방 제목은 1~" + Protocol.ROOM_TITLE_MAX_LENGTH + "자로 입력하세요.");
            return;
        }
        if (max < Protocol.ROOM_MIN_USERS || max > Protocol.ROOM_MAX_USERS) {
            fail(user, "최대 인원은 " + Protocol.ROOM_MIN_USERS + "~" + Protocol.ROOM_MAX_USERS + "명으로 설정하세요.");
            return;
        }
        leave(user);
        ChatRoom room;
        synchronized (this) {
            room = new ChatRoom(nextId++, title, max, user.nickname());
            rooms.put(room.getId(), room);
            room.add(user);
            user.setRoom(room);
        }
        Log.info("방 생성 #%d '%s' (최대 %d명, 방장 %s)", room.getId(), title, max, user.nickname());
        afterJoin(user, room);
    }

    private void handleJoin(ClientHandler user, Message msg) {
        int roomId = msg.getInt("roomId", -1);
        ChatRoom target;
        synchronized (this) {
            target = rooms.get(roomId);
        }
        if (target == null) {
            fail(user, "존재하지 않는 채팅방입니다.");
            return;
        }
        if (target == user.getRoom()) {
            return;
        }
        if (target.isFull()) { // 지금 방을 떠나기 전에 먼저 확인
            fail(user, "채팅방 인원이 가득 찼습니다. (" + target.size() + "/" + target.getMaxUsers() + ")");
            return;
        }
        leave(user);
        synchronized (this) {
            if (!rooms.containsKey(roomId)) {
                fail(user, "채팅방이 이미 닫혔습니다.");
                return;
            }
            if (target.isFull()) {
                fail(user, "채팅방 인원이 가득 찼습니다. (" + target.size() + "/" + target.getMaxUsers() + ")");
                return;
            }
            target.add(user);
            user.setRoom(target);
        }
        afterJoin(user, target);
    }

    private void handleChat(ClientHandler user, Message msg) {
        ChatRoom room = user.getRoom();
        if (room == null) {
            user.sendError("채팅방에 먼저 입장하세요.");
            return;
        }
        String text = msg.get("text");
        if (text.isBlank()) return;
        if (text.length() > Protocol.CHAT_MAX_LENGTH) {
            text = text.substring(0, Protocol.CHAT_MAX_LENGTH);
        }
        room.broadcast(Message.of(MessageType.CHAT)
                .put("roomId", room.getId())
                .put("from", user.nickname())
                .put("text", text)
                .put("time", LocalTime.now().format(TIME_FMT)));
    }

    // ------------------------------------------------------------------ join / leave

    private void afterJoin(ClientHandler user, ChatRoom room) {
        user.send(Message.of(MessageType.ROOM_JOINED)
                .put("roomId", room.getId())
                .put("title", room.getTitle())
                .put("max", room.getMaxUsers())
                .put("owner", room.getOwner()));
        room.broadcast(Message.of(MessageType.SYSTEM).put("text", user.nickname() + " 님이 입장했습니다."));
        broadcastMembers(room);
        broadcastRoomList();
    }

    /** 현재 방에서 나간다. 방이 비면 삭제하고, 방장이 나가면 다음 사람에게 넘긴다. */
    public void leave(ClientHandler user) {
        ChatRoom room;
        boolean removedRoom = false;
        String newOwner = null;
        synchronized (this) {
            room = user.getRoom();
            if (room == null) return;
            room.remove(user);
            user.setRoom(null);
            if (room.size() == 0) {
                rooms.remove(room.getId());
                removedRoom = true;
            } else if (user.nickname().equals(room.getOwner())) {
                newOwner = room.members().get(0).nickname();
                room.setOwner(newOwner);
            }
        }
        user.send(Message.of(MessageType.ROOM_LEFT).put("roomId", room.getId()));
        if (!removedRoom) {
            room.broadcast(Message.of(MessageType.SYSTEM).put("text", user.nickname() + " 님이 퇴장했습니다."));
            if (newOwner != null) {
                room.broadcast(Message.of(MessageType.SYSTEM).put("text", newOwner + " 님이 새 방장이 되었습니다."));
            }
            broadcastMembers(room);
        } else {
            Log.info("방 삭제 #%d '%s'", room.getId(), room.getTitle());
        }
        broadcastRoomList();
        for (BiConsumer<ClientHandler, ChatRoom> l : leaveListeners) {
            l.accept(user, room);
        }
    }

    // ------------------------------------------------------------------ broadcast

    public void broadcastMembers(ChatRoom room) {
        if (room == null) return;
        List<String[]> rows = new ArrayList<>();
        for (ClientHandler m : room.members()) {
            rows.add(new String[]{m.nickname(), m.isInGame() ? Protocol.STATUS_IN_GAME : Protocol.STATUS_IDLE});
        }
        room.broadcast(Message.of(MessageType.ROOM_MEMBERS)
                .put("roomId", room.getId())
                .put("owner", room.getOwner())
                .putTable("members", rows));
    }

    public Message roomListMessage() {
        List<String[]> rows = new ArrayList<>();
        synchronized (this) {
            for (ChatRoom r : rooms.values()) {
                rows.add(new String[]{
                        String.valueOf(r.getId()), r.getTitle(),
                        String.valueOf(r.size()), String.valueOf(r.getMaxUsers()), r.getOwner()});
            }
        }
        return Message.of(MessageType.ROOM_LIST).putTable("rooms", rows);
    }

    public void broadcastRoomList() {
        server.broadcastAll(roomListMessage());
    }

    private void fail(ClientHandler user, String reason) {
        user.send(Message.of(MessageType.ROOM_FAIL).put("reason", reason));
    }
}
