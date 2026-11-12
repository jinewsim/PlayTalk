package playtalk.common;

/**
 * 지원하는 미니게임 목록.
 *
 * <p>새 게임 추가 방법 (자세한 내용은 README):
 * <ol>
 *   <li>여기에 상수 추가 (표시 이름, 최소/최대 인원)</li>
 *   <li>서버: {@code playtalk.server.game.GameSession}을 상속한 클래스 작성 후 {@code GameRegistry}에 등록</li>
 *   <li>클라이언트: {@code playtalk.client.game.GameWindow}를 상속한 창 작성 후 {@code GameWindowRegistry}에 등록</li>
 * </ol>
 */
public enum GameType {

    TIC_TAC_TOE("틱택토", 2, 2),
    ROCK_PAPER_SCISSORS("가위바위보", 2, 2),
    TIMING("시간 맞추기", 2, 5);

    private final String displayName;
    private final int minPlayers;
    private final int maxPlayers;

    GameType(String displayName, int minPlayers, int maxPlayers) {
        this.displayName = displayName;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    public String displayName() {
        return displayName;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    /** 1:1 초대로 시작하는 게임인지 (아니면 대기실로 시작하는 다인용 게임) */
    public boolean isOneOnOne() {
        return maxPlayers == 2;
    }

    public static GameType parse(String name) {
        try {
            return GameType.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
