package util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class Comparator {
	
	private static String doOcr(File stitchedPicture) {
		ITesseract tesseractOcr;
		tesseractOcr = new Tesseract();
		try {
			String text = tesseractOcr.doOCR(stitchedPicture);
			
			return text;
		} catch (TesseractException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}
	public static void main(String[] args) {
		NormalizedLevenshtein levenstien;
		
		levenstien  = new NormalizedLevenshtein();
		
		String stitchedString = doOcr(new File("./test.jpg"));
		String grandTruthString="";
		File suborSGrandTrurhTextom = new File("./grandTruth.jpg.txt");
		
		if(suborSGrandTrurhTextom.exists()) {
			try {
				byte[] znaky = Files.readAllBytes(Paths.get("./grandTruth.jpg.txt"));
				grandTruthString = new String(znaky);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			System.out.println("toto by nemalo vypisat, nenaslo textak s grantruth textom");
			grandTruthString = doOcr(new File("./grandTruth.jpg"));
		}
		
		System.out.println(levenstien.similarity(stitchedString, grandTruthString));
		

	}

}
