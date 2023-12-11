# Milestone 3 Report

## (1) Test Output
Running your test should generate a similar output after running [PairingTest.java](src/test/PairingTest.java)

## (2) `COMPLETE_GAME` vs `ABORT_GAME` requests
| `COMPLETE_GAME`                                                                    | `ABORT_GAME`                                                                 |
|------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| Sets `EventStatus` to `COMPLETED`                                                  | Sets `EventStatus` to `ABORTED`                                              |
| Sent when either player refused to play again at the end of a game                 | Sent when wither player quits the game while the game is in play             |
| `GamingResponse` will have `active` attribute as `false`                           | `GamingResponse` will have `active` attribute as `false`                     |
| `GamingResponse` will have `message` attribute as `Opponent refused to play again` | `GamingResponse` will have `message` attribute as `Opponent Aborts the game` |

## (3) Can two users log in with the same credentials at the same time?
`YES`, with the current implementation, two users can log in with same credentials at the same time.
This might cause issues when a user sends a game invitation to himself and when exchanging game moves.

### Why?
This is because, we did not implement any checks during user login to see if the user is already online.
To fix the issue, we can check if `user.isOnline()` before we authorize a successful login.

## (4) Stages of `EventStatus`
1. When a `SEND_INVITATION` request is sent, an `Event` will be created with `PENDING` stage
2. When the opponent sends a `ACCEPT_INVITATION` requests, it is then changed to from `PENDING` to `ACCEPTED` stage
3. When the opponent sends a `DECLINE_INVITATION` requests, it is then changed to from `PENDING` to `DECLINED` stage
4. When the sender sends a `ACKNOWLEDGE_RESPONSE` requests, it is then changed to from either `ACCEPTED` to `PLAYING` or from `DECLINED` to `ABORTED` stage
5. When either player sends a `ABORT_GAME` requests, it is then changed to from `PLAYING` to `ABORTED` stage
6. When either player sends a `COMPLETE_GAME` requests, it is then changed to from `PLAYING` to `COMPLETED` stage
7. If any client's socket disconnects, it is changed to from either `PENDING`, `ACCEPTED`, `DECLINED`, or `PLAYING` to `ABORTED` stage.
8. `COMPLETED` and `ABORTED` are the terminal stages.

## (5) What happens when `DatabaseHelper.getInstance().truncateTables();` is deleted
**`Yes`, it is necessary**.
- It is necessary because we want to start testing with an empty database always
- When we delete the above line, our test will always fail.
- All user registration tests will fail because the users already exists in the database. 
