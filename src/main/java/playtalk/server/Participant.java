package playtalk.server;

import playtalk.common.Message;

/**
 * 메시지를 받을 수 있는 접속 사용자.
 * 게임 로직은 소켓을 직접 다루지 않고 이 인터페이스만 사용하므로 테스트용 가짜 사용자로 바꿔 끼우기 쉽다.
 */
public interface Participant {

    String nickname();

    void send(Message message);
}
