package playtalk.server.room;

import playtalk.common.MessageType;

import playtalk.common.Message;
import playtalk.server.ClientHandler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 채팅방 하나.
 * 멤버 목록 변경은 {@link RoomManager}의 잠금 안에서만 일어나고, 외부에는 복사본만 준다.
 */
public class ChatRoom {

    private final int id;
    private final String title;
    private final int maxUsers;
    private final Set<ClientHandler> members = new LinkedHashSet<>();
    private volatile String owner;
    /** 닉네임 → 어디까지 읽었는지 (채팅 seq). 방에 있는 사람만 보관한다 */
    private final java.util.Map<String, Long> readSeq = new java.util.LinkedHashMap<>();

    ChatRoom(int id, String title, int maxUsers, String owner) {
        this.id = id;
        this.title = title;
        this.maxUsers = maxUsers;
        this.owner = owner;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public String getOwner() {
        return owner;
    }

    void setOwner(String owner) {
        this.owner = owner;
    }

    synchronized boolean add(ClientHandler user) {
        return members.add(user);
    }

    synchronized boolean remove(ClientHandler user) {
        readSeq.remove(user.nickname());
        return members.remove(user);
    }

    /** 읽은 위치를 앞으로만 옮긴다. 바뀌었으면 true */
    synchronized boolean markRead(String nickname, long seq) {
        Long old = readSeq.get(nickname);
        if (old != null && old >= seq) return false;
        readSeq.put(nickname, seq);
        return true;
    }

    /** CHAT_READ 메시지: 방 사람들이 각자 어디까지 읽었는지 */
    public synchronized Message readMessage() {
        List<String[]> rows = new ArrayList<>();
        for (ClientHandler m : members) {
            rows.add(new String[]{m.nickname(), String.valueOf(readSeq.getOrDefault(m.nickname(), 0L))});
        }
        return Message.of(MessageType.CHAT_READ).put("roomId", id).putTable("reads", rows);
    }

    public synchronized int size() {
        return members.size();
    }

    public synchronized boolean isFull() {
        return members.size() >= maxUsers;
    }

    public synchronized boolean contains(ClientHandler user) {
        return members.contains(user);
    }

    /** 멤버 목록 복사본 (입장 순서) */
    public synchronized List<ClientHandler> members() {
        return new ArrayList<>(members);
    }

    public void broadcast(Message message) {
        for (ClientHandler m : members()) {
            m.send(message);
        }
    }
}
