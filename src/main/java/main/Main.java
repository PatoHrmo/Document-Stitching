package main;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import org.ddogleg.struct.LinkedList;

import boofcv.io.image.UtilImageIO;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.InterleavedF32;

public class Main {
	 
	public static void stitchImagesInDataset(String nameOfFolder) {
		MultipleStitcher<GrayF32, TupleDesc> sti = new MultipleStitcher<>();
		File[] foldersWithPictures = new File("./"+nameOfFolder).listFiles();
		String pathToStitchedDataset = "./"+nameOfFolder+" stitched";
		new File(pathToStitchedDataset).mkdirs();
		File[] imageFiles;
		BufferedImage stitchedImage;
		BufferedImage image1, image2;
		String pathToConcreteDataset;
		for(File file : foldersWithPictures) {
			pathToConcreteDataset = pathToStitchedDataset+"/"+file.getName();
			new File(pathToConcreteDataset).mkdirs();
			imageFiles = file.listFiles();
			image1 = UtilImageIO.loadImage(imageFiles[0].getPath());
			image2 = UtilImageIO.loadImage(imageFiles[1].getPath());
			List<BufferedImage> images = new java.util.LinkedList<>();
			images.add(image1);
			images.add(image2);
			stitchedImage = sti.stitch(images, GrayF32.class);
			UtilImageIO.saveImage(stitchedImage, pathToConcreteDataset+"/stitched.jpg");
			UtilImageIO.saveImage(image1, pathToConcreteDataset+"/1.jpg");
			UtilImageIO.saveImage(image2, pathToConcreteDataset+"/2.jpg");
		}
	}
 
	public static void main( String args[] ) {
		final String CESTA_PRIECINKA_S_DATASETOM = "./homografyTestDataset/homografie";
		stitchImagesInDataset(CESTA_PRIECINKA_S_DATASETOM);
		//BufferedImage imageA,imageB;
		//imageA = UtilImageIO.loadImage("./nove data/1.jpg");
		//imageB = UtilImageIO.loadImage("./nove data/2.jpg");
		//Stitcher.stitch(imageA,imageB, GrayF32.class);
	}
}