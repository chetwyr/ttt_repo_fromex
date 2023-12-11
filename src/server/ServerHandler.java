package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import model.Event;
import model.User;
import socket.GamingResponse;
import socket.PairingResponse;
import socket.Request;
import socket.Response;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  A class that helps SocketServer Handle individual user communication. This class extends {@link Thread}
 *
 * @author Ahmad Suleiman
 */
public class ServerHandler extends Thread {

	/**
	 * Used for printing logs of different levels
	 */
	private final Logger LOGGER;

	/**
	 * Socket connection with a client
	 */
	private final Socket socket;

	/**
	 * Input stream to get clients {@link socket.Request}
	 */
	private final DataInputStream inputStream;

	/**
	 * Output Stream to send client a {@link socket.Response}
	 */
	private final DataOutputStream outputStream;

	/**
	 * Used to serialize/deserialize objects
	 */
	private final Gson gson;

	/**
	 * Will be used to store game move
	 * A RDBMS will be used to store game move in later milestones
	 */
	public int currentEventId;

	/**
	 * Username of the current client of this socket connection
	 */
	public String currentUsername;

	/**
	 * Default constructor
	 * Initializes all attributes
	 * @param socket Client's socket connection after server accepts
	 * @throws IOException When no valid input or output stream from socket
	 */
	public ServerHandler(Socket socket) throws IOException {
		LOGGER = Logger.getLogger(ServerHandler.class.getName());

		this.socket = socket;
		this.gson = new GsonBuilder().serializeNulls().create();
		this.inputStream = new DataInputStream(socket.getInputStream());
		this.outputStream = new DataOutputStream(socket.getOutputStream());
	}

	/**
	 * Runs immediately after the thread is started
	 * The function continuously waits for a client request and sends a response
	 * Until a client disconnects
	 */
	@Override
	public void run() {
		// Keep accepting request until client disconnects are send invalid request
		while (true) {
			try {
				String serializedRequest = inputStream.readUTF(); // read/receive clients request (blocking operation)
				Request request = gson.fromJson(serializedRequest, Request.class); // deserialized the request
				LOGGER.log(Level.INFO,"Client Request: " + currentUsername + " - " + request.getType());

				Response response = handleRequest(request); // get response to client's request
				String serializedResponse = gson.toJson(response); // serialize the response
				outputStream.writeUTF(serializedResponse); // write/send the response
				outputStream.flush(); // Flush the stream, force response to go
			} catch (EOFException e) {
				LOGGER.log(Level.INFO,"Server Info: Client Disconnected: " + currentUsername + " - " + socket.getRemoteSocketAddress());
				closeSocket();
				break;
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE,"Server Info: Client Connection Failed", e);
			}  catch (JsonSyntaxException e) {
				LOGGER.log(Level.SEVERE,"Server Info: Serialization Error", e);
			} catch (SQLException e) {
				LOGGER.log(Level.SEVERE,"Server Info: Database Error", e);
			}
		}
	}

	/**
	 * Closes clients connection
	 */
	private void closeSocket() {
		// Close socket connection and all IO streams
		try {
			socket.close();
			inputStream.close();
			outputStream.close();

			if(currentUsername != null) {
				//Set Offline
				User user = DatabaseHelper.getInstance().getUser(currentUsername);
				user.setOnline(false);
				DatabaseHelper.getInstance().updateUser(user);

				//Change the status of all playing and pending gaming
				DatabaseHelper.getInstance().abortAllUserEvents(currentUsername);
			}
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE,"Server Info: Unable to close socket", e);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE,"Server Info: Database Exception Occurred", e);
		}
	}

	/**
	 * Handles all clients {@link Request.RequestType}
	 * @param request The request to handle
	 * @return Response to client's request
	 */
	private Response handleRequest(Request request) throws SQLException {
		// Decide which function to call for different types of request
		switch (request.getType()) {
			case REQUEST_MOVE:
				return handleRequestMove();
			case SEND_MOVE:
				int move = gson.fromJson(request.getData(), Integer.class);
				return handleSendMove(move);
			case REGISTER:
				User registerUser = gson.fromJson(request.getData(), User.class);
				return handleRegister(registerUser);
			case LOGIN:
				User loginUser = gson.fromJson(request.getData(), User.class);
				return handleLogin(loginUser);
			case UPDATE_PAIRING:
				return handleUpdatePairing();
			case SEND_INVITATION:
				String opponent = gson.fromJson(request.getData(), String.class);
				return handleSendInvitation(opponent);
			case ACCEPT_INVITATION:
				int eventIdToAccept = gson.fromJson(request.getData(), Integer.class);
				return handleAcceptInvitation(eventIdToAccept);
			case DECLINE_INVITATION:
				int eventIdToDecline = gson.fromJson(request.getData(), Integer.class);
				return handleDeclineInvitation(eventIdToDecline);
			case ACKNOWLEDGE_RESPONSE:
				int eventIdToAcknowledge = gson.fromJson(request.getData(), Integer.class);
				return handleAcknowledgeResponse(eventIdToAcknowledge);
			case COMPLETE_GAME:
				return handleCompleteGame();
			case ABORT_GAME:
				return handleAbortGame();
			default: // Invalid request type
				return new Response(Response.ResponseStatus.FAILURE, "Invalid Request");
		}
	}


	/**
	 * Handle request of type {@link Request.RequestType#REQUEST_MOVE}
	 * @return a game response with move information
	 * @throws SQLException When database error occurs
	 */
	private GamingResponse handleRequestMove() throws SQLException {
		Event event = DatabaseHelper.getInstance().getEvent(currentEventId);
		if(event != null) {
			GamingResponse response = new GamingResponse();
			response.setStatus(Response.ResponseStatus.SUCCESS);

			if (event.getStatus() == Event.EventStatus.ABORTED) {
				response.setActive(false);
				response.setMessage("Opponent Abort");
				return response;
			} else if (event.getStatus() == Event.EventStatus.COMPLETED) {
				response.setActive(false);
				response.setMessage("Opponent Deny Play Again");
				return response;
			} else {
				response.setActive(true);
				response.setMessage("Move Update");
			}
			// check if there is a valid move made by my opponent
			if (event.getMove() != -1 && !event.getTurn().equals(currentUsername)) {
				response.setMove(event.getMove());
				// Delete the move
				event.setMove(-1);
				event.setTurn(null);
				DatabaseHelper.getInstance().updateEvent(event);
			} else {
				response.setMove(-1);
			}
			return response;
		} else {
			return new GamingResponse(Response.ResponseStatus.FAILURE, "No active Game", -1, false);
		}
	}

	/**
	 * Handle request of type {@link Request.RequestType#SEND_MOVE}
	 * @param move the move to be added to the game
	 * @return a standard response
	 * @throws SQLException When database error occurs
	 */
	private Response handleSendMove(int move) throws SQLException {
		Event event = DatabaseHelper.getInstance().getEvent(currentEventId);
		if(event != null) {
			if (move < 0 || move > 8) { // Check for valid move
				return new Response(Response.ResponseStatus.FAILURE, "Invalid Move");
			}
			if (event.getTurn() == null || !event.getTurn().equals(currentUsername)) {
				// Save the move in the server and return a standard Response
				event.setMove(move);
				event.setTurn(currentUsername);
				DatabaseHelper.getInstance().updateEvent(event);
				return new Response(Response.ResponseStatus.SUCCESS, "New mode added to Game");
			} else {
				return new Response(Response.ResponseStatus.FAILURE, "Not your turn to move");
			}
		} else {
			return new Response(Response.ResponseStatus.FAILURE, "No active Game");
		}
	}

	/**
	 * Handle request of type {@link Request.RequestType#REGISTER}
	 * @param user The user object to register in the database
	 * @return a standard response
	 * @throws SQLException When database error occurs
	 */
	private Response handleRegister(User user) throws SQLException {
		//Checking if Username is already in Database
		if(DatabaseHelper.getInstance().isUsernameExists(user.getUsername())) {
			return new Response(Response.ResponseStatus.FAILURE,"User already exists");
		} else {
			DatabaseHelper.getInstance().createUser(user);
			return new Response(Response.ResponseStatus.SUCCESS,  "User added: " + user.getUsername());
		}
	}

	/**
	 * Handle request of type {@link Request.RequestType#LOGIN}
	 * @param loginUser The user object to log in to the game
	 * @return a standard response
	 * @throws SQLException When database error occurs
	 */
	private Response handleLogin(User loginUser) throws SQLException {
		Response response;

		User user = DatabaseHelper.getInstance().getUser(loginUser.getUsername());
		if(user == null) {
			response = new Response(Response.ResponseStatus.FAILURE,"User does not exists");
		} else if(!user.getPassword().equals(loginUser.getPassword())) {
			response = new Response(Response.ResponseStatus.FAILURE,"Incorrect Password");
		} else {
			user.setOnline(true);
			DatabaseHelper.getInstance().updateUser(user);
			currentUsername = user.getUsername();
			response = new Response(Response.ResponseStatus.SUCCESS, "Logged in as: " + user.getUsername());
		}
		return response;
	}

	/**
	 * Handle request of type {@link Request.RequestType#UPDATE_PAIRING}
	 * @return pairing update with available players, invitation, and invitation response
	 * @throws SQLException When database error occurs
	 */
	private PairingResponse handleUpdatePairing() throws SQLException {
		if(currentUsername == null) {
			return new PairingResponse(Response.ResponseStatus.FAILURE, "Not logged in", null, null, null);
		}
		//Getting all available users
		List<User> availableUsers = DatabaseHelper.getInstance().getAvailableUsers(currentUsername);
		//Checking for user's invitation
		Event invitation = DatabaseHelper.getInstance().getUserInvitation(currentUsername);
		//Checking for user's response to invitation
		Event invitationResponse = DatabaseHelper.getInstance().getUserInvitationResponse(currentUsername);

		return new PairingResponse(Response.ResponseStatus.SUCCESS, "Update", availableUsers, invitation, invitationResponse);
	}

	/**
	 * Handle request of type {@link Request.RequestType#SEND_INVITATION}
	 * @param opponent Username of the opponent to sent invitation to
	 * @return a standard response
	 * @throws SQLException When database error occurs
	 */
	private Response handleSendInvitation(String opponent) throws SQLException {
		Response response;
		if(currentUsername == null) {
			return new Response(Response.ResponseStatus.FAILURE, "Not logged in");
		}
		//Checking if user is available for request
		if(DatabaseHelper.getInstance().isUserAvailable(opponent)) {
			Event event = new Event();
			event.setSender(currentUsername);
			event.setOpponent(opponent);
			event.setStatus(Event.EventStatus.PENDING);
			event.setMove(-1);

			DatabaseHelper.getInstance().createEvent(event);
			response = new Response(Response.ResponseStatus.SUCCESS, "Game Invitation Sent");
		} else {
			response = new Response(Response.ResponseStatus.FAILURE, "User can not accept invitation at the moment");
		}
		return response;
	}

	/**
	 * Handle request of type {@link Request.RequestType#ACCEPT_INVITATION}
	 * @param eventId The eventId of the invitation to accept
	 * @return a standard response
	 * @throws SQLException When database error occurs
	 */
	private Response handleAcceptInvitation(int eventId) throws SQLException {
		Event event = DatabaseHelper.getInstance().getEvent(eventId);
		//Ensure it's a valid invitation and it belongs to the user
		if(event != null && event.getStatus() == Event.EventStatus.PENDING && event.getOpponent().equals(currentUsername)) {
			event.setStatus(Event.EventStatus.ACCEPTED);

			//abort all other unattended events
			DatabaseHelper.getInstance().abortAllUserEvents(currentUsername);
			DatabaseHelper.getInstance().updateEvent(event);
			currentEventId = eventId;
			return new Response(Response.ResponseStatus.SUCCESS, "Invitation Accepted");
		}else {
			return new Response(Response.ResponseStatus.FAILURE, "Invalid Event ID");
		}
	}

	/**
	 * Handle request of type {@link Request.RequestType#DECLINE_INVITATION}
	 * @param eventId The eventId of the invitation to decline
	 * @return a standard response
	 * @throws SQLException When database error occurs
	 */
	private Response handleDeclineInvitation(int eventId) throws SQLException {
		Event event = DatabaseHelper.getInstance().getEvent(eventId);
		if(event != null && event.getStatus() == Event.EventStatus.PENDING) {
			event.setStatus(Event.EventStatus.DECLINED);
			DatabaseHelper.getInstance().updateEvent(event);
			return new Response(Response.ResponseStatus.SUCCESS, "Invite Declined");
		} else {
			return new Response(Response.ResponseStatus.FAILURE, "Invalid Event ID");
		}
	}

	/**
	 * Handle request of type {@link Request.RequestType#ACKNOWLEDGE_RESPONSE}
	 * @param eventId The eventId of the invitation response to Acknowledge
	 * @return a standard response
	 * @throws SQLException When database error occurs
	 */
	private Response handleAcknowledgeResponse(int eventId) throws SQLException {
		Event event = DatabaseHelper.getInstance().getEvent(eventId);
		if(event != null && event.getSender().equals(currentUsername)) {
			if(event.getStatus() == Event.EventStatus.ACCEPTED) {
				//abort all other unattended events
				DatabaseHelper.getInstance().abortAllUserEvents(currentUsername);
				currentEventId = eventId;
				event.setStatus(Event.EventStatus.PLAYING);
			} else if(event.getStatus() == Event.EventStatus.DECLINED) {
				event.setStatus(Event.EventStatus.ABORTED);
			}
			DatabaseHelper.getInstance().updateEvent(event);
			return new Response(Response.ResponseStatus.SUCCESS, "Acknowledged");
		} else {
			return new Response(Response.ResponseStatus.FAILURE, "Invalid Event ID");
		}
	}

	/**
	 * Handle request of type {@link Request.RequestType#COMPLETE_GAME}
	 * @return a standard response
	 * @throws SQLException When database error occurs
	 */
	private Response handleCompleteGame() throws SQLException {
		Event event = DatabaseHelper.getInstance().getEvent(currentEventId);
		if(event == null || event.getStatus() != Event.EventStatus.PLAYING) {
			return new Response(Response.ResponseStatus.FAILURE, "Invalid Game");
		}
		event.setStatus(Event.EventStatus.COMPLETED);
		currentEventId = -1;
		DatabaseHelper.getInstance().updateEvent(event);
		return new Response(Response.ResponseStatus.SUCCESS, "Game Completed");
	}

	/**
	 * Handle request of type {@link Request.RequestType#ABORT_GAME}
	 * @return a standard response
	 * @throws SQLException When database error occurs
	 */
	private Response handleAbortGame() throws SQLException {
		Event event = DatabaseHelper.getInstance().getEvent(currentEventId);
		if(event == null || event.getStatus() != Event.EventStatus.PLAYING) {
			return new Response(Response.ResponseStatus.FAILURE, "Invalid Game");
		}
		event.setStatus(Event.EventStatus.ABORTED);
		DatabaseHelper.getInstance().updateEvent(event);
		currentEventId = -1;
		return new Response(Response.ResponseStatus.SUCCESS, "Game Aborted");
	}
}
