package playtalk.server.friend;

import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.server.ClientHandler;
import playtalk.server.Log;
import playtalk.server.MessageRouter;
import playtalk.server.PlayTalkServer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 친구 요청/수락/거절/삭제 처리와 친구 목록 전송.
 *
 * <p>친구 목록이 바뀌거나 친구가 접속/종료하면 관련된 사람에게 {@code FRIEND_LIST} 전체를 다시 보낸다.
 * 목록이 작아서 바뀐 부분만 보내는 것보다 단순하고, 클라이언트 화면이 서버 상태와 어긋날 일이 없다.
 */
public class FriendManager {

    private final PlayTalkServer server;
    private final FriendStore store;

    public FriendManager(PlayTalkServer server, FriendStore store) {
        this.server = server;
        this.store = store;
    }

    public void registerHandlers(MessageRouter router) {
        router.on(MessageType.FRIEND_REQUEST, this::handleRequest);
        router.on(MessageType.FRIEND_REPLY, this::handleReply);
        router.on(MessageType.FRIEND_REMOVE, this::handleRemove);
    }

    // ------------------------------------------------------------------ handlers

    private void handleRequest(ClientHandler user, Message msg) {
        String me = user.nickname();
        String target = msg.get("target").trim();
        if (target.isEmpty()) {
            fail(user, "친구로 추가할 닉네임을 입력하세요.");
            return;
        }
        if (target.equals(me)) {
            fail(user, "자기 자신은 친구로 추가할 수 없습니다.");
            return;
        }
        if (!server.accounts().exists(target)) {
            fail(user, "'" + target + "' 님은 가입되지 않은 사용자입니다.");
            return;
        }
        switch (store.request(me, target)) {
            case ALREADY_FRIENDS -> fail(user, "이미 " + target + " 님과 친구입니다.");
            case ALREADY_SENT -> fail(user, "이미 " + target + " 님에게 친구 요청을 보냈습니다.");
            case SENT -> {
                Log.info("친구 요청: %s → %s", me, target);
                notice(user, target + " 님에게 친구 요청을 보냈습니다.");
                ClientHandler other = server.users().find(target);
                if (other != null) other.send(Message.of(MessageType.FRIEND_REQUEST).put("from", me));
                sendListTo(me, target);
            }
            case ACCEPTED_BOTH -> {
                // 상대가 먼저 나에게 요청을 보내 둔 상태였으므로 바로 친구가 됨
                Log.info("친구 맺음: %s - %s", me, target);
                notice(user, target + " 님과 친구가 되었습니다.");
                noticeTo(target, me + " 님과 친구가 되었습니다.");
                sendListTo(me, target);
            }
        }
    }

    private void handleReply(ClientHandler user, Message msg) {
        String me = user.nickname();
        String from = msg.get("from");
        boolean accept = msg.getBoolean("accept");
        if (!store.reply(me, from, accept)) {
            fail(user, "이미 처리되었거나 취소된 친구 요청입니다.");
            sendListTo(me);
            return;
        }
        if (accept) {
            Log.info("친구 맺음: %s - %s", from, me);
            notice(user, from + " 님과 친구가 되었습니다.");
            noticeTo(from, me + " 님이 친구 요청을 수락했습니다.");
        }
        // 거절은 보낸 사람에게 따로 알리지 않는다 (목록에서 조용히 사라짐)
        sendListTo(me, from);
    }

    private void handleRemove(ClientHandler user, Message msg) {
        String me = user.nickname();
        String target = msg.get("target");
        boolean wasFriend = store.areFriends(me, target);
        if (!store.remove(me, target)) {
            fail(user, "친구 목록에 없는 사용자입니다.");
            return;
        }
        notice(user, wasFriend ? target + " 님을 친구에서 삭제했습니다." : target + " 님에게 보낸 친구 요청을 취소했습니다.");
        sendListTo(me, target);
    }

    // ------------------------------------------------------------------ login / logout

    /** 로그인 직후: 내 친구 목록을 보내고, 접속 중인 친구들에게 내가 온라인이 되었음을 알린다. */
    public void onLogin(ClientHandler user) {
        String me = user.nickname();
        Set<String> targets = new LinkedHashSet<>(store.friendsOf(me));
        targets.add(me);
        sendListTo(targets.toArray(new String[0]));
        int pending = store.receivedBy(me).size();
        if (pending > 0) notice(user, "받은 친구 요청이 " + pending + "개 있습니다.");
    }

    /** 접속 종료 후: 접속 중인 친구들의 목록에서 나를 오프라인으로 바꾼다. */
    public void onLogout(String nickname) {
        sendListTo(store.friendsOf(nickname).toArray(new String[0]));
    }

    // ------------------------------------------------------------------ messages

    /** 접속 중인 사람에게만 최신 친구 목록을 보낸다 (오프라인이면 다음 로그인 때 받음). */
    private void sendListTo(String... nicknames) {
        for (String n : nicknames) {
            ClientHandler c = server.users().find(n);
            if (c != null) c.send(friendListMessage(n));
        }
    }

    public Message friendListMessage(String nickname) {
        List<String[]> rows = new ArrayList<>();
        for (String f : store.friendsOf(nickname)) {
            rows.add(new String[]{f, String.valueOf(server.users().find(f) != null)});
        }
        return Message.of(MessageType.FRIEND_LIST)
                .putTable("friends", rows)
                .putList("received", store.receivedBy(nickname))
                .putList("sent", store.sentBy(nickname));
    }

    private static void notice(ClientHandler user, String text) {
        user.send(Message.of(MessageType.FRIEND_NOTICE).put("text", text));
    }

    /** 요청 거부 안내. ERROR 대신 FRIEND_NOTICE(error=true)로 보내 친구 탭에만 표시되게 한다. */
    private static void fail(ClientHandler user, String text) {
        user.send(Message.of(MessageType.FRIEND_NOTICE).put("text", text).put("error", true));
    }

    private void noticeTo(String nickname, String text) {
        ClientHandler c = server.users().find(nickname);
        if (c != null) notice(c, text);
    }
}
