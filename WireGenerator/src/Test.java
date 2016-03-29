import ij.ImageJ;

// Hallo
public class Test {

	public static void main(String[] args) {
		ImageJ.main( args );
		Wire_Generator_ wg = new Wire_Generator_();
		String arg = "";
		if (args.length>0){
			arg = args[0];
		}
		
		wg.run(arg);

	}

}
