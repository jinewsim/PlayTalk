package playtalk.common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** 서버와 클라이언트가 함께 쓰는 상수. */
public final class Protocol {

    private Protocol() {
    }

    public static final int DEFAULT_PORT = 9090;
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    public static final int NICKNAME_MAX_LENGTH = 12;
    public static final int ROOM_TITLE_MAX_LENGTH = 30;
    public static final int ROOM_MIN_USERS = 2;
    public static final int ROOM_MAX_USERS = 5;
    public static final int CHAT_MAX_LENGTH = 500;

    /** 1:1 게임 초대 응답 제한 시간(초) */
    public static final int INVITE_TIMEOUT_SECONDS = 15;

    /** 사용자 상태 (ROOM_MEMBERS 의 status 값) */
    public static final String STATUS_IDLE = "IDLE";
    public static final String STATUS_IN_GAME = "IN_GAME";

    /** 게임 결과 (GAME_END 의 outcome 값) */
    public static final String OUTCOME_WIN = "WIN";
    public static final String OUTCOME_LOSS = "LOSS";
    public static final String OUTCOME_DRAW = "DRAW";

    /** 닉네임 형식 검사: 1~12자, 공백/제어문자/구분 기호 불가 */
    public static String validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) return "닉네임을 입력하세요.";
        if (nickname.length() > NICKNAME_MAX_LENGTH) return "닉네임은 " + NICKNAME_MAX_LENGTH + "자 이하로 입력하세요.";
        for (char ch : nickname.toCharArray()) {
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == ',' || ch == ':') {
                return "닉네임에 공백이나 특수 구분 기호(, :)는 쓸 수 없습니다.";
            }
        }
        return null;
    }
}
