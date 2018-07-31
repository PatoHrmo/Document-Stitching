package util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.Planar;
import main.Stitcher;

public class Connector {
	private static boolean isPictureToConnect(File file) {
		if(file.isFile()) {
			String name = file.getName();
			name = name.substring(name.lastIndexOf(".")+1);
			if((name.equals("png") || name.equals("jpg") || name.equals("jpeg")) 
					&& !name.equals("stitched.jpg")) {
				return true;
			}
		}
		return false;
	}
	public static void main(String[] args) {
		Stitcher stitcher = new Stitcher();
		List<Planar<GrayF32>> pictures = new ArrayList<>();
		File folder = new File((Paths.get(".").toAbsolutePath().normalize().toString()));
		if(args.length==0) {
			for(File file : folder.listFiles()) {
				if(isPictureToConnect(file)) {
					pictures.add(ConvertBufferedImage.convertFromMulti(UtilImageIO.loadImage(file.getPath())
							,null,true,GrayF32.class));
				}
			}
		} else {
			
		}
		if(pictures.isEmpty()) return;
		Planar<GrayF32> stitchedOutput = stitcher.stitch(pictures);
		BufferedImage output = new BufferedImage(stitchedOutput.width, stitchedOutput.height,BufferedImage.TYPE_INT_RGB);
		ConvertBufferedImage.convertTo(stitchedOutput, output, true);
		UtilImageIO.saveImage(output, folder.getPath() + "/stitched.jpg");
	}

}
