package playtalk.common;

/**
 * 프로토콜 메시지 종류.
 *
 * <p>C→S: 클라이언트가 서버로 보냄, S→C: 서버가 클라이언트로 보냄.
 * 각 메시지가 담는 key는 docs/PROTOCOL.md 에 정리되어 있다.
 * 새 기능을 추가할 때는 여기에 타입을 추가하고 문서도 함께 갱신한다.
 */
public enum MessageType {

    // ---------------------------------------------------------- 접속
    /** C→S nickname */
    LOGIN,
    /** S→C nickname */
    LOGIN_OK,
    /** S→C reason */
    LOGIN_FAIL,
    /** C→S 정상 종료 알림 */
    LOGOUT,
    /** S→C users(목록): 전체 접속자 */
    USER_LIST,

    // ---------------------------------------------------------- 채팅방
    /** S→C rooms(표: id, title, current, max, owner) */
    ROOM_LIST,
    /** C→S title, max */
    ROOM_CREATE,
    /** C→S roomId */
    ROOM_JOIN,
    /** C→S (내용 없음) */
    ROOM_LEAVE,
    /** S→C roomId, title, max, owner: 내가 방에 들어감 */
    ROOM_JOINED,
    /** S→C roomId: 내가 방에서 나감 */
    ROOM_LEFT,
    /** S→C roomId, owner, members(표: nickname, status) */
    ROOM_MEMBERS,
    /** S→C reason: 방 생성/입장 실패 */
    ROOM_FAIL,

    // ---------------------------------------------------------- 채팅
    /** C→S text / S→C roomId, from, text, time */
    CHAT,
    /** S→C text: 입장/퇴장 등 시스템 안내 */
    SYSTEM,

    // ---------------------------------------------------------- 1:1 게임 초대
    /** C→S target, game / S→C inviteId, from, game, timeout */
    GAME_INVITE,
    /** C→S inviteId, accept(true/false) */
    GAME_INVITE_REPLY,
    /** S→C inviteId, target, game, result(ACCEPTED/DECLINED/TIMEOUT/CANCELLED) */
    GAME_INVITE_RESULT,

    // ---------------------------------------------------------- 다인용 게임 대기실
    /** C→S game: 현재 방에 대기실 열기 */
    LOBBY_OPEN,
    /** C→S lobbyId */
    LOBBY_JOIN,
    /** C→S lobbyId */
    LOBBY_LEAVE,
    /** C→S lobbyId: 호스트만 가능 */
    LOBBY_START,
    /** S→C lobbyId, game, host, min, max, players(목록) */
    LOBBY_STATE,
    /** S→C lobbyId, reason */
    LOBBY_CLOSED,

    // ---------------------------------------------------------- 게임 진행 (모든 게임 공통)
    /** S→C sessionId, game, players(목록) + 게임별 값 */
    GAME_START,
    /** C→S sessionId + 게임별 값 */
    GAME_ACTION,
    /** S→C sessionId + 게임별 값 */
    GAME_STATE,
    /** S→C sessionId, game, results(표: nickname, outcome), summary */
    GAME_END,
    /** C→S sessionId: 기권/창 닫기 */
    GAME_LEAVE,

    // ---------------------------------------------------------- 전적
    /** C→S nickname(생략 시 본인) */
    STATS_REQUEST,
    /** S→C nickname, win, loss, draw, games(표: game, win, loss, draw) */
    STATS,

    // ---------------------------------------------------------- 기타
    /** S→C text: 요청 처리 실패 안내 */
    ERROR
}
