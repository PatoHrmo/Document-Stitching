package util;

import java.util.Random;

import main.Stitcher;
import main.StitcherConfigurationFactory;

public class test {
	public static void main(String[] args) {
		double sancaNaGol = 0.88;
		
		double financie;
		double priemFinancie = 0;
		Random rnd = new Random();
		Random rnd2 = new Random();
		int pocetVMinuse = 0;
		double pocetnostVyher[] = new double[48];
		
		
		double sancaNaVyhru  = Math.pow(sancaNaGol, 3);
		for(int i = 0; i<10000000; i++) {
			financie = 0;
			for(int j = 0; j<7;j++ ) {
				if(rnd.nextDouble()<sancaNaVyhru) {
					double kurz = 1.55;
					kurz = kurz -0.05 +(rnd2.nextDouble()/10);
					financie+=20*kurz -20;
				} else {
					financie-=20;
				}
			}
			financie+=140;
			pocetnostVyher[(int)Math.floor(financie/5)]+=1.0/10000;
			
		}
		for(int i = 0; i< pocetnostVyher.length;i++) {
			System.out.println(pocetnostVyher[i]);
		}
		System.out.println(priemFinancie/pocetVMinuse+ " sanca na minus je "+pocetVMinuse/10000000.0);
//		Stitcher stitcher = new Stitcher(StitcherConfigurationFactory.surfGreedyBright());
//		Tester tester = new Tester(stitcher);
//		tester.setDatasetFolder("./dataset/new upravene");
//		tester.runTests();
	}

}
