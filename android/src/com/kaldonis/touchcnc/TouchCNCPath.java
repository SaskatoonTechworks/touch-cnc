package com.kaldonis.touchcnc;

import android.graphics.Path;

public class TouchCNCPath extends Path{
	public float cutDepth;
	
	public void setCircle(float x1, float y1, float x2, float y2)
	{
		this.reset();
		this.addCircle(x1,y1,(float)Math.abs(Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1))),Path.Direction.CW);
	}
	
	public void setRect(float x1, float y1, float x2, float y2)
	{
		this.reset();
		this.addRect(x1, y1, x2, y2, Path.Direction.CW);
	}
	
	public void setLine(float x1, float y1, float x2, float y2)
	{
		this.reset();
		this.moveTo(x1,y1);
		this.lineTo(x2,y2);
	}
}
