package test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import model.User;
import server.DatabaseHelper;
import server.SocketServer;
import socket.PairingResponse;
import socket.Request;
import socket.Response;

import java.sql.SQLException;


/**
 * Test class for Pairing
 *
 * @author Ahmad Suleiman
 */
public class PairingTest {

    /**
     * Runs the test
     * @param args command line arguments
     * @throws Exception When Database error occurs
     */
    public static void main(String[] args) throws Exception {
        Thread mainThread= new Thread(() -> {
            try {
                DatabaseHelper.getInstance().truncateTables();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            SocketServer.main(null);
        });
        mainThread.start();
        Thread.sleep(1000);


        // Used for Serialization
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

        // Users
        User user1 = new User("user1", "1234", "Smith Alex", false);
        User user2 = new User("user2", "1234", "Bob Johnson", false);
        User user3 = new User("user3", "1234", "Rebacca Will", false);
        User user4 = new User("user4", "1234", "Ahmad Suleiman", false);

        //SocketClients
        SocketClientHelper scUser1 = new SocketClientHelper();
        SocketClientHelper scUser2 = new SocketClientHelper();
        SocketClientHelper scUser3 = new SocketClientHelper();
        SocketClientHelper scUser4 = new SocketClientHelper();

        // Test 1
        System.out.println("Test 1: Testing Invalid username");
        Request request = new Request(Request.RequestType.LOGIN, gson.toJson(user1));
        Response response = scUser1.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Test 2
        System.out.println("Test 2: Testing register");
        request = new Request(Request.RequestType.REGISTER, gson.toJson(user1));
        response = scUser1.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Test 3
        System.out.println("Test 3: Testing Incorrect password");
        user1.setPassword("4321");
        request = new Request(Request.RequestType.LOGIN, gson.toJson(user1));
        response = scUser1.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Test 4
        System.out.println("Test 4: Testing Correct password");
        user1.setPassword("1234");
        request = new Request(Request.RequestType.LOGIN, gson.toJson(user1));
        response = scUser1.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Register the other 3 users
        scUser2.sendRequest(new Request(Request.RequestType.REGISTER, gson.toJson(user2)), Response.class);
        scUser3.sendRequest(new Request(Request.RequestType.REGISTER, gson.toJson(user3)), Response.class);
        scUser4.sendRequest(new Request(Request.RequestType.REGISTER, gson.toJson(user4)), Response.class);

        // Test 5
        System.out.println("Test 5: Testing Empty PairingResponse");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        PairingResponse pairingResponse = scUser1.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Test 6
        System.out.println("Test 6: Testing UPDATE_PAIRING when not logged in");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser2.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Login user2
        scUser2.sendRequest(new Request(Request.RequestType.LOGIN, gson.toJson(user2)), Response.class);

        // Test 7
        System.out.println("Test 7: Testing PairingResponse with one available user");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser1.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Login user3 and user4
        scUser3.sendRequest(new Request(Request.RequestType.LOGIN, gson.toJson(user3)), Response.class);
        scUser4.sendRequest(new Request(Request.RequestType.LOGIN, gson.toJson(user4)), Response.class);

        // Test 8
        System.out.println("Test 8: Testing PairingResponse with 3 available user");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser2.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Disconnect user4
        scUser4.close();

        // Test 9
        System.out.println("Test 9: Testing PairingResponse after user4 disconnects");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser2.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Reconnect and login user4
        scUser4 = new SocketClientHelper();
        scUser4.sendRequest(new Request(Request.RequestType.LOGIN, gson.toJson(user4)), Response.class);

        // Test 10
        System.out.println("Test 10: Testing PairingResponse after user4 reconnects");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser2.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Test 11
        System.out.println("Test 11: Test Send invitation from user1 to user2");
        request = new Request(Request.RequestType.SEND_INVITATION, gson.toJson(user2.getUsername()));
        response = scUser1.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Test 12
        System.out.println("Test 12: Testing PairingResponse after with an invitation");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser2.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Test 13
        System.out.println("Test 13: Testing Decline invitation by user2");
        request = new Request(Request.RequestType.DECLINE_INVITATION, gson.toJson(pairingResponse.getInvitation().getEventId()));
        response = scUser2.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Test 14
        System.out.println("Test 14: Testing PairingResponse after with a declined invitation response");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser1.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Test 15
        System.out.println("Test 15: Test Acknowledge response by user1");
        request = new Request(Request.RequestType.ACKNOWLEDGE_RESPONSE, gson.toJson(pairingResponse.getInvitationResponse().getEventId()));
        response = scUser1.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Test 16
        System.out.println("Test 16: Test Send invitation from user1 to user3");
        request = new Request(Request.RequestType.SEND_INVITATION, gson.toJson(user3.getUsername()));
        response = scUser1.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Test 17
        System.out.println("Test 17: Testing PairingResponse after with an invitation");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser3.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Test 18
        System.out.println("Test 18: Testing Accept invitation by user3");
        request = new Request(Request.RequestType.ACCEPT_INVITATION, gson.toJson(pairingResponse.getInvitation().getEventId()));
        response = scUser3.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Test 19
        System.out.println("Test 19: Testing PairingResponse after with an accepted invitation response");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser1.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Test 20
        System.out.println("Test 20: Test Acknowledge response by user1");
        request = new Request(Request.RequestType.ACKNOWLEDGE_RESPONSE, gson.toJson(pairingResponse.getInvitationResponse().getEventId()));
        response = scUser1.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Test 21
        System.out.println("Test 21: Testing PairingResponse with one available user");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser2.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        // Test 22
        System.out.println("Test 22: Testing Abort Game");
        request = new Request(Request.RequestType.ABORT_GAME, null);
        response = scUser1.sendRequest(request, Response.class);
        System.out.println(gson.toJson(response));

        // Test 23
        System.out.println("Test 23: Testing PairingResponse with three available user");
        request = new Request(Request.RequestType.UPDATE_PAIRING, null);
        pairingResponse = scUser2.sendRequest(request, PairingResponse.class);
        System.out.println(gson.toJson(pairingResponse));

        //Close SocketClients
        scUser1.close();
        scUser2.close();
        scUser3.close();
        scUser4.close();
    }

}