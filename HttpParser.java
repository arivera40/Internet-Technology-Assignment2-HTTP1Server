import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

public class HttpParser {
	private String cmd;
	private String resource;
	private double version;

	private Date ifModified;
	private String lastModifiedStr;

	private String payload;
	private String fromVariable;
	private String userAgentVariable;
	private int portNum;
	private String serverName;

	private File webroot;

	final SimpleDateFormat form = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
	
	public HttpParser(int portNum, String serverName) {
		cmd = "";
		resource = "";
		version = 0;

		ifModified = null;
		lastModifiedStr = "";

		payload = "";
		fromVariable = "";
		userAgentVariable = "";
		this.portNum = portNum;
		this.serverName = serverName;

		webroot = new File(".");
	}
	
	//returns int to indicate status of request
	public int parseRequest(String request) {
		String[] parsedLine = request.split(" |\r\n");
		if(parsedLine.length < 3) {
			return -1;	// 400 Bad Request
		}
		
		//Has possible If-Modified parameter OR POST with possible parameters
		if(parsedLine.length > 3) {
			//if tag is valid then check for valid date
			if(parsedLine[3].equals("If-Modified-Since:")) {
				String date = "";
				for(int i=4; i < parsedLine.length; i++) {
					date += (i != parsedLine.length - 1) ? parsedLine[i] + " " : parsedLine[i];
				}
				
				String[] tempSplit = date.split(" ");
				//if array size is 6 then most likely valid date so it is stored for comparison later
				if(tempSplit.length == 6) {
					try {
						synchronized(this){
							ifModified = form.parse(date);
						}
						
					}catch(java.text.ParseException e) {
						e.printStackTrace();
					}
				}
			//Seems to be a POST with extra parameters and possible payload
			}else if(parsedLine[0].equals("POST")){
				int status = checkPOSTHeaders(parsedLine);
				if(status == -4) return -4;	//500 Internal Server Error
				if(status == -5) return -5;	//411 Length Required
			}
		}
		
		//Command format checking
		if(parsedLine[0].equals("get") || parsedLine[0].equals("post") || parsedLine[0].equals("head") ||
				parsedLine[0].equals("delete") || parsedLine[0].equals("put") || parsedLine[0].equals("link") || parsedLine[0].equals("unlink")) {
			return -1;	// 400 Bad Request
		}else if(parsedLine[0].equals("DELETE") || parsedLine[0].equals("PUT") || parsedLine[0].equals("LINK") || parsedLine[0].equals("UNLINK")) {
			return -2; // 501 Not Implemented
		}else if(parsedLine[0].equals("GET") || parsedLine[0].equals("POST") || parsedLine[0].equals("HEAD")){
			cmd = parsedLine[0];
		}else {
			return -1; // 400 Bad Request
		}
		
		//Resource
		resource = parsedLine[1];
		
		//Version number format checking
		if(parsedLine[2].indexOf("HTTP/") != 0 || !Character.isDigit(parsedLine[2].charAt(5)) ) {
			return -1;	// 400 Bad Request
		}else {
			boolean decimal = false;
			parsedLine[2] = parsedLine[2].substring(5); //-4 to remove \r\n
			for(int i=1; i < parsedLine[2].length(); i++) {	//we know first index is a digit
				if(!Character.isDigit(parsedLine[2].charAt(i)) && parsedLine[2].charAt(i) != '.') {
					return -1; // 400 Bad Request - version number contains a alphabetic character instead of numeric
				}else if(parsedLine[2].charAt(i) == '.' && decimal == true) {
					return -1; // 400 Bad Request - multiple decimals
				}else if(parsedLine[2].charAt(i) == '.') {
					decimal = true;
				}
			}
			version = Double.parseDouble(parsedLine[2]);
			if(version > 1.0) {
				return -3; //version not supported
			}
		}

		if(cmd.equals("POST") && !resource.endsWith(".cgi")) return -6;
		if(!payload.equals("")) decodePayload();
		return 0;
	}

	//Checks existing POST headers returning an int code identifying any missing essential headers otherwise the content length itself
	private int checkPOSTHeaders(String[] parsedLine){
		boolean hasContentType = false;
		boolean hasContentLength = false;
		int contentLength = 0;
		for(int i=3; i < parsedLine.length; i++){
			if(parsedLine[i].equals("From:")){
				fromVariable = parsedLine[i+1];
				i++;
			}else if(parsedLine[i].equals("User-Agent:")){
				userAgentVariable = parsedLine[i+1];
				i++;
			}else if(parsedLine[i].equals("Content-Type:")){
				hasContentType = true;
			}else if(parsedLine[i].equals("Content-Length:")){
				hasContentLength = true;
				try{
					if(Integer.parseInt(parsedLine[i+1]) > 0) payload = parsedLine[i+3];
				}catch(NumberFormatException e){
					return -5;
				}
				break;
			}
		}
		if(!hasContentType) return -4;
		if(!hasContentLength) return -5;

		return contentLength;
	}

	//Helper function that decodes the encoded payload
	private void decodePayload(){
		String decoded = "";
		for(int i=0; i < payload.length(); i++){
			if(payload.charAt(i) != '!'){
				decoded += payload.charAt(i);
			}else if(payload.charAt(i) == '!' && payload.charAt(i+1) == '!'){
				decoded += '!';
				i += 1; 
			}
		}
		payload = decoded;
	}
	
	//Retrieves MIME type of requested file
	private String getMIME() {
		if(resource.endsWith("html") || resource.endsWith("plain")) {
			return "text/" + ((resource.endsWith("html")) ? "html" : "plain");
		}else if(resource.endsWith("gif") || resource.endsWith("jpeg") || resource.endsWith("png")) {
			return "image/" + ((resource.endsWith("gif")) ? "gif" : (resource.endsWith("jpeg")) ? "jpeg" : "png");
		}else if(resource.endsWith("octet-stream") || resource.endsWith("pdf") || resource.endsWith("x-gzip") || resource.endsWith("zip")) {
			return "application/" + ((resource.endsWith("octet-stream")) ? "octet-stream" : (resource.endsWith("pdf")) ? "pdf" :
				(resource.endsWith("x-gzip")) ? "x-gzip" : "zip");
		}else {
			return "application/octet-stream";
		}
	}
	
	//Retrieves requested file contained within GET request
	private byte[] getRequestedFile(File file, int length) {
		byte[] data = new byte[length];
		try {
			FileInputStream fileInputStream = new FileInputStream(file);
			fileInputStream.read(data);
			fileInputStream.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
		return data;
	}
	
	public void getHttpResponse(PrintWriter head, BufferedOutputStream body) {
		File file = new File(webroot, resource);
		//Checks to see if file exists within webroot, otherwise responds with 404 Not Found
		if(!file.exists()) {
			head.println("HTTP/1.0 404 Not Found" + "\r\n");
			head.flush();
			return;
		}

		//Checks reading permission of existing file, if reading is not allowed responds with 403 Forbidden
		if(!file.canRead()) {
			head.println("HTTP/1.0 403 Forbidden" + "\r\n");
			head.flush();
			return;
		}

		int contentLength = (int) file.length();
		Date lastModified = getModifiedDate(new Date(file.lastModified()));
		String type = getMIME();

		//Date object created for comparing purposes
		Date expiration = new GregorianCalendar(2021, Calendar.OCTOBER, 2).getTime();
		String expirationStr = form.format(expiration);
		
		if(cmd.equals("GET")) {
			if(cmd.equals("GET") && ifModified != null) {
				//Checks if file was last modified before the if modified header, if not then responds with 304 Not Modified
				if(lastModified.before(ifModified)) {
					head.print("HTTP/1.0 304 Not Modified" + "\r\n" + 
					"Expires: " + expirationStr + "\r\n");
					head.flush();
					return;
				}
			}
			byte[] requestedFileData = getRequestedFile(file, contentLength);

			//Outputs properly formatted HTTP GET response with the requested file
			head.print("HTTP/1.0 200 OK" + "\r\n" + 
			"Content-Type: " + type + "\r\n" + 
			"Content-Length: " + contentLength + "\r\n" + 
			"Last-Modified: " + lastModifiedStr + "\r\n" + 
			"Content-Encoding: identity" + "\r\n" +
			"Allow: GET, POST, HEAD" + "\r\n" + 
			"Expires: " + expirationStr + "\r\n\r\n");
			head.flush();
			try {
				body.write(requestedFileData, 0, contentLength);
				body.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}		
		}else if(cmd.equals("POST")){
			String cgiOutput = executeCGI();
			int outputLength = cgiOutput.length();

			//Checks to see if cgi output returns any content, if not server responds with 204 No Content, otherwise proceeds with 200 OK as normal
			if(outputLength < 1){
				head.print("HTTP/1.0 204 No Content" + "\r\n");
			}else{
				head.print("HTTP/1.0 200 OK" + "\r\n");
			}
			//Outputs properly formatted HTTP POST response along with cgi output if content exists
			head.print("Content-Type: " + "text/html" + "\r\n" +
			"Content-Length: " + outputLength + "\r\n" + 
			"Last-Modified: " + lastModifiedStr + "\r\n" +
			"Content-Encoding: identity" + "\r\n" +
			"Allow: GET, POST, HEAD" + "\r\n" +
			"Expires: " + expirationStr + "\r\n\r\n");
			if(outputLength >= 1) head.print(cgiOutput);
			head.flush();

		}else {
			//Outputs properly formatted HTTP HEAD response
			head.print("HTTP/1.0 200 OK" + "\r\n" + 
			"Content-Type: " + type + "\r\n" + 
			"Content-Length: " + contentLength + "\r\n" + 
			"Last-Modified: " + lastModifiedStr + "\r\n" + 
			"Content-Encoding: identity" + "\r\n" +
			"Allow: GET, POST, HEAD" + "\r\n" + 
			"Expires: " + expirationStr + "\r\n\r\n");
			head.flush();
		}
		return;
	}

	//Loads environmental variables
	private String [] setEnvironmentalVars(){
		String [] variables = new String[6];
		variables[0] = "CONTENT_LENGTH=" + payload.getBytes().length;
		variables[1] = "SCRIPT_NAME=" + resource;
		variables[2] = "SERVER_NAME=" + serverName;
		variables[3] = "SERVER_PORT=" + portNum;
		variables[4] = "HTTP_FROM=" + ((!fromVariable.equals("")) ? fromVariable : "null");
		variables[5] = "HTTP_USER_AGENT=" + ((!userAgentVariable.equals("")) ? userAgentVariable : "null");
		return variables;
	}

	//Executes cgi of requested file and returns the output
	private String executeCGI(){
		try{
			Runtime run = Runtime.getRuntime();
			String [] environmentVars = setEnvironmentalVars();

			Process proc = run.exec("." + resource, environmentVars);
			
			if(!payload.equals("")){
				proc.getOutputStream().write(payload.getBytes());
				proc.getOutputStream().close();
			}

			InputStream stdInput = proc.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(stdInput));

			StringBuilder output = new StringBuilder();

			String line;
			while((line = br.readLine()) != null){
				output.append(line + "\n");
			}

			br.close();
			stdInput.close();

			String outputStr = output.toString();

			if(outputStr.endsWith("\n\n")) return outputStr.substring(0, outputStr.length()-1);
			
			return outputStr;

        }catch(IOException e){
            e.printStackTrace();
		}
		return "Error";
	}
	
	//Creates date object to compare for GET requests
	synchronized private Date getModifiedDate(Date date) {
		Date lastModified = null;
		form.setTimeZone(TimeZone.getTimeZone("GMT"));
		lastModifiedStr = form.format(date);
		try {
			lastModified = form.parse(lastModifiedStr);
		}catch(java.text.ParseException e) {
			e.printStackTrace();
		}
		return lastModified;
	}

}
