package util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.Planar;
import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import main.Stitcher;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;


public class Tester {
	private Stitcher stitcher;
	private String datasetFolder;
	private NormalizedLevenshtein levenstien;
	private ITesseract tesseract;
	private long lengthOfLastStitching;
	/**
	 * 
	 * @param datasetFolder
	 *            name of dataset folder. This folder can have multiple folders,
	 *            however - only two types of folders are allowed - folders
	 *            which contains only folders, and folders which contains only
	 *            images and text document(containing grand truth string)
	 */
	public Tester(Stitcher testingStitcher) {
		this.stitcher = testingStitcher;
		levenstien = new NormalizedLevenshtein();
		tesseract = new Tesseract();
	}
	
    /**
     * stitches all pictures in folder except those named grandTruth.jpg and stitched.jpg 
     * stitched picture will be saved in stitched.jpg in this folder
     * @param folderWithPictures pictures conataining pictures of text files
     * @return stitched image
     */
	public BufferedImage stitchPicturesInFolder(File folderWithPictures) {
		List<Planar<GrayF32>> obrazky = new ArrayList<>();
		for (File obrazok : folderWithPictures.listFiles()) {
			if (!(obrazok.getName().equals("stitched.jpg") || obrazok.getName().equals("stitched.jpg.txt")
				|| obrazok.getName().equals("grandTruth.jpg.txt"))) {
				obrazky.add(ConvertBufferedImage.convertFromMulti(UtilImageIO.loadImage(obrazok.getPath())
						,null,true,GrayF32.class));
			}
		}
		System.gc();
		System.out.println("chystam sa spojit " + obrazky.size() + " obrazkov");
		long casPredSpajanim = System.currentTimeMillis();
		Planar<GrayF32> stitchedOutput = stitcher.stitch(obrazky);
		BufferedImage output = new BufferedImage(stitchedOutput.width, stitchedOutput.height,BufferedImage.TYPE_INT_RGB);
		ConvertBufferedImage.convertTo(stitchedOutput, output, true);
		lengthOfLastStitching = System.currentTimeMillis()-casPredSpajanim;
		UtilImageIO.saveImage(output, folderWithPictures.getPath() + "/stitched.jpg");
		return output;

	}
	/**
	 * run tests within a specified folder (in constructor)
	 */
	public void runTests() {
		File helpFile = new File(datasetFolder);
		long stitchingTime = 0;
		List<Double> results = new ArrayList<>();
		Stack<File> files = new Stack<>();
		files.add(helpFile);
		while (!files.isEmpty()) {
			helpFile = files.pop();
			if (helpFile.listFiles().length != 0 && helpFile.listFiles()[0].isFile()) {
				System.out.println("spajam obrazky v priecinku " + helpFile.getName());
				stitchPicturesInFolder(helpFile);
				System.out.println("obrazky v priecinku boli pospajane");
				System.out.println("idem porovnat grandTruth so spojenym obrazkom");
				//double quality = compareStitchedWithGrandTruth(helpFile);
				//results.add(quality);
				//System.out.println("spajanie je na "+quality*100+"% presné");
				System.out.println("spajanie trvalo "+lengthOfLastStitching+" miliskund");
				stitchingTime+= lengthOfLastStitching;
				System.out.println();
			} else {
				for (File subFolder : helpFile.listFiles()) {
					files.add(subFolder);
				}
			}
		}
		showTestResults(results, stitchingTime);
		
	}
	private void showTestResults(List<Double> resultsOfQuality, long stitchingTime) {
		int stitchedPicturesCount = resultsOfQuality.size();
		double average = 0;
		for(double result : resultsOfQuality) {
			average +=result;
		}
		average = average*100 / stitchedPicturesCount;
		double deviation = 0;
		for(double result : resultsOfQuality) {
			deviation +=Math.pow((result*100)-average, 2)/stitchedPicturesCount;
		}
		deviation = Math.sqrt(deviation);
		NumberFormat formatter = new DecimalFormat("#0.00");  
		System.out.println("spájanie celého datasetu je na " + formatter.format(average) + 
				"% +- "+ formatter.format(deviation)+" presné");
		System.out.println("spájanie priemerne trvalo "+stitchingTime/stitchedPicturesCount);
		
	}

	/**
	 * compares text from grantruth picture and stitched picture
	 * @param helpFile file in which is stitched and grantruth picture
	 * @return number from 0 (text from images are completely differetnt) to 1 (text is same) 
	 */
	private double compareStitchedWithGrandTruth(File helpFile) {

		String stitchedString = doOcr(new File(helpFile.getPath() + "/stitched.jpg"));
		String grandTruthString="";
		File suborSGrandTrurhTextom = new File(helpFile.getPath() + "/grandTruth.jpg.txt");
		
		if(suborSGrandTrurhTextom.exists()) {
			try {
				byte[] znaky = Files.readAllBytes(Paths.get(helpFile.getPath() + "/grandTruth.jpg.txt"));
				grandTruthString = new String(znaky);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			grandTruthString = doOcr(new File(helpFile.getPath() + "/grandTruth.jpg"));
		}
		
		return levenstien.similarity(stitchedString, grandTruthString);
	}
	/**
	 * read text from image
	 * @param stitchedPicture file with some text
	 * @return string from OCR of picture
	 */
	private String doOcr(File stitchedPicture) {
		try {
			String text = tesseract.doOCR(stitchedPicture);
			Files.write(Paths.get(stitchedPicture.getPath()+".txt"), text.getBytes());
			return text;
		} catch (TesseractException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}
	public void setDatasetFolder(String folderName) {
		this.datasetFolder  = folderName;
	}

}
