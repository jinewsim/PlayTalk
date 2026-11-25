# PlayTalk 통신 프로토콜

## 형식

- TCP, UTF-8, **메시지 한 건 = 한 줄** (`\n`으로 끝남)
- `TYPE<TAB>key=value<TAB>key=value ...`
- 값 안의 `\`, 탭, 줄바꿈은 `\\`, `\t`, `\n`으로 이스케이프 (`Message.encode/decode`가 처리)
- 목록/표 값: 행은 `U+001E`, 열은 `U+001F`로 구분 (`Message.putList/putTable/getList/getTable`)

```
CHAT	text=안녕하세요 :smile:
CHAT	roomId=1	from=민수	text=안녕하세요 :smile:	time=오후 2:14
```

C→S: 클라이언트 → 서버, S→C: 서버 → 클라이언트

## 회원가입 / 접속

| 타입 | 방향 | key | 설명 |
|---|---|---|---|
| `REGISTER` | C→S | nickname, password | 계정 만들기. 로그인 전에 보낼 수 있는 메시지는 `REGISTER`, `LOGIN`뿐 |
| `REGISTER_OK` | S→C | nickname | 가입 성공. 클라이언트는 같은 연결로 바로 `LOGIN`을 보냄 |
| `REGISTER_FAIL` | S→C | reason | 형식 오류(닉네임 1~12자, 비밀번호 4~20자), 이미 가입된 닉네임 |
| `LOGIN` | C→S | nickname, password | |
| `LOGIN_OK` | S→C | nickname | 성공. 바로 이어서 `ROOM_LIST`, `USER_LIST`, `FRIEND_LIST` 전송 |
| `LOGIN_FAIL` | S→C | reason | 가입되지 않은 닉네임, 비밀번호 틀림, 이미 접속 중인 계정 |
| `ACCOUNT_DELETE` | C→S | password | 회원 탈퇴. 비밀번호를 다시 확인하고 계정·친구 관계·받은/보낸 요청·전적을 삭제. 게임 중에는 불가 |
| `ACCOUNT_DELETED` | S→C | (없음) | 탈퇴 완료. 서버가 이어서 연결을 끊음 (방 퇴장 등은 평소 접속 종료와 같이 처리) |
| `ACCOUNT_DELETE_FAIL` | S→C | reason | 비밀번호 틀림, 게임 중 |
| `LOGOUT` | C→S | | 정상 종료 |
| `USER_LIST` | S→C | users(목록) | 접속자 변동 시 전체에게 |

## 채팅방

| 타입 | 방향 | key | 설명 |
|---|---|---|---|
| `ROOM_LIST` | S→C | rooms(표: id, title, current, max, owner) | 방 변동 시 전체에게 |
| `ROOM_CREATE` | C→S | title, max(2~5) | 만든 사람은 자동 입장 + 방장 |
| `ROOM_JOIN` | C→S | roomId | 다른 방에 있으면 자동으로 나간 뒤 입장 |
| `ROOM_LEAVE` | C→S | | |
| `ROOM_JOINED` | S→C | roomId, title, max, owner, history, historyUpTo | |
| `ROOM_LEFT` | S→C | roomId | |
| `ROOM_MEMBERS` | S→C | roomId, owner, members(표: nickname, status `IDLE`/`IN_GAME`) | 방 인원/상태 변동 시 방 전체에게 |
| `ROOM_FAIL` | S→C | reason | 인원 초과 등 |

## 채팅

| 타입 | 방향 | key | 설명 |
|---|---|---|---|
| `CHAT` | C→S | text | 이모티콘은 `:이름:` 코드로 본문에 포함 |
| `CHAT` | S→C | roomId, from, text, time | 같은 방 사람에게만 |
| `CHAT_HISTORY_REQUEST` | C→S | roomId, upTo | |
| `CHAT_HISTORY` | S→C | roomId, messages(표: from, text, time) | |
| `SYSTEM` | S→C | text | 입장/퇴장, 게임 시작/결과 안내 |
| `ERROR` | S→C | text | 요청 거부 사유 |

이모티콘 이름: `smile laugh wink sad angry surprised heart gg` (`common/Emoticons.NAMES`)

## 1:1 게임 초대

| 타입 | 방향 | key | 설명 |
|---|---|---|---|
| `GAME_INVITE` | C→S | target, game | 같은 방에 있거나 **친구**여야 함 (친구는 채팅방 밖에서도 가능). 둘 다 게임/초대/대기실 중이 아니어야 함 |
| `GAME_INVITE` | S→C | inviteId, from, game, timeout | 받는 사람에게 |
| `GAME_INVITE_REPLY` | C→S | inviteId, accept(true/false) | |
| `GAME_INVITE_RESULT` | S→C | inviteId, from, target, game, result | result: `PENDING`(보낸 사람에게) / `ACCEPTED` / `DECLINED` / `TIMEOUT`(15초) / `CANCELLED` |
| `GAME_INVITE_CANCEL` | C→S | inviteId | 초대를 보낸 사람만 취소 가능. 결과는 `GAME_INVITE_RESULT`의 `CANCELLED`로 양쪽에 전달 |

## 다인용 게임 대기실

| 타입 | 방향 | key | 설명 |
|---|---|---|---|
| `LOBBY_OPEN` | C→S | game | 내 채팅방에 대기실 열기 (방당 1개), 연 사람이 호스트 |
| `LOBBY_JOIN` / `LOBBY_LEAVE` | C→S | lobbyId | 호스트가 나가면 대기실이 닫힘 |
| `LOBBY_START` | C→S | lobbyId | 호스트만, 최소 인원 이상일 때 |
| `LOBBY_STATE` | S→C | lobbyId, roomId, game, host, min, max, players(목록) | 방 전체에게 |
| `LOBBY_CLOSED` | S→C | lobbyId, reason | `STARTED` 또는 닫힌 이유 |

## 게임 진행 (모든 게임 공통)

| 타입 | 방향 | key | 설명 |
|---|---|---|---|
| `GAME_START` | S→C | sessionId, game, players(목록), roomId + 게임별 값 | 친구끼리 방 밖에서 하는 게임은 roomId=-1 |
| `GAME_ACTION` | C→S | sessionId + 게임별 값 | |
| `GAME_STATE` | S→C | sessionId, game + 게임별 값 | |
| `GAME_END` | S→C | sessionId, game, results(표: nickname, `WIN`/`LOSS`/`DRAW`), summary | |
| `GAME_LEAVE` | C→S | sessionId | 기권. 연결이 끊긴 경우도 같은 처리 |

플레이어가 나가면 `GAME_STATE event=LEFT nickname=...`이 전송되고, 1명만 남으면 그 사람이 승리합니다.

### 틱택토 `TIC_TAC_TOE`
- START: `x`, `o`, `board`(9자, `.`=빈칸), `turn` (초대한 사람이 X 선공)
- ACTION: `cell` (0~8, 왼쪽 위부터)
- STATE: `board`, `turn`, `last`, `lastBy`

### 가위바위보 `ROCK_PAPER_SCISSORS`
- START: `limit`(초)
- ACTION: `choice` = `ROCK` / `PAPER` / `SCISSORS`
- STATE: `event=CHOSEN nickname` (누가 골랐는지만), `event=REVEAL choices(표: nickname, choice)` (`NONE`=시간 초과)

### 시간 맞추기 `TIMING` (2~5명)
- START: `target`(ms, 10000), `visibleMs`(타이머가 보이는 시간), `countdown`(초)
- ACTION: `action=STOP`
- STATE: `event=COUNTDOWN count` → `event=RUNNING` → `event=STOPPED nickname` → `event=REVEAL records(표: nickname, elapsedMs, diffMs)` (-1 = 기록 없음)
- 시간은 서버가 `RUNNING`을 보낸 순간부터 `STOP`을 받은 순간까지 잽니다.

### 폭탄 돌리기 `BOMB` (2~5명)
- ACTION: `action=PASS`, `target`
- STATE: `event=HOLDER nickname from passes` → `event=DANGER` → `event=EXPLODE nickname`

## 친구

| 타입 | 방향 | key | 설명 |
|---|---|---|---|
| `FRIEND_REQUEST` | C→S | target | 가입된 사용자에게 요청 (오프라인이어도 가능, 다음 로그인 때 확인). 상대가 이미 나에게 요청했다면 바로 친구가 됨 |
| `FRIEND_REQUEST` | S→C | from | 받는 사람이 접속 중이면 실시간 알림 |
| `FRIEND_REPLY` | C→S | from, accept(true/false) | 받은 요청에 수락/거절. 거절은 보낸 사람에게 따로 알리지 않음 |
| `FRIEND_REMOVE` | C→S | target | 친구 삭제 (양쪽 모두에서 삭제), 또는 내가 보낸 요청 취소 |
| `FRIEND_LIST` | S→C | friends(표: nickname, online), received(목록), sent(목록) | 로그인 시, 친구 관계가 바뀔 때, 친구가 접속/종료할 때 관련된 사람에게 전체 목록을 다시 보냄 |
| `FRIEND_NOTICE` | S→C | text, error | 친구 관련 안내. `error=true`면 요청 거부 사유 (없는 사용자, 이미 친구, 자기 자신 등) |

## 전적

| 타입 | 방향 | key |
|---|---|---|
| `STATS_REQUEST` | C→S | nickname(생략 시 본인) |
| `STATS` | S→C | nickname, win, loss, draw, games(표: game, win, loss, draw) |
| `ROOM_STATS_REQUEST` | C→S | roomId |
| `ROOM_STATS` | S→C | roomId, users(표: nickname, win, loss, draw) |
| `RANKING_REQUEST` | C→S | (없음) |
| `RANKING` | S→C | users(표: nickname, win, loss, draw) |