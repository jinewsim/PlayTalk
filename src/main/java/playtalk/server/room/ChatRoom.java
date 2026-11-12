package playtalk.server.room;

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
        return members.remove(user);
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
