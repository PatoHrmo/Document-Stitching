package main;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.ddogleg.struct.FastQueue;

import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.Planar;
import georegression.struct.point.Point2D_F64;

public class DescribedImage {
	Planar<GrayF32> colorImage;
	GrayF32 grayImage;
	FastQueue<BrightFeature> description;
	List<Point2D_F64> locationsOfFeaturePoints;
	DetectDescribePoint<GrayF32, BrightFeature> detectDescriptor;
	
	public DescribedImage(BufferedImage image,  DetectDescribePoint<GrayF32, BrightFeature> detectDescriptor) {
		this.grayImage = ConvertBufferedImage.convertFromSingle(image, null, GrayF32.class);
		this.description = UtilFeature.createQueue(detectDescriptor, 1500);
		this.locationsOfFeaturePoints = new ArrayList<Point2D_F64>();
		this.detectDescriptor = detectDescriptor;
		this.colorImage = ConvertBufferedImage.convertFromMulti(image, null, true, GrayF32.class);
	}

	/**
	 * describes image in this instance using detect descriptor
	 */
	public void describe() {
		detectDescriptor.detect(grayImage);
		description.reset();
		locationsOfFeaturePoints.clear();
		for (int i = 0; i < detectDescriptor.getNumberOfFeatures(); i++) {
			locationsOfFeaturePoints.add(detectDescriptor.getLocation(i).copy());
			description.grow().setTo(detectDescriptor.getDescription(i).copy());
		}
	}
}