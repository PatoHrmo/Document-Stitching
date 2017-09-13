package util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import boofcv.io.image.UtilImageIO;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.image.GrayF32;
import ch.qos.logback.core.net.SyslogOutputStream;
import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import main.MultipleStitcher;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class Tester {
	private MultipleStitcher<GrayF32, TupleDesc> stitcher;
	private String nameOfFolder;
	private NormalizedLevenshtein levenstien;
	private ITesseract tesseractOcr;
	private boolean saveTextFromOcr;
	private long casPoslednehoSpoju;
	/**
	 * 
	 * @param nameOfFolder
	 *            name of dataset folder. This folder can have multiple folders,
	 *            however - only two types of folders are allowed - folders
	 *            which contains only folders, and folders which contains only
	 *            images(one of this images need to be named grandTruth.jpg)
	 */
	public Tester(String nameOfFolder) {
		stitcher = new MultipleStitcher<>();
		this.nameOfFolder = nameOfFolder;
		levenstien = new NormalizedLevenshtein();
		tesseractOcr = new Tesseract();
		this.saveTextFromOcr = true;
		casPoslednehoSpoju = 0;

	}
    /**
     * stitches all pictures in folder except those named grandTruth.jpg and stitched.jpg 
     * stitched picture will be saved in stitched.jpg in this folder
     * @param folderWithPictures pictures conataining pictures of text files
     * @return stitched image
     */
	public BufferedImage stitchPicturesInFolder(File folderWithPictures) {
		List<BufferedImage> obrazky = new ArrayList<>();
		for (File obrazok : folderWithPictures.listFiles()) {
			if (!(obrazok.getName().equals("stitched.jpg") || obrazok.getName().equals("grandTruth.jpg") ||
					obrazok.getName().equals("stitched.jpg.txt") || obrazok.getName().equals("grandTruth.jpg.txt"))) {
				obrazky.add(UtilImageIO.loadImage(obrazok.getPath()));
			}
		}
		System.gc();
		System.out.println("chystam sa spojit " + obrazky.size() + " obrazkov");
		long casPredSpajanim = System.currentTimeMillis();
		BufferedImage stitched = stitcher.stitch(obrazky, 10);
		casPoslednehoSpoju = System.currentTimeMillis()-casPredSpajanim;
		UtilImageIO.saveImage(stitched, folderWithPictures.getPath() + "/stitched.jpg");
		return stitched;

	}
	/**
	 * run tests within a specified folder (in constructor)
	 */
	public void runTests() {
		File helpFile = new File(nameOfFolder);
		double qualitySum = 0;
		long celkomCas = 0;
		int stitchedPicturesCount = 0;
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
				//qualitySum += quality;
				//System.out.println("spajanie je na "+quality*100+"% presné");
				System.out.println("spajanie trvalo "+casPoslednehoSpoju+" miliskund");
				celkomCas+= casPoslednehoSpoju;
				System.out.println();
				stitchedPicturesCount++;
			} else {
				for (File subFolder : helpFile.listFiles()) {
					files.add(subFolder);
				}
			}

		}
		System.out.println("spájanie celého datasetu je na " + qualitySum * 100 / stitchedPicturesCount + "% presné");
		System.out.println("spájanie datasetu trvalo "+celkomCas);
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
				// TODO Auto-generated catch block
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
			String text = tesseractOcr.doOCR(stitchedPicture);
			if(saveTextFromOcr) {
				Files.write(Paths.get(stitchedPicture.getPath()+".txt"), text.getBytes());
			}
			return text;
		} catch (TesseractException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}

}
