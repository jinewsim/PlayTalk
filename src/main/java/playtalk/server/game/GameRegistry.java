package playtalk.server.game;

import playtalk.common.GameType;
import playtalk.server.Participant;
import playtalk.server.game.games.RockPaperScissorsGame;
import playtalk.server.game.games.TicTacToeGame;
import playtalk.server.game.games.TimingGame;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * GameType → 게임 세션 생성기.
 * 새 게임을 만들면 {@link #GameRegistry()} 안에 한 줄만 추가하면 된다.
 */
public class GameRegistry {

    @FunctionalInterface
    public interface Factory {
        GameSession create(String sessionId, List<Participant> players, GameContext context);
    }

    private final Map<GameType, Factory> factories = new EnumMap<>(GameType.class);

    public GameRegistry() {
        register(GameType.TIC_TAC_TOE, TicTacToeGame::new);
        register(GameType.ROCK_PAPER_SCISSORS, RockPaperScissorsGame::new);
        register(GameType.TIMING, TimingGame::new);
        // 예) register(GameType.BOMB, BombGame::new);
    }

    public void register(GameType type, Factory factory) {
        factories.put(type, factory);
    }

    public boolean supports(GameType type) {
        return type != null && factories.containsKey(type);
    }

    public GameSession create(GameType type, String sessionId, List<Participant> players, GameContext context) {
        Factory f = factories.get(type);
        if (f == null) throw new IllegalArgumentException("등록되지 않은 게임: " + type);
        return f.create(sessionId, players, context);
    }
}
