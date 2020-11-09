import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;


public class HTTP1Server {

	public static void main(String[] args) {
		int portNum = Integer.parseInt(args[0]);
		ServerSocket socket = null;
		try {
			//Server is listening to port
			socket = new ServerSocket(portNum);
			
			//Capacity for concurrent connections will start at 5
			ArrayList<Thread> connections = new ArrayList<Thread>(5);
			//running infinite loop to accept multiple client connections
			System.out.println("Server started.\nListening for connections on port : " + portNum + " ...\n");
			while(true) {
				int index = contractList(connections);
				if(connections.size() <= 50 || index != -1) {
					Socket connection = socket.accept();
					
					//Obtaining input and output streams to pass to thread
					InputStream input_stream = connection.getInputStream();
					OutputStream output_stream = connection.getOutputStream();
					
					//Create thread used to take care of communication between client and server
					Thread t = new ClientHandler(connection, input_stream, output_stream);
					t.start();
				}else {
					Socket connection = socket.accept();
					
					OutputStream output_stream = connection.getOutputStream();
					PrintWriter response = new PrintWriter(output_stream);
					
					response.println("HTTP/1.0 503 Service Unavailable" + "\r\n");
					response.flush();
					response.close();
					output_stream.close();
					connection.close();
				}
			}
		} catch (IOException e) {
		    if (socket != null && !socket.isClosed()) {
		        try {
		            socket.close();
		        } catch (IOException f){
		            f.printStackTrace(System.err);
		        }
		    }
		}

	}
	
	public static int contractList(ArrayList<Thread> connections) {
		int index = -1;
		for(int i=0; i < connections.size(); i++) {
			if(!connections.get(i).isAlive()) {
				index = (index == -1) ? i : index;
				connections.remove(i);
			}
		}
		return index;
	}


}
