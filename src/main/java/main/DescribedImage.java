package main;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.ddogleg.struct.FastQueue;

import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.alg.color.ColorRgb;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.feature.TupleDesc_B;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.Planar;
import ch.qos.logback.classic.spi.TurboFilterList;
import georegression.struct.point.Point2D_F64;

public class DescribedImage {
	Planar<GrayF32> colorImage;
	GrayF32 grayImage;
	FastQueue<TupleDesc> description;
	List<Point2D_F64> locationsOfFeaturePoints;
	DetectDescribePoint detectDescriptor;
	
	
	public DescribedImage(Planar<GrayF32> image,  DetectDescribePoint detectDescriptor) {
		this.colorImage = image;
		this.grayImage = new GrayF32(image.width, image.height);
		ColorRgb.rgbToGray_Weighted_F32(colorImage, grayImage);
		this.description = UtilFeature.createQueue(detectDescriptor, 1500);
		this.locationsOfFeaturePoints = new ArrayList<Point2D_F64>();
		this.detectDescriptor = detectDescriptor;
	}

	/**
	 * describes image in this instance using detect descriptor
	 */
	public void describe() {
		detectDescriptor.detect(grayImage);
		description.reset();
		locationsOfFeaturePoints.clear();
		System.out.println(detectDescriptor.getNumberOfFeatures());
		for (int i = 0; i < detectDescriptor.getNumberOfFeatures(); i++) {
			locationsOfFeaturePoints.add(detectDescriptor.getLocation(i).copy());
			description.grow().setTo(detectDescriptor.getDescription(i).copy());
		}
	}
}