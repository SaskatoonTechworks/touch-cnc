package com.kaldonis.touchcnc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class TouchCNCActivity extends Activity{
    TouchCNC cncView;
    
    public static SharedPreferences prefs;
    
    public static String serverAddr;
    public static Float zCutDepth = -0.25f; //just a default cut depth, can be changed in app
    public static AudioManager am;
    
    public LinearLayout layout;
    
    Context context;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //grab the last IP that was used, if any
        prefs = this.getSharedPreferences("com.kaldonis.touchcnc", Context.MODE_PRIVATE);
        serverAddr = prefs.getString("com.kaldonis.touchcnc.serverAddr", "0.0.0.0");
        
        layout = new LinearLayout(this);
        setContentView(layout);
        
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
	    StrictMode.setThreadPolicy(policy);		
	    
		// Set an EditText view to get user input 
		final EditText input = new EditText(this);
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		
		context = this;

		alert.setTitle("Enter Server IP");
		alert.setMessage("Enter Server IP");
		alert.setView(input);
		
		input.setInputType(InputType.TYPE_CLASS_PHONE);
		input.setText(serverAddr);
		
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int whichButton) {
			String value = input.getText().toString();    		  
			serverAddr = value;
			prefs.edit().putString("com.kaldonis.touchcnc.serverAddr", serverAddr).commit();
			
	        cncView = new TouchCNC(context);
	        cncView.setBackgroundColor(Color.WHITE);
	        layout.addView(cncView);
		  }
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
		  public void onClick(DialogInterface dialog, int whichButton) {
		    System.exit(1);
		  }
		});

		alert.show();    

    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	super.onCreateOptionsMenu(menu);

       	menu.add("Exit");
    	SubMenu sm = menu.addSubMenu("Cut Mode");
    	sm.add("Path");
       	sm.add("Line");
       	sm.add("Rectangle");
       	sm.add("Circle");
       	menu.add("Cut Depth");
       	menu.add("Cut Area");
       	menu.add("Clear Screen");
    	
    	return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	String title = item.getTitle().toString();
    	Log.d("menu",title);
    	if(title == "Exit")
    	{
    		cncView.sendCommand("exit");
    		System.exit(-1);
    	}
    	else if(title == "Cut Depth")
    	{
        	// Set an EditText view to get user input 
    		final EditText input = new EditText(this);
     		AlertDialog.Builder alert = new AlertDialog.Builder(this);

     		input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
     		input.setHint(zCutDepth.toString());
     		
    		alert.setTitle("Cut Depth");
    		alert.setMessage("Cut Depth");
    		alert.setView(input);

    		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			String value = input.getText().toString();    		  
    			zCutDepth = Float.valueOf(value);
    		  }
    		});

    		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    		  public void onClick(DialogInterface dialog, int whichButton) {		    
    		  }
    		});

    		alert.show();
    	}
    	else if(title == "Cut Area")
    	{
        	// Set an EditText view to get user input 
    		final EditText input = new EditText(this);
     		AlertDialog.Builder alert = new AlertDialog.Builder(this);

     		input.setHint(cncView.tableX.toString() + "," + cncView.tableY.toString());
     		
    		alert.setTitle("Cut Area");
    		alert.setMessage("Specify piece dimensions in format 'Width,Height' (inches)");
    		alert.setView(input);

    		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			String value = input.getText().toString();
    			cncView.tableX = Float.valueOf(value.split(",")[0]);
    			cncView.tableY = Float.valueOf(value.split(",")[1]);
    			cncView.backgroundSet = false;
    			cncView.invalidate();
    		  }
    		});

    		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    		  public void onClick(DialogInterface dialog, int whichButton) {		    
    		  }
    		});

    		alert.show();
    	}
    	else if(title == "Path" || title == "Rectangle" || title == "Line" || title == "Circle")
    	{
    		Toast modeToast = Toast.makeText(this,"Cut Mode: " + title,Toast.LENGTH_LONG);
    		modeToast.show();
    		cncView.cutMode = title;
    	}
    	else if(title == "Clear Screen")
    	{
    		cncView.toolPath.clear();
    		cncView.invalidate();
    	}
    	
    	return true;
    }
}
