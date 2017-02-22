package main;

import java.awt.image.BufferedImage;
import java.util.LinkedList;
import java.util.List;

import boofcv.io.image.UtilImageIO;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.image.GrayF32;

public class HrubeSpojenieViacerichTEST {

	public static void main(String[] args) {
		BufferedImage image1 = UtilImageIO.loadImage("./multiple/a.jpg");
		BufferedImage image2 = UtilImageIO.loadImage("./multiple/c.jpg");
		BufferedImage image3 = UtilImageIO.loadImage("./multiple/b.jpg");
		List<BufferedImage> images = new LinkedList<>();
		images.add(image1);
		images.add(image2);
		images.add(image3);
		MultipleStitcher<GrayF32, TupleDesc> sti = new MultipleStitcher<>();
		BufferedImage stitchedImage1 = sti.stitch(images, GrayF32.class);
		UtilImageIO.saveImage(stitchedImage1, "./multiple/stitched.jpg");
	}

}
