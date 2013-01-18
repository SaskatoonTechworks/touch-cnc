package com.kaldonis.touchcnc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.media.AudioManager;


public class TouchCNC extends View implements OnTouchListener, OnLongClickListener {
	
    //for drawing points on screen
    Paint paint = new Paint();
    List<TouchCNCPath> toolPath = new ArrayList<TouchCNCPath>();
    TouchCNCPath currentPath;
	float lastX,lastY,firstX,firstY;
	String cutMode = "Path";
    
	//actual table/piece size... is variable within app
	public Float tableY;
	public Float tableX;
	
	public Integer feedRate;
	
	//need to know size of our screen in order to draw
	Integer screenWidth, screenHeight;
	
	//pixel size representation of table (should have same aspect ratio as actual table size)
	Integer viewX, viewY;
	
	//helper booleans to tell what's going on
	boolean isCutting = false;
	boolean inBounds = false;
	boolean isDrawing = false;
	
	//variables for setting up background
	public boolean backgroundSet = false;
	ShapeDrawable background = new ShapeDrawable(new RectShape());		
	int backgroundX,backgroundY;
	
	//network communication stuff
	Socket socket;
	OutputStream nos;
	
	AudioManager am;
	
	public void sendCommand(String command)
	{
		String toSend = command + "\r\n";
		Log.d("tool", command);

		try {
            if (socket.isConnected()) {
                nos.write(toSend.getBytes());
            } else {
                Log.d("Network", "SendDataToNetwork: Cannot send message. Socket is closed");
            }
        } catch (Exception e) {
            Log.d("Network", "SendDataToNetwork: " + e.toString());
        }       
	}
	
	public Float convertX(float x)
	{
		return (x-backgroundX) * (tableX / viewX.floatValue()); //scale down to inches
		
	}
	
	public Float convertY(float y)
	{
		return tableY - ((y-backgroundY) * (tableY / viewY.floatValue()));			
	}
	
	public void toolUp()
	{
		Float x1 = convertX(firstX);
		Float x2 = convertX(lastX);
		Float y1 = convertY(firstY);
		Float y2 = convertY(lastY);
		
		Log.d("path","cutting: " + cutMode);
		
		//need to figure out if circle will try to cut outside of path... add/subtract radius from center
		double circleRad = Math.abs(Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1)));
		if(cutMode == "Circle" && (x1 - circleRad < 0 || x1 + circleRad > tableX || y1 - circleRad < 0 || y1 + circleRad > tableY))
		{
			currentPath = null;
			isCutting = false;
			invalidate();
			return;
		}
		
		if (cutMode == "Line")
		{
			currentPath.setLine(firstX,firstY,lastX,lastY);
			sendCommand("G00 X" + x1 + " Y" + y1); //rapid to start point
			sendCommand("G01 Z" + TouchCNCActivity.zCutDepth); //lower tool
			sendCommand("G01 X" + x2 + " Y" + y2 + " F" + feedRate); //cut to end point
		}
		else if (cutMode == "Rectangle")
		{
			currentPath.setRect(firstX,firstY,lastX,lastY);
			sendCommand("G00 X" + x1 + " Y" + y1); //rapid to start point
			sendCommand("G01 Z" + TouchCNCActivity.zCutDepth); //lower tool
			sendCommand("G01 X" + x1 + " Y" + y2 + " F" + feedRate); //cut side 1
			sendCommand("G01 X" + x2 + " Y" + y2 + " F" + feedRate); //cut side 2
			sendCommand("G01 X" + x2 + " Y" + y1 + " F" + feedRate); //cut side 3
			sendCommand("G01 X" + x1 + " Y" + y1 + " F" + feedRate); //cut side 4
		}
		else if (cutMode == "Circle")
		{
			Float xOffset = x1-x2;
			Float yOffset = y1-y2;
			currentPath.setCircle(firstX,firstY,lastX,lastY);
			sendCommand("G00 X" + x2 + " Y" + y2); //rapid to start point
			sendCommand("G01 Z" + TouchCNCActivity.zCutDepth); //lower tool
			sendCommand("G02 X" + x2 //point on outside edge X,Y
					     + " Y" + y2 
					     + " I" + xOffset //center point I,J
					     + " J" + yOffset
					     + " F" + feedRate);			
		}
		
		sendCommand("G01 Z0");
		isCutting = false;
		
		if(!currentPath.isEmpty())
		{
			Log.d("path","adding path to list");
			currentPath.cutDepth = TouchCNCActivity.zCutDepth;
			toolPath.add(currentPath);		
			currentPath = null;
			invalidate();
		}
		
	}
	
	public void toolDown()
	{
		
		float vol = 1.0f; //This will be half of the default system sound
		am.playSoundEffect(AudioManager.FX_KEY_CLICK, vol);		
		
		if(cutMode == "Path")
			sendCommand("G01 Z" + TouchCNCActivity.zCutDepth.toString());
		
		isCutting = true;		
		currentPath = new TouchCNCPath();
		
		if(cutMode == "Path")
		{
			currentPath.moveTo(firstX, firstY);
			currentPath.lineTo(firstX, firstY);
		}
		else if(cutMode == "Line")
		{
			currentPath.setLine(firstX,firstY,lastX,lastY);
		}
		else if(cutMode == "Rectangle")
		{
			currentPath.setRect(firstX,firstY,lastX,lastY);
		}
		else if(cutMode == "Circle")
		{
			currentPath.setCircle(firstX,firstY,lastX,lastY);
		}
	}
	
	public boolean onLongClick(View view)
	{	
		if(firstX > backgroundX && firstX < backgroundX+viewX && firstY > backgroundY && firstY < backgroundY+viewY)
		{
			toolDown();
		}
		return true;
	}
	
	public boolean onTouch(View view, MotionEvent event)
	{
		//variable for holding gcode instruction
		String instruction = null;
		Float touchX, touchY;
		
		touchX = event.getX();
		touchY = event.getY();

		//make sure we're within the boundaries
		if(touchX < backgroundX || touchX > backgroundX+viewX || touchY < backgroundY || touchY > backgroundY+viewY)
		{
			Log.d("tool","touch out of bounds");
			inBounds = false;
			if(isCutting && cutMode == "Path")
			{
				toolUp();
			}
			else if(isCutting) //other than Path
			{
				isCutting = false;
				currentPath.reset();
				currentPath = null;
			}
			invalidate();
			return false;
		}	
		else
			inBounds = true;
		
		//handle type of action
		switch(event.getAction())
		{
			case MotionEvent.ACTION_DOWN:	
				if(!isCutting) //if not down, do a rapid move to wherever was touched
					sendCommand("G00 X" + convertX(touchX) + " Y" + convertY(touchY));
				
				firstX = event.getX();
				firstY = event.getY();
				lastX = firstX;
				lastY = firstY;
				return false;
		
			case MotionEvent.ACTION_UP:
				if(isCutting)
				{
					toolUp();
				}
				break;
		}
		
		
		//ignore tiny deltas
		if(Math.abs(lastX-touchX)<10 && Math.abs(lastY-touchY)<10)
			return false;	
		this.cancelLongPress();
		
		lastX = touchX;
		lastY = touchY;					
		
		if(isCutting)
		{
			if(cutMode == "Path")
			{
				currentPath.lineTo(touchX, touchY);
				instruction = "G01 X" + convertX(touchX).toString() + " Y" + convertY(touchY).toString() + " F" + feedRate;
			}
			else if (cutMode == "Line")
			{
				currentPath.setLine(firstX,firstY,lastX,lastY);
			}
			else if (cutMode == "Rectangle")
			{
				currentPath.setRect(firstX,firstY,lastX,lastY);
			}
			else if (cutMode == "Circle")
			{
				currentPath.setCircle(firstX,firstY,lastX,lastY);
			}
			invalidate();
		}
		else if(!isCutting)
		{
			//do a G00 (rapid)
			instruction = "G00 X" + convertX(touchX).toString() + " Y" + convertY(touchY).toString(); 
		}
				
		if(instruction != null)
			sendCommand(instruction);
		
		return true;
	}
	
	public void setupSocket()
	{
		while(socket == null || !socket.isConnected())
		{
			try {
	            Log.d("network", "Creating socket");
	            SocketAddress sockaddr = new InetSocketAddress(TouchCNCActivity.serverAddr, 2734);
	            socket = new Socket();
	            socket.connect(sockaddr, 5000); //10 second connection timeout
	            if (socket.isConnected()) { 
	                nos = socket.getOutputStream();
	                Log.d("network", "Socket created, stream assigned");
	            }
	        } catch (IOException e) {
	            e.printStackTrace();
	            Log.d("network", "IOException: " + e.toString());
	        } catch (Exception e) {
	            e.printStackTrace();
	            Log.d("network", "Exception: " + e.toString());
	        }
			try{
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				System.exit(-1);				
			}
		}
	}
	
	public void setupCNC()
	{
		//tells mach3 to use "absolute distance" mode
		sendCommand("G90");
		
		//"incremental IJ" mode (for arcs)
		sendCommand("G91.1");
	}
	
    public TouchCNC(Context context) {    	
        super(context);
        
        //setup paint brush
        paint.setDither(true);
        paint.setColor(0xFFFF0000);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.abs(TouchCNCActivity.zCutDepth) * 8);
    	
    	//get device screen dimensions
    	WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    	Display display = wm.getDefaultDisplay();
    	screenWidth = display.getWidth();
    	screenHeight = display.getHeight();
        
    	//create audio manager to handle playing sounds
    	am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    	
        setupSocket();
        
        //send any initial instructions that are necessary
        setupCNC();
        
        //grab stored values for things
        tableX = TouchCNCActivity.prefs.getFloat("com.kaldonis.touchcnc.tableX", 40.0f);
        tableY = TouchCNCActivity.prefs.getFloat("com.kaldonis.touchcnc.tableY", 20.0f);
        feedRate = TouchCNCActivity.prefs.getInt("com.kaldonis.touchcnc.feedRate",80);
        
	    //set up touch handler
        this.setOnLongClickListener(this);
	    this.setOnTouchListener(this);
    } 
    
    @Override
    public void onDraw(Canvas c)
    {
    	super.onDraw(c);
        
    	//draw background
    	if(!backgroundSet)
    	{
    		//calculate viewX/viewY (maximum pixel representation of tableX/tableY)
    		if((tableX/tableY) > (screenWidth/screenHeight))
    		{
    			viewX = screenWidth;
    			viewY = Math.round(viewX.floatValue()/(tableX/tableY));
    		}
    		else
    		{
    			viewY = screenHeight;
    			viewX = Math.round(viewY.floatValue()*(tableX/tableY));
    		}
    			
    		backgroundX = this.getWidth()-viewX-((this.getWidth()-viewX)/2);
    		backgroundY = this.getHeight()-viewY-((this.getHeight()-viewY)/2);
            background.getPaint().setColor(0xff74AC23); //gross green :)
            background.setBounds(backgroundX,backgroundY,backgroundX+viewX,backgroundY+viewY);
            backgroundSet = true;
    	}
    	background.draw(c);
    	
    	paint.setColor(Color.BLACK);
    	//draw old paths
    	Log.d("draw","drawing " + String.valueOf(toolPath.size()) + " previous paths");
    	for (TouchCNCPath path : toolPath) {
            paint.setStrokeWidth(Math.abs(path.cutDepth)*8);
    		c.drawPath(path, paint);
    	}
    	
    	if(currentPath != null)
    	{
    		Log.d("draw","drawing current path");
    		paint.setStrokeWidth(TouchCNCActivity.zCutDepth);
    		paint.setColor(Color.GRAY);
    		//draw current path
    		c.drawPath(currentPath,paint);
    	}
    } 
}