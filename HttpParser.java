import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Date;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class HttpParser {
	private String cmd;
	private String resource;
	private double version;

	private Date ifModified;
	private String lastModifiedStr;

	private String payload;
	
	private File webroot;

	final SimpleDateFormat form = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
	
	public HttpParser() {
		cmd = "";
		resource = "";
		version = 0;

		ifModified = null;
		lastModifiedStr = "";

		payload = null;

		webroot = new File(".");
	}
	
	//returns int to indicate status of request
	public int parseRequest(String request) {
		String[] parsedLine = request.split(" |\r\n");
		// for(int i=0; i < parsedLine.length; i++){
		// 	System.out.println(parsedLine[i]);
		// }
		// System.out.println();

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
			}else if(parsedLine[3].equals("From:")){
				if(!parsedLine[7].equals("Content-Type:") && !parsedLine[8].equals("application/x-www-form-urlencoded")){
					return -4; // 500 Internal Server Error
				}else if(!parsedLine[9].equals("Content-Length:")){
					return -5; // 411 Length Required
				}else{
					//Has POST payload if Content-Length > 0
					try{
						if(Integer.parseInt(parsedLine[10]) > 0) payload = parsedLine[12];
					}catch(NumberFormatException e){
						return -5;	//411 Length Required when Content-Length value not numeric
					}
				}
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
		
		//Resource - Not sure if any format checking is necessary?
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

		if(payload != null) decodePayload();
		System.out.println("payload is: " + payload);
		return 0;
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
		System.out.println(file.toString());
		if(!file.exists()) {
			head.println("HTTP/1.0 404 Not Found" + "\r\n");
			head.flush();
			return;
		}

		if(!file.canRead()) {
			head.println("HTTP/1.0 403 Forbidden" + "\r\n");
			head.flush();
			return;
		}
		int contentLength = (int) file.length();
		Date lastModified = getModifiedDate(new Date(file.lastModified()));

		String type = getMIME();

		Date expiration = new GregorianCalendar(2021, Calendar.OCTOBER, 2).getTime();
		String expirationStr = form.format(expiration);
		
		if(cmd.equals("GET")) {
			if(cmd.equals("GET") && ifModified != null) {
				if(lastModified.before(ifModified)) {
					head.print("HTTP/1.0 304 Not Modified" + "\r\n" + "Expires: " + expirationStr + "\r\n");
					head.flush();
					return;
				}
			}
			byte[] requestedFileData = getRequestedFile(file, contentLength);
			head.print("HTTP/1.0 200 OK" + "\r\n" + "Content-Type: " + type + "\r\n" + "Content-Length: " + contentLength + "\r\n" + 
						"Last-Modified: " + lastModifiedStr + "\r\n" + "Content-Encoding: identity" + "\r\n" +
						"Allow: GET, POST, HEAD" + "\r\n" + "Expires: " + expirationStr + "\r\n\r\n");
			head.flush();
			try {
				body.write(requestedFileData, 0, contentLength);
				body.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}		
		}else if(cmd.equals("POST")){
			CGIHandler cgiHandler = new CGIHandler(file.toString(), "");
			System.out.println(cgiHandler.executeCGI());
		}else {
			head.print("HTTP/1.0 200 OK" + "\r\n" + "Content-Type: " + type + "\r\n" + "Content-Length: " + contentLength + "\r\n" + 
			"Last-Modified: " + lastModifiedStr + "\r\n" + "Content-Encoding: identity" + "\r\n" +
			"Allow: GET, POST, HEAD" + "\r\n" + "Expires: " + expirationStr + "\r\n\r\n");
			head.flush();
		}
		return;
	}
	
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
