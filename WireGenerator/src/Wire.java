import java.util.ArrayList;
import org.ini4j.Ini;

import ij.IJ;

/*
 * This Class represents the Wire Objects (bent and straight)
 * The Constructor needs the Ini Object and three Probability Lists (lists can be "null" if not needed)
 * 
 * 
 */

public class Wire {

	private double length;
	private double width;
	private double r;		// Radius
	private double alpha; 	// Opening Angle
	private double phi; 	// Orientation
	private int p1x;		// Point 1
	private int p1y;		
	private int p2x;		// Point 1
	private int p2y;
	private int mx;			// Midpoint
	private int my;
	private boolean isBent = true;

	public Wire(Ini ini, ArrayList<String> length_prop_list, ArrayList<String> width_prop_list, ArrayList<String> alpha_prop_list){

		int counter=0; // no more than 100 trys to place a Wire. Otherwise its too big for the picture.

		double min_width = ini.get("Parameters","min_width",double.class);
		double max_width = ini.get("Parameters","max_width",double.class);
		double min_length = ini.get("Parameters","min_length",double.class);
		double max_length = ini.get("Parameters","max_length",double.class);
		double max_alpha = ini.get("Parameters","max_opening_angle",double.class);
		int size = ini.get("Parameters","image_size",double.class).intValue();

		// Get random length, width and opening angle (with given prop. distribution from file, if desired)
		if (ini.get("Parameters","bool_length_by_list",boolean.class))
		{
			this.length = valueByList(length_prop_list);
		} else {
			this.length = min_length + Math.random()*(max_length- min_length);
		}

		if (ini.get("Parameters","bool_width_by_list",boolean.class))
		{
			this.width = valueByList(width_prop_list);
		} else {
			this.width = min_width + Math.random()*(max_width- min_width);
		}

		if (ini.get("Parameters","bool_alpha_by_list",boolean.class))
		{
			this.alpha = valueByList(alpha_prop_list);
		} else {
			this.alpha = Math.random()*max_alpha;
		}

		// convert alpha to rad
		this.alpha*=(Math.PI/180);

		// Try to place a wire in picture
		do{
			// Random Orientation
			this.phi = Math.random()*2*Math.PI;

			// P1 random in picture
			this.p1x = (int) (Math.random()*size);
			this.p1y = (int)(Math.random()*size);

			// if Midpoint difference of bent wire and straight line is less than 2pixel --> no need for bending
			if(alpha==0 || ((this.length/this.alpha)*(1-Math.cos(this.alpha/2)))<2){
				this.isBent = false;
			}

			if (this.isBent){
				// calculate r
				this.r = this.length/this.alpha;

				// go to midpoint
				this.mx = (int) (this.p1x + r*Math.cos(this.phi));
				this.my = (int) (this.p1y - r*Math.sin(this.phi));

				// now go from midpoint to P2

				this.p2x = (int) (this.mx - this.r*Math.cos(this.alpha+this.phi));
				this.p2y = (int) (this.my + this.r*Math.sin(this.alpha+this.phi));
			}
			else{// not bent:
				// P2 is straight line away from P1
				this.p2x = (int) (this.p1x + this.length*Math.cos(this.phi));
				this.p2y = (int) (this.p1y - this.length*Math.sin(this.phi));
			}


			counter++;
			if (counter>100){
				System.err.println("Wires too big for image size!\nChoose shorter wires or a bigger image.");
				IJ.error("Error during Wire placement", "Wires too big for image size!\nChoose shorter wires or a bigger image.");
				System.exit(1); //0 for normal shutdown - 1 for emergency!
				break;
			}
		}
		// Repeat as long as Wire isn't in Picture except user said it is allowed
		while (!this.IsInPicture(ini) && !ini.get("Parameters","allow_outside",boolean.class));

	}

	public double getLength(){
		return length;
	}

	public double getWidth(){
		return width;
	}

	public int getX1(){
		return p1x;
	}

	public int getX2(){
		return p2x;
	}

	public int getY1(){
		return p1y;
	}

	public int getY2(){
		return p2y;
	}

	public int getmx(){
		return mx;
	}

	public int getmy(){
		return my;
	}

	public double getAlpha(){
		return alpha;
	}

	public double getPhi(){
		return phi;
	}

	public double getR(){
		return r;
	}

	public boolean isBent(){
		return isBent;
	}


	private static double valueByList(ArrayList<String> liste){

		// This Method gets the String List containing the probability distribution
		// The file should look like: (without header)
		// value , prop
		// 5 , 0.3
		// 10 , 0.3
		// 20 , 0.4

		double[][] werte = new double[liste.size()][2];
		double werte_sum = 0;
		for (int i = 0; i < liste.size(); i++){
			String[] zeile = liste.get(i).split(",");
			werte[i][0] = Double.parseDouble(zeile[0]);
			werte[i][1] = Double.parseDouble(zeile[1]);
			werte_sum += werte[i][1];
		}
		//normalize probability values (no effect if already normalized)
		for (int i = 0; i < liste.size(); i++){
			werte[i][1] /= werte_sum;
		}
		double min_val = werte[0][0];
		double max_val = werte[werte.length-1][0];
		double value = 0;
		double probability = 0;

		do{
			// Random value in lists Range
			value = min_val + Math.random()*(max_val- min_val);

			// threshold is the first value thats bigger than value
			int threshhold = 0;
			for (int i = 0; i< werte.length; i++){
				if (value < werte[i][0]){
					threshhold = i;
					break;
				}
			}

			// probability is linear interpolated between the two sourrounding values
			double x1 = werte[threshhold-1][0];
			double x2 = werte[threshhold][0];
			double y1 = werte[threshhold-1][1];
			double y2 = werte[threshhold][1];

			probability = y1 + (value-x1)*((y2-y1) / (x2-x1));

		}
		while (probability < Math.random());
		return value;
	}

	private boolean IsInPicture(Ini ini){		

		double size = ini.get("Parameters","image_size",double.class);

		// For bent wire check first if quadrat around the midpoint is in picture
		// This avoids running through the hole Wire every time
		if (this.isBent){
			if (this.mx-this.r >0 && this.mx+this.r <size && this.my-this.r > 0 && this.my+this.r <size){
				return true;
			} else{
				// check every Section of the Wire if its in Picture
				// stop as soon one pixel is found
				int numberOfSteps = (int)(this.length/this.width);
				double increment = this.alpha/numberOfSteps;
				for(double a = this.phi+increment; a <  this.alpha+this.phi; a=a+increment){
					int x = this.mx - (int)(r*Math.cos(a));
					int y = this.my + (int)(r*Math.sin(a));
					if (x<0 || x>size || y<0 || y>size){
						return false;
					}
				}

				return true;
			}
		}
		else{
			// straight wires are checked easily
			if (this.p1x>0 && this.p1x<size && this.p1y>0 && this.p1y < size && this.p2x>0 && this.p2x<size && this.p2y>0 && this.p2y < size){
				return true;
			}else{return false;}
		}

	}

	@Override
	public String toString(){
		double a = alpha*180/Math.PI;
		double p = phi*180/Math.PI;
		a = ((int)((a+0.05)*10))/10.0;
		p = ((int)((p+0.05)*10))/10.0;
		// convenient way of Writing the wires atributes to console or File...
		return  (int)length +"\t"+(int)width+"\t"+p1x+"\t"+p1y+"\t"+p2x+"\t"+p2y+"\t"+mx+"\t"+my+"\t"+(int)r+"\t"+a+"\t"+p;
	}

}
