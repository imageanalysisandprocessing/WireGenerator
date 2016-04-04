import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.ini4j.Ini;

import ij.IJ;
import ij.Prefs;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Plot;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import fiji.util.gui.GenericDialogPlus;
import util.FindConnectedRegions;
import util.FindConnectedRegions.*;

/*
 * 
 * Copyright 2015 Jonas Schatz, Matthias Büchele, Ralf Keding
 * 
 * This file is part of the ImageJ plugin "Generate Wires". 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 */

public class Wire_Generator_ implements PlugIn{

	@Override
	public void run(String arg) {
		System.err.println("Welcome to the Wire Generator");
		//Some Variables to store the probability lists
		ArrayList<String> length_prop_list=null;
		ArrayList<String> width_prop_list=null;
		ArrayList<String> alpha_prop_list=null;
		
		// The most important object in this code!
		// The Ini holds every setting Information either from 
		// user input or the config file
		Ini myini;
		
		// Initialize config File.
		// Path and filename are hardcoded.
		// --> If no configfile is available, a standard one is created at 
		// "username/.imagej/create_and_evaluate_synthetic_wires_config.ini"
		// This happens in getoptions(), when file is not found
		String config_path = System.getProperty("user.home")+ File.separator+".imagej" + File.separator+ "create_and_evaluate_synthetic_wires_config.ini";
		System.out.println("Looking for configfile: " + config_path);
		
		// Get values from dialog:
		myini = getOptions(config_path);
		// null is returned if user cancellled the dialog. Then we quit the PlugIn...
		if (myini==null) return;
		
		// Get filepaths to probability-by-lists
		if (myini.get("Parameters","bool_length_by_list",boolean.class)){
			length_prop_list = ImportProbabilityList(myini.get("Parameters","length_prop_list",String.class));
		}
		if (myini.get("Parameters","bool_width_by_list",boolean.class)){
			width_prop_list = ImportProbabilityList(myini.get("Parameters","width_prop_list",String.class));
		}
		if (myini.get("Parameters","bool_alpha_by_list",boolean.class)){
			alpha_prop_list = ImportProbabilityList(myini.get("Parameters","alpha_prop_list",String.class));
		}
		
		// Create Pictures
		ImagePlus wires_imp = CreatePicturtes(myini, length_prop_list, width_prop_list, alpha_prop_list);
		wires_imp.show();
		
		// Perform Skeletonize if desired
		if (myini.get("Parameters","perform_skeletonize",boolean.class)){
			System.out.println("starting Skeletonize");
			ImagePlus skel_imp = PerformSkeletonize(wires_imp, myini);
			skel_imp.show();
			System.out.println("Skeletonize done");
			}
		
		// Perform FCR if desired
		if (myini.get("Parameters","perform_fcr",boolean.class)){
			System.out.println("starting FCR");
			ImagePlus fcr_imp = PerformFCR(wires_imp, myini);
			fcr_imp.show();
			System.out.println("FCR done");
			}
		
		
		System.out.println("finished all");	
	}


	private static Ini create_config_file(String config_path){
		
		// This Method is only called if LoadConfigFile() fails
		// It creates the standard configfile with hardcoded standard values
		// and calls LoadConfigFile() again.
		FileWriter writer;
		try
		{
			writer = new FileWriter(config_path);
			writer.write("[Parameters]\n");
			writer.write("min_length = 500\n");
			writer.write("max_length = 1000\n");
			writer.write("bool_length_by_list = False\n");
			writer.write("min_width = 10\n");
			writer.write("max_width = 20\n");
			writer.write("bool_width_by_list = False\n");
			writer.write("image_size = 4096\n");
			writer.write("n_start = 100\n");
			writer.write("n_end = 100\n");
			writer.write("n_step = 10\n");
			writer.write("allow_outside = False\n");
			writer.write("perform_fcr = False\n");
			writer.write("save_fcr_images = False\n");
			writer.write("perform_skeletonize = False\n");
			writer.write("save_skeletonized_images = False\n");
			String path = "destinationpath = "+ System.getProperty("user.home")+ File.separator+"Pictures" + File.separator + "\n";
			writer.write(path);
			path = "length_prop_list = "+ System.getProperty("user.home")+ File.separator+"Pictures" + File.separator + "length_prop_list.txt"+"\n";
			writer.write(path);
			path = "width_prop_list = "+ System.getProperty("user.home")+ File.separator+"Pictures" + File.separator + "width_prop_list.txt"+"\n";
			writer.write(path);
			path = "alpha_prop_list = "+ System.getProperty("user.home")+ File.separator+"Pictures" + File.separator + "alpha_prop_list.txt"+"\n";
			writer.write(path);
			writer.write("max_opening_angle = 90\n");	
			writer.write("bool_alpha_by_list = False\n");
			writer.close();
		}
		catch (IOException e) 
		{
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
		return LoadConfigFile(config_path);

	}

	private static Ini LoadConfigFile(String config_path) {
		
		// Ini Object is created by loading the configfile
		Ini ini = new Ini();
		try{
			ini.load(new FileReader(config_path));
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.err.println("There was no config File!");
			ini = create_config_file(config_path);
			System.err.println("Created new Standard one...");
			
		}
		return ini;
	}

	private static void StoreConfigFile(Ini ini, String path) {
		
		// Configfile is overwritten with values from the Ini Object
		File output = new File(path);
		ini.setFile(output);
		try {
			ini.store();
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
		
	}

	private static Ini getOptions(String config_path){

		// get Settings from the configfile
		Ini ini = LoadConfigFile(config_path);
		
		// Create UI
		GenericDialogPlus gd = new GenericDialogPlus("Options");
	
		gd.addMessage("Length:");	
		gd.addNumericField("Minimum wire length", ini.get("Parameters","min_length",double.class), 0);
		gd.addNumericField("Maximum wire length", ini.get("Parameters","max_length",double.class), 0);
		gd.addCheckbox("Specified length distribution in file", ini.get("Parameters","bool_length_by_list",boolean.class));
		gd.addFileField("If yes, path to file", ini.get("Parameters","length_prop_list",String.class));
		
		gd.addMessage("Width:");
		gd.addNumericField("Minimum wire width", ini.get("Parameters","min_width",double.class), 0);
		gd.addNumericField("Maximum wire width", ini.get("Parameters","max_width",double.class), 0);
		gd.addCheckbox("Specified width distribution in file", ini.get("Parameters","bool_width_by_list",boolean.class));
		gd.addFileField("If yes, path to file", ini.get("Parameters","width_prop_list",String.class));
		
		gd.addMessage("Bending:");
		gd.addNumericField("Max opening angle [DEG]", ini.get("Parameters","max_opening_angle",double.class), 0);
		gd.addMessage("(0=no bending; 180=up to half circles; 360= up to circles)");
		gd.addCheckbox("Specified angle distribution in file", ini.get("Parameters","bool_alpha_by_list",boolean.class));
		gd.addFileField("If yes, path to file", ini.get("Parameters","alpha_prop_list",String.class));
		
		gd.addMessage("Image Properties:");
		gd.addNumericField("Image size", ini.get("Parameters","image_size",double.class), 0);
		gd.addNumericField("Image sequence: Min. number of wires", ini.get("Parameters","n_start",double.class), 0);
		gd.addNumericField("Image sequence: Max. number of wires", ini.get("Parameters","n_end",double.class), 0);
		gd.addNumericField("Image sequence: Stepsize", ini.get("Parameters","n_step",double.class), 0);
		
		gd.addDirectoryField("Path for Output", ini.get("Parameters","destinationpath",String.class));
		
		gd.addCheckbox("Allow wires intersecting with the border", ini.get("Parameters","allow_outside",boolean.class));
		gd.addCheckbox("Perform 'Find Connected Regions'", ini.get("Parameters","perform_fcr",boolean.class));
		gd.addCheckbox("Save FCR - images", ini.get("Parameters","save_fcr_images",boolean.class));
		gd.addCheckbox("Perform 'Skeletonize'", ini.get("Parameters","perform_skeletonize",boolean.class));
		gd.addCheckbox("Save skeletonized images", ini.get("Parameters","save_skeletonized_images",boolean.class));
		
		gd.showDialog();
		
		if (gd.wasCanceled()){
			System.out.println("User canceled dialog!");
			//IJ.error("Canceled","User canceled dialog!");
			// Don't save anything, just quit...
			return null;
		}
			
		
		//Read out the options from UI and store them in the Ini
		ini.put("Parameters","min_length", gd.getNextNumber());
		ini.put("Parameters","max_length", gd.getNextNumber());
		ini.put("Parameters","bool_length_by_list", gd.getNextBoolean());
		ini.put("Parameters","length_prop_list", gd.getNextString());
		
		ini.put("Parameters","min_width", gd.getNextNumber());
		ini.put("Parameters","max_width", gd.getNextNumber());
		ini.put("Parameters","bool_width_by_list", gd.getNextBoolean());
		ini.put("Parameters","width_prop_list", gd.getNextString());
		
		ini.put("Parameters","max_opening_angle", gd.getNextNumber());
		ini.put("Parameters","bool_alpha_by_list", gd.getNextBoolean());
		ini.put("Parameters","alpha_prop_list", gd.getNextString());
		
		ini.put("Parameters","image_size", gd.getNextNumber());
		ini.put("Parameters","n_start", gd.getNextNumber());
		ini.put("Parameters","n_end", gd.getNextNumber());
		ini.put("Parameters","n_step", gd.getNextNumber());
		
		// make sure there is an / or \ at the end of the directory path
		String path = gd.getNextString();
		if (!path.endsWith(File.separator)){path += File.separator;}
		ini.put("Parameters","destinationpath", path);
		
		ini.put("Parameters","allow_outside", gd.getNextBoolean());
		ini.put("Parameters","perform_fcr", gd.getNextBoolean());
		ini.put("Parameters","save_fcr_images", gd.getNextBoolean());
		ini.put("Parameters","perform_skeletonize", gd.getNextBoolean());
		ini.put("Parameters","save_skeletonized_images", gd.getNextBoolean());

		//Write changed values back into configfile:
		StoreConfigFile(ini, config_path);		
		return ini;
	}
	
	private static ArrayList<String> ImportProbabilityList(String path){
		
		ArrayList<String> text = new ArrayList<String>();
		try {
			FileReader fr = new FileReader(path);
			BufferedReader r = new BufferedReader(fr);
		String zeile = r.readLine();
		while (zeile!=null){
			text.add(zeile);
			zeile = r.readLine();
		}
		r.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
				
		return text;
		
	}

	private static String CreateFileName(int n, Ini p){
		
		// convenient method, because filenames are used over ond over again
		int min_l = p.get("Parameters","min_length",double.class).intValue();
		int max_l = p.get("Parameters","max_length",double.class).intValue();
		int min_w = p.get("Parameters","min_width",double.class).intValue();
		int max_w = p.get("Parameters","max_width",double.class).intValue();
		int size = p.get("Parameters","image_size",double.class).intValue();
		return ("wires_n-"+n+"_length-"+min_l+"-"+max_l+"_width-"+min_w+"-"+max_w+"_size-"+size);
	}

	private static void WriteToFile(String path_String, String text){
		
		// simply writes one line of text in a file at the given path
		// file is created if it doesn't exists
		FileWriter writer;
		File f = new File(path_String);
		try
		{
			// second argument in FileWriter is boolean=append.
			// And thats true if File already exists...
			writer = new FileWriter(path_String,f.exists());
			writer.write(text + '\n');
			writer.close();
					
		}
		catch (IOException e) 
		{
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
	}
		
	private static void WriteToFile(String path_String, Wire draht) {
		
		// writes the properties of a wire Object to the file
		// using the wires Method toString()
		// file is created if it doesn't exists
		FileWriter writer;
		File f = new File(path_String);
		try
		{
			// second argument in FileWriter is boolean=append.
			// And thats true if File already exists...
			writer = new FileWriter(path_String,f.exists());
			writer.write(draht.toString() + '\n');
			writer.close();
					
		}
		catch (IOException e) 
		{
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
		
	}

	private static ByteProcessor Draw(Wire draht, ByteProcessor picture){
		
		picture.setLineWidth((int) draht.getWidth());
		
		// Wires are always white
		picture.setColor(255);
		//picture.setColor((int)(Math.random()*255 + 1));

		if(!draht.isBent()){
			// non-bent Wires can be easily drawn with a straight line
			picture.drawLine(draht.getX1(), draht.getY1(), draht.getX2(), draht.getY2());
		}
		else{
			double alpha = draht.getAlpha();
			double phi = draht.getPhi();
			int r = (int)(draht.getR());
			int mx = draht.getmx();
			int my = draht.getmy();

			// bent wires are drawn using polar coordinates
			int numberOfSteps = (int)(draht.getLength()/draht.getWidth());
			double increment = alpha/numberOfSteps;
			for(double a = phi+increment; a <  alpha+phi; a=a+increment){
				double a0 = (a - increment);
				int x0 = mx - (int)(r*Math.cos(a0));
				int y0 = my + (int)(r*Math.sin(a0));
				int x1 = mx - (int)(r*Math.cos(a));
				int y1 = my + (int)(r*Math.sin(a));	
				picture.drawLine(x0, y0, x1, y1);					
			}
			//make sure we draw till the very end!
			double a = alpha+phi;
			double a0 = alpha+phi - increment;
			int x0 = mx - (int)(r*Math.cos(a0));
			int y0 = my + (int)(r*Math.sin(a0));
			int x1 = mx - (int)(r*Math.cos(a));
			int y1 = my + (int)(r*Math.sin(a));	
			picture.drawLine(x0, y0, x1, y1);
			
		}
		return picture;
	}
	
	private ImagePlus PerformSkeletonize(final ImagePlus stack, final Ini ini) {

		// the AtomicInteger ensures that the different threads don't read the same counter variable at the same time
		// or one thread increases variable during another thread reads it etc...
		final AtomicInteger ai = new AtomicInteger(1);
		final int stacksize = stack.getStackSize();
		// store all result images here  
		final ImageProcessor[] results = new ImageProcessor[stacksize];  

		final Thread[] threads = newThreadArray();  

		for (int ithread = 0; ithread < threads.length; ithread++) {  

			// Concurrently run in as many threads as CPUs  
			threads[ithread] = new Thread() {  

				{ setPriority(Thread.NORM_PRIORITY); }  

				@Override
				public void run() {  

					// Each thread processes a few items in the total stack 
					// Each loop iteration within the run method  
					// has a unique 'i' number to work with  
					// and to use as index in the results array:  

					for (int i = ai.getAndIncrement(); i <= stacksize; i = ai.getAndIncrement()) {  
						//get Image out of stack 
						ImageProcessor ip = stack.getStack().getProcessor(i).duplicate();  

						//Print what you're doing
						System.err.println("I'm Thread-"+ Thread.currentThread().getId()+ " and I'm doing Skeletonize on Element number " + i);

						ImagePlus imp = new ImagePlus("Skeletonized " + i, ip);  
						// Run the plugin on the new image:  
						IJ.run(imp, "Skeletonize (2D/3D)", "");

						// Save Image if desired
						if (ini.get("Parameters","save_skeletonized_images",boolean.class))
						{
							FileSaver fs = new FileSaver(imp);
							int start =ini.get("Parameters","n_start",double.class).intValue();
							int step = ini.get("Parameters","n_step",double.class).intValue();
							int number = start + (i-1)*step;
							//fs.saveAsTiff(ini.get("Parameters","destinationpath",String.class)+CreateFileName(number, ini)+"_skeletonized.tif");
							fs.saveAsZip(ini.get("Parameters","destinationpath",String.class)+CreateFileName(number, ini)+"_skeletonized.tif");
						}

						// Save Image in results array
						results[i-1] = imp.getProcessor();  
						imp.flush();  
					}  
				}
			};  
		}  

		startAndJoin(threads);  

		// now the results array is full. Just show them in a stack:  
		final ImageStack stack_new = new ImageStack(stack.getProcessor().getHeight(), stack.getProcessor().getHeight());  
		for (int i=0; i< results.length; i++) {  
			stack_new.addSlice("Skeletonized " + i, results[i]);  
		}  

		ImagePlus imp = new ImagePlus("Skeletonize Results", stack_new);  
		return imp;
	}
	
	
	private ImagePlus PerformFCR(final ImagePlus stack, final Ini ini) {
		
		// the AtomicInteger ensures that the different threads don't read the same counter variable at the same time
		// or one thread increases variable during another thread reads it etc...
		final AtomicInteger ai = new AtomicInteger(1);
		final int stacksize = stack.getStackSize();
		final double[][] cov_perc = new double[2][stacksize];
		
		// store all result images here  
		final ImageProcessor[] results = new ImageProcessor[stacksize];  

		final Thread[] threads = newThreadArray();  
		
		for (int ithread = 0; ithread < threads.length; ithread++) {  
		// for (int ithread = 0; ithread < 3; ithread++) {
			// Concurrently run in as many threads as CPUs 
		
			threads[ithread] = new Thread() {  

				{ setPriority(Thread.NORM_PRIORITY); }  

				@Override
				public void run() {  

					// Each thread processes a few items in the total stack 
					// Each loop iteration within the run method  
					// has a unique 'i' number to work with  
					// and to use as index in the results array:  

					for (int i = ai.getAndIncrement(); i <= stacksize; i = ai.getAndIncrement()) {  
						//get Image out of stack 
						ImageProcessor ip = stack.getStack().getProcessor(i).duplicate();  
												
						//Print what you're doing
						System.err.println("I'm Thread-"+ Thread.currentThread().getId()+ " and I'm doing FCR on Element number " + i);

						ImagePlus imp = new ImagePlus("FCR " + i, ip);  
						// Run the plugin on the new image:
						long time = System.currentTimeMillis();
						FindConnectedRegions fcr= new FindConnectedRegions();
						FindConnectedRegions.Results fcrresults = fcr.run(imp, true, true, true, true, true, false, false, 100, 1, -1, true);
						System.out.println((System.currentTimeMillis()-time)/1000.0 + " Sekunden");
						// Assign different Results to Variables
						ImagePlus allRegionsImp = fcrresults.allRegions;
						List<Region> infoList = fcrresults.regionInfo;
						
						int number_of_CRs = infoList.size();
						int area_max = 0;
						int area_sum = 0;
						for (int n = 0; n< infoList.size(); n++){
							area_max = Math.max(area_max, infoList.get(n).getNumberOfPoints());
							area_sum += infoList.get(n).getNumberOfPoints();
						}
						// Percolation:
						cov_perc[1][i-1]= area_max/(double)area_sum;
						
						// Coverage:
						cov_perc[0][i-1] = area_sum /(double)(allRegionsImp.getProcessor().getHeight()*allRegionsImp.getProcessor().getWidth());
						
						// Round em on 3 digits
						cov_perc[1][i-1]= ((int)((cov_perc[1][i-1]+0.0005)*1000))/1000.0;
						cov_perc[0][i-1] = ((int)((cov_perc[0][i-1]+0.0005)*1000))/1000.0;
						
						//Save FCR Results in file
						final int start = ini.get("Parameters","n_start",double.class).intValue();
						final int step = ini.get("Parameters","n_step",double.class).intValue();
						int number = start + (i-1)*step;
						String filename = CreateFileName(number, ini);
						String filename_picture = ini.get("Parameters","destinationpath",String.class) + filename +"_FCR.tif";
						String filename_results = ini.get("Parameters","destinationpath",String.class) + "00-FCR_results.txt";
						
						File f = new File(filename_results);
						if (!f.exists()){
							// If we're the first thread to finish FCR we write header + values
							WriteToFile(filename_results,"#wires" + '\t'+ "#CR" + '\t'+ "perc."+ '\t'+ "cov.");
							WriteToFile(filename_results, "" + number + '\t'+ number_of_CRs + '\t'+ cov_perc[1][i-1]+ '\t'+ cov_perc[0][i-1]);
						}
						else{// we just write the values
							WriteToFile(filename_results, "" + number + '\t'+ number_of_CRs + '\t'+ cov_perc[1][i-1]+ '\t'+ cov_perc[0][i-1]);
						}
						
						// Tell User the one Number he's interested in! 
						System.out.println("Image with "+number +" wires has "+ number_of_CRs + " regions");

						// Save Image if desired
						if (ini.get("Parameters","save_fcr_images",boolean.class))
						{
							FileSaver fs = new FileSaver(allRegionsImp);
							//fs.saveAsTiff(filename_picture);
							fs.saveAsZip(filename_picture);
						}

						// Save Image in results array
						results[i-1] = allRegionsImp.getProcessor();
						allRegionsImp.flush();
						imp.flush();  
					}  
				}
			};  
		}  

		startAndJoin(threads);  

		// now the results array is full. Just show them in a stack:  
		final ImageStack stack_new = new ImageStack(stack.getProcessor().getHeight(), stack.getProcessor().getHeight());  
		for (int i=0; i< results.length; i++) {  
			stack_new.addSlice("FCR " + i, results[i]);  
		}  

		Plot p = new Plot("Coverage vs Percolation", "Coverage", "Percolation",cov_perc[0], cov_perc[1]);
//		Plot p = new Plot("Coverage vs Percolation", "Coverage", "Percolation");
//		p.addPoints( cov_perc[0], cov_perc[1], 1);
		p.show();
		ImagePlus imp = new ImagePlus("FCR Results", stack_new);  
		return imp;
	}
	
	
	private ImagePlus CreatePicturtes(final Ini ini, final ArrayList<String> length_prop_list, final ArrayList<String> width_prop_list, final ArrayList<String> alpha_prop_list){

		// get necessary values from the Ini
		final int start = ini.get("Parameters","n_start",double.class).intValue();
		final int end =  ini.get("Parameters","n_end",double.class).intValue();
		final int step = ini.get("Parameters","n_step",double.class).intValue();
		final int image_size = ini.get("Parameters","image_size",double.class).intValue();
		final String path = ini.get("Parameters","destinationpath",String.class);
		final int stacksize = (end-start)/step;
		final AtomicInteger ai = new AtomicInteger(0);
				
		// store all result images here  
		final ImageProcessor[] results = new ImageProcessor[stacksize+1];  
		
		final Thread[] threads = newThreadArray();  

		for (int ithread = 0; ithread < threads.length; ithread++) {  

			// Concurrently run in as many threads as CPUs  
			threads[ithread] = new Thread() {  

				{ setPriority(Thread.NORM_PRIORITY); }  

				@Override
				public void run() {  

					// Each thread processes a few items in the total stack 
					// Each loop iteration within the run method  
					// has a unique 'i' number to work with  
					// and to use as index in the results array:  

					for (int i = ai.getAndIncrement(); i <= stacksize; i = ai.getAndIncrement()) {
						
						int number = start + i*step;
						String filename = CreateFileName(number, ini);
						String filename_picture =  path + filename +".tif";
						String filename_results = path + filename +"_wire-information.txt";
						
						// write Fileheader
						WriteToFile(filename_results, "Length"+'\t'+"Width"+'\t'+"x1"+'\t'+"y1"+'\t'+"x2"+'\t'+"y2"+'\t'+"mx"+'\t'+"my"+'\t'+"r"+'\t'+"alpha"+'\t'+"phi");
						
						// Create new, black Picture
						ByteProcessor fp = new ByteProcessor(image_size,image_size);
						ImagePlus imp = new ImagePlus(filename,fp);
						
						//Print what you're doing
						System.err.println("I'm Thread-"+ Thread.currentThread().getId()+ ", and I'm creating a Picture with " + number + " wires");

						// Draw and save the Wires
						for (int j = 0; j < number; j++)
						{
							// Create a wire
							Wire draht = new Wire(ini, length_prop_list, width_prop_list, alpha_prop_list);
							// Write Wire Data to txt file
							WriteToFile(filename_results,draht);
							// Draw it
							fp = Draw(draht,fp);
						}
									
						// Save Image as tif
						FileSaver fs = new FileSaver(imp);
						//fs.saveAsTiff(filename_picture);
						fs.saveAsZip(filename_picture);
						System.out.println(filename+" -----> done.");

						// Save Image in results array
						results[i] = imp.getProcessor();  
						imp.flush();  
					}  
				}
			};  
		}  

		startAndJoin(threads);  

		// now the results array is full. Just show them in a stack:  
		final ImageStack stack_new = new ImageStack(image_size,image_size);  
		for (int i=0; i< results.length; i++) {
			int number = start + i*step;
			stack_new.addSlice(CreateFileName(number, ini), results[i]);  
		}  

		ImagePlus imp = new ImagePlus("Created Wires", stack_new);  
		return imp;
	}
	
	private Thread[] newThreadArray() {  
        // int n_cpus = Runtime.getRuntime().availableProcessors();
		// System.err.println(Prefs.getThreads());
		// here the number of treads from the menue preferences
		int n_cpus = Prefs.getThreads();
		return new Thread[n_cpus];  
	}
	
	 public static void startAndJoin(Thread[] threads)  
	    {  
	        for (int ithread = 0; ithread < threads.length; ++ithread)  
	        {  
	            threads[ithread].setPriority(Thread.NORM_PRIORITY);  
	            threads[ithread].start();  
	        }  
	  
	        try  
	        {     
	            for (int ithread = 0; ithread < threads.length; ++ithread)  
	                threads[ithread].join();  
	        } catch (InterruptedException ie)  
	        {  
	            throw new RuntimeException(ie);  
	        }  
	    }  
	}
