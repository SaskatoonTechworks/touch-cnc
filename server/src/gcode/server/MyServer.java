package gcode.server;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class MyServer {
 
	public static void main(String[] args){
		  ServerSocket serverSocket = null;
		  Socket socket = null;
		  BufferedReader in = null;
		  FileWriter outFile = null;
		  
		  int lineNum = 0;
		  
		  String filePath = "gcode.txt";
		  
		  try{
			  outFile = new FileWriter(filePath); //open file (erases contents)
		  } catch (IOException e) {
			  System.out.println("IOException: " + e.toString());
			  System.exit(1);
		  }
		  
		  String line;
		  
		  try {
			  serverSocket = new ServerSocket(2734);
			  System.out.println(InetAddress.getLocalHost().toString());
		   	  System.out.println("Listening on port 2734");
		  } catch (IOException e) {
			  // TODO Auto-generated catch block
			  e.printStackTrace();
		  }
		  
		  try {	   
			  socket = serverSocket.accept();
			  in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		  } catch (IOException e) {
			  // TODO Auto-generated catch block
			  e.printStackTrace();
			  System.exit(-1);
		  }
		  
		  System.out.println("Input Stream opened: " + in.toString());
		  
		  try{
			  while((line = in.readLine()) != null)
			  {
				  System.out.println(line);
				  lineNum+=1;
				  if(line == "exit")
				  {
					  //need to stop cnc now, so fill buffer with exit commands...
					  outFile.close();
					  outFile = new FileWriter(filePath);
					  for(int i=0;i<=lineNum+10;i++)
					  {
						  	outFile.write("exit\r\n");
					  }
					  outFile.close();
					  System.out.println("Abandon ship!");
					  System.exit(-1);
				  }
		    	  outFile.write(line + "\r\n");
		    	  outFile.flush();
			  }
		  } catch (IOException e) {
		    	  System.out.println("Read failed: " + e.toString());
		    	  System.exit(-1);
		  }
		  
		  //close file
		  try{
			  outFile.close();
		  } catch(IOException e) {
			  System.out.println("IOException: " + e.toString());
			  System.exit(1);
		  }
	}	  
}
