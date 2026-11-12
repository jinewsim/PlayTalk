package playtalk.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 접속 중인 사용자(닉네임 → 연결) 관리. 닉네임 중복 검사도 여기서 한다. */
public class UserManager {

    private final Map<String, ClientHandler> users = new ConcurrentHashMap<>();

    /** @return 등록 성공 여부 (이미 사용 중인 닉네임이면 false) */
    public boolean register(String nickname, ClientHandler client) {
        return users.putIfAbsent(nickname, client) == null;
    }

    /** 연결이 끊긴 사용자의 닉네임을 반납한다. */
    public void remove(ClientHandler client) {
        if (client.nickname() != null) {
            users.remove(client.nickname(), client);
        }
    }

    public ClientHandler find(String nickname) {
        return nickname == null ? null : users.get(nickname);
    }

    public Collection<ClientHandler> all() {
        return users.values();
    }

    public List<String> nicknames() {
        List<String> list = new ArrayList<>(users.keySet());
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }
}
