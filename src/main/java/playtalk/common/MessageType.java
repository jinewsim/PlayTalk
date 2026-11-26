package playtalk.common;

/**
 * 프로토콜 메시지 종류.
 *
 * <p>C→S: 클라이언트가 서버로 보냄, S→C: 서버가 클라이언트로 보냄.
 * 각 메시지가 담는 key는 docs/PROTOCOL.md 에 정리되어 있다.
 * 새 기능을 추가할 때는 여기에 타입을 추가하고 문서도 함께 갱신한다.
 */
public enum MessageType {

    // ---------------------------------------------------------- 회원가입 / 접속
    /** C→S nickname, password: 계정 만들기 (로그인 전에 보낼 수 있음) */
    REGISTER,
    /** S→C nickname: 가입 성공 (클라이언트는 이어서 LOGIN을 보낸다) */
    REGISTER_OK,
    /** S→C reason */
    REGISTER_FAIL,
    /** C→S nickname, password */
    LOGIN,
    /** S→C nickname */
    LOGIN_OK,
    /** S→C reason */
    LOGIN_FAIL,
    /** C→S 정상 종료 알림 */
    LOGOUT,
    /** C→S password: 회원 탈퇴 (계정, 친구 관계, 전적 삭제) */
    ACCOUNT_DELETE,
    /** S→C: 탈퇴 완료. 서버는 이어서 연결을 끊는다 */
    ACCOUNT_DELETED,
    /** S→C reason: 탈퇴 실패 (비밀번호 틀림 등) */
    ACCOUNT_DELETE_FAIL,
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
    /** S→C roomId, title, max, owner, history(불러올 수 있는 이전 대화 수), historyUpTo: 내가 방에 들어감 */
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
    /** C→S roomId, upTo (ROOM_JOINED의 historyUpTo): 이전 대화 불러오기 요청 */
    CHAT_HISTORY_REQUEST,
    /** S→C roomId, messages(표: from, text, time): 오래된 것부터 */
    CHAT_HISTORY,
    /** S→C text: 입장/퇴장 등 시스템 안내 */
    SYSTEM,

    // ---------------------------------------------------------- 1:1 게임 초대
    /** C→S target, game / S→C inviteId, from, game, timeout */
    GAME_INVITE,
    /** C→S inviteId, accept(true/false) */
    GAME_INVITE_REPLY,
    /** S→C inviteId, target, game, result(ACCEPTED/DECLINED/TIMEOUT/CANCELLED) */
    GAME_INVITE_RESULT,
    /** C→S inviteId: 내가 보낸 초대를 취소 (S→C GAME_INVITE_RESULT, result=CANCELLED) */
    GAME_INVITE_CANCEL,

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

    /** C→S (key 없음) */
    RANKING_REQUEST,
    /** S→C users(표: nickname, win, loss, draw) — 전적이 있는 모든 사용자, 정렬은 클라이언트가 한다 */
    RANKING,

    /** C→S roomId */
    ROOM_STATS_REQUEST,
    /** S→C roomId, users(표: nickname, win, loss, draw) — 그 채팅방에서 한 게임의 전적만 (서버를 끄면 초기화) */
    ROOM_STATS,

    /** C→S opponent */
    VERSUS_STATS_REQUEST,
    /** S→C opponent, users(표: nickname, win, loss, draw) — 나와 그 사람이 서로 한 1:1 게임만 (서버를 끄면 초기화) */
    VERSUS_STATS,

    // ---------------------------------------------------------- 친구
    /** C→S target: 친구 요청 보내기 / S→C from: 친구 요청을 받음 (실시간 알림) */
    FRIEND_REQUEST,
    /** C→S from, accept(true/false): 받은 친구 요청에 답하기 */
    FRIEND_REPLY,
    /** C→S target: 친구 삭제, 또는 내가 보낸 요청 취소 */
    FRIEND_REMOVE,
    /** S→C friends(표: nickname, online), received(목록), sent(목록): 로그인 시, 친구 관계·접속 상태가 바뀔 때마다 */
    FRIEND_LIST,
    /** S→C text, error(true면 요청 거부 사유): 친구 관련 안내 (요청 보냄, 수락됨, 없는 사용자 등) */
    FRIEND_NOTICE,

    // ---------------------------------------------------------- 기타
    /** S→C text: 요청 처리 실패 안내 */
    ERROR
}
