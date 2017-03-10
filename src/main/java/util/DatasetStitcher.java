package util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import boofcv.io.image.UtilImageIO;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.image.GrayF32;
import main.MultipleStitcher;

public class DatasetStitcher {
	private static Logger logger = LogManager.getLogger();
	MultipleStitcher<GrayF32, TupleDesc> stitcher;
	public DatasetStitcher() {
		stitcher = new MultipleStitcher<>();
	}
	public void stitchDataset(String nameOfFolder) {
		logger.entry(nameOfFolder);
		File helpFile = new File(nameOfFolder);
		Stack<File> files = new Stack<>();
		files.add(helpFile);
		while(!files.isEmpty()) {
			helpFile = files.pop();
			if(helpFile.listFiles().length!=0 && helpFile.listFiles()[0].isFile()) {
				List<BufferedImage> obrazky = new ArrayList<>();
				for(File obrazok : helpFile.listFiles()) {
					if(!obrazok.getName().equals("stitched.jpg")){
						obrazky.add(UtilImageIO.loadImage(obrazok.getPath()));
					}
					
				}
				UtilImageIO.saveImage(stitcher.stitch(obrazky, GrayF32.class),helpFile.getPath()+"/stitched.jpg");
				//System.out.println(helpFile.getPath());
				continue;
			}
			for(File subFolder : helpFile.listFiles()) {
				files.add(subFolder);
			}
		}
		logger.traceExit();
	}
}
