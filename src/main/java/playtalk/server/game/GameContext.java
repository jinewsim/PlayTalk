package playtalk.server.game;

/** 게임 세션이 바깥(GameManager)에 요청할 수 있는 기능. */
public interface GameContext {

    /** 게임 종료: 결과 전송, 전적 기록, 세션 정리 */
    void finish(GameSession session, GameResult result);

    /** delayMs 뒤에 task 실행. 게임이 이미 끝났으면 실행되지 않는다. 반환값으로 취소 가능. */
    Cancellable schedule(GameSession session, Runnable task, long delayMs);

    interface Cancellable {
        void cancel();
    }
}
