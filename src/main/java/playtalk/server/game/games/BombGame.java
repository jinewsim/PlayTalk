package playtalk.server.game.games;

import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.server.Participant;
import playtalk.server.game.GameContext;
import playtalk.server.game.GameResult;
import playtalk.server.game.GameSession;

import java.util.List;
import java.util.Random;

/**
 * 폭탄 돌리기 (다인용 2~6명).
 * 폭탄을 가진 사람이 다른 사람을 골라 넘긴다. 폭탄은 서버가 몰래 정한 시간(10~30초)에 터지고,
 * 그때 폭탄을 가진 사람이 패배, 나머지는 승리한다. 터지는 시각은 클라이언트에 알려주지 않는다.
 *
 * <pre>
 * GAME_START : (게임별 값 없음)
 * GAME_STATE : event=HOLDER, nickname, from(넘긴 사람, 처음엔 빈 값), passes(지금까지 넘긴 횟수)
 *              event=EXPLODE, nickname
 * GAME_ACTION: action=PASS, target
 * </pre>
 */
public class BombGame extends GameSession {

    public static final long MIN_FUSE_MS = 5_000;
    public static final long MAX_FUSE_MS = 15_000;

    /** 터지기 이만큼 전에 DANGER 신호를 보낸다 (정확한 폭발 시각은 알려주지 않는다) */
    public static final long WARNING_MS = 3_000;

    private final Random random = new Random();
    private Participant holder;
    private int passes;

    public BombGame(String id, List<Participant> players, GameContext context) {
        super(id, GameType.BOMB, players, context);
    }

    @Override
    public void start() {
        holder = active.get(random.nextInt(active.size()));
        broadcast(holderState(""));
        long fuse = MIN_FUSE_MS + (long) (random.nextDouble() * (MAX_FUSE_MS - MIN_FUSE_MS));
        schedule(this::explode, fuse);
        schedule(() -> broadcast(state().put("event", "DANGER")), Math.max(0, fuse - WARNING_MS));
    }

    @Override
    public void onAction(Participant from, Message action) {
        if (!"PASS".equals(action.get("action"))) return;
        if (from != holder) {
            reject(from, "지금 폭탄을 가진 사람만 넘길 수 있습니다.");
            return;
        }
        Participant target = find(action.get("target", ""));
        if (target == null || target == from) {
            reject(from, "넘길 수 있는 상대를 골라주세요.");
            return;
        }
        passes++;
        holder = target;
        broadcast(holderState(from.nickname()));
    }

    @Override
    protected void onPlayerDropped(Participant leaver) {
        if (leaver == holder) {
            holder = active.get(random.nextInt(active.size()));
            broadcast(holderState(leaver.nickname()));
        }
    }

    private void explode() {
        if (isFinished()) return;
        broadcast(state().put("event", "EXPLODE").put("nickname", holder.nickname()));
        GameResult result = new GameResult();
        for (Participant p : players) {
            if (p == holder) result.loss(p.nickname());
            else result.win(p.nickname());
        }
        finish(result.summary("펑! " + holder.nickname() + " 님이 폭탄을 들고 있었습니다. (패스 " + passes + "번)"));
    }

    private Message holderState(String from) {
        return state().put("event", "HOLDER").put("nickname", holder.nickname())
                .put("from", from).put("passes", passes);
    }

    private Participant find(String nickname) {
        for (Participant p : active) {
            if (p.nickname().equals(nickname)) return p;
        }
        return null;
    }
}