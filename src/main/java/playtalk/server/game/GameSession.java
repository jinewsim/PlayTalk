package playtalk.server.game;

import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.server.Participant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 모든 미니게임의 공통 틀. 인원 수에 상관없이(2~N명) 같은 구조로 동작한다.
 *
 * <p>새 게임은 이 클래스를 상속해 세 가지만 구현하면 된다.
 * <ul>
 *   <li>{@link #start()}: 게임 시작 시 초기 상태 전송</li>
 *   <li>{@link #onAction}: 플레이어 입력(GAME_ACTION) 처리. 유효성 검사와 승패 판정은 반드시 서버에서 한다.</li>
 *   <li>{@link #onPlayerDropped}: (선택) 3명 이상 게임에서 한 명이 나갔을 때 처리</li>
 * </ul>
 *
 * <p>동시성: GameManager가 하나의 잠금 안에서 모든 메서드를 호출하므로 세션 안에서는 동기화를 신경 쓰지 않아도 된다.
 */
public abstract class GameSession {

    protected final String id;
    protected final GameType type;
    /** 처음 참가한 플레이어 (순서 유지) */
    protected final List<Participant> players;
    /** 아직 게임에 남아 있는 플레이어 */
    protected final List<Participant> active;
    private final GameContext context;
    private boolean finished;

    protected GameSession(String id, GameType type, List<Participant> players, GameContext context) {
        this.id = id;
        this.type = type;
        this.players = Collections.unmodifiableList(new ArrayList<>(players));
        this.active = new ArrayList<>(players);
        this.context = context;
    }

    /** GAME_START 전송 직후 호출된다. */
    public abstract void start();

    /** 플레이어가 GAME_ACTION을 보냈을 때. */
    public abstract void onAction(Participant from, Message action);

    /**
     * 플레이어가 기권하거나 연결이 끊겼을 때.
     * 기본 동작: 나간 사람은 패배, 한 명만 남으면 그 사람이 승리. 여러 명 남으면 {@link #onPlayerDropped} 호출.
     */
    public void onPlayerLeft(Participant leaver) {
        if (finished || !active.remove(leaver)) return;
        broadcast(state().put("event", "LEFT").put("nickname", leaver.nickname()));
        if (active.size() <= 1) {
            GameResult result = new GameResult().loss(leaver.nickname());
            for (Participant p : players) {
                if (p != leaver) {
                    if (active.contains(p)) result.win(p.nickname());
                    else result.loss(p.nickname());
                }
            }
            String winner = active.isEmpty() ? "" : active.get(0).nickname() + " 님의 기권승!";
            finish(result.summary(leaver.nickname() + " 님이 게임을 나갔습니다. " + winner));
        } else {
            onPlayerDropped(leaver);
        }
    }

    /** 3명 이상 게임에서 한 명이 나가고 2명 이상 남았을 때. 기본: 남은 사람끼리 계속 진행. */
    protected void onPlayerDropped(Participant leaver) {
    }

    /** 게임 시작 메시지에 게임별 값을 추가하고 싶을 때 재정의. */
    public void fillStartMessage(Message start) {
    }

    // ------------------------------------------------------------------ helpers

    protected Message state() {
        return Message.of(MessageType.GAME_STATE).put("sessionId", id).put("game", type.name());
    }

    protected void broadcast(Message message) {
        for (Participant p : players) {
            p.send(message);
        }
    }

    protected void sendTo(Participant p, Message message) {
        p.send(message);
    }

    protected void reject(Participant p, String reason) {
        p.send(Message.of(MessageType.ERROR).put("text", reason));
    }

    protected void finish(GameResult result) {
        if (finished) return;
        finished = true;
        context.finish(this, result);
    }

    protected GameContext.Cancellable schedule(Runnable task, long delayMs) {
        return context.schedule(this, task, delayMs);
    }

    public boolean isFinished() {
        return finished;
    }

    public String getId() {
        return id;
    }

    public GameType getType() {
        return type;
    }

    public List<Participant> getPlayers() {
        return players;
    }
}
