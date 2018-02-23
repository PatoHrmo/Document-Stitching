package main;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.struct.FastQueue;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.ConfigCompleteSift;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.alg.color.ColorRgb;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.distort.impl.DistortSupport;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.core.image.border.BorderType;
import boofcv.factory.distort.FactoryDistort;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.dense.ConfigDenseHoG;
import boofcv.factory.feature.dense.FactoryDescribeImageDense;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.factory.feature.detect.interest.FactoryDetectPoint;
import boofcv.factory.geo.ConfigLMedS;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.transform.homography.HomographyPointOps_F64;
import net.coobird.thumbnailator.Thumbnails;

public class MultipleStitcher {
	
	private DetectDescribePoint<GrayF32, BrightFeature> detDesc;
	private ScoreAssociation<BrightFeature> scorer;
	private AssociateDescription<BrightFeature> associate;
	private ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher;
	
    public MultipleStitcher() {
    	detDesc = FactoryDetectDescribe
				.surfStable(new ConfigFastHessian(1, 2, 1500, 1, 9, 4, 4), null, null, GrayF32.class);
		scorer = FactoryAssociation.scoreEuclidean(BrightFeature.class, true);
		associate = FactoryAssociation.greedy(scorer, Double.MAX_VALUE, true);
    }
    
	public Planar<GrayF32> stitch(List<Planar<GrayF32>> images) {
		
		modelMatcher = FactoryMultiViewRobust.homographyRansac(null,
				new ConfigRansac(3000, 1));
		DescribedImage mainImage = new DescribedImage(images.remove(0), detDesc);
		List<DescribedImage> imagesToStitch = computeDescriptions(images, detDesc);
		
		int bestNumberOfMatches = 0;
		DescribedImage bestImageToStitch;
		
		FastQueue<AssociatedIndex> matches = new FastQueue<>(AssociatedIndex.class, true);
		int indexOfBestImageToStitch = 0;
		while (!imagesToStitch.isEmpty()) {
			mainImage.describe();
			bestNumberOfMatches = 0;
			for (int i = 0; i < imagesToStitch.size(); i++) {
				associate.setSource(mainImage.description);
				associate.setDestination(imagesToStitch.get(i).description);
				associate.associate();

				if (associate.getMatches().size > bestNumberOfMatches) {
					bestNumberOfMatches = associate.getMatches().size;
					indexOfBestImageToStitch = i;
					matches = cloneMatchesFromAssociate();
				}
			}
			

			bestImageToStitch = imagesToStitch.remove(indexOfBestImageToStitch);
			List<AssociatedPair> pairs = new ArrayList<>();

			for (int i = 0; i < matches.size(); i++) {
				AssociatedIndex match = matches.get(i);
				Point2D_F64 a = mainImage.locationsOfFeaturePoints.get(match.src);
				Point2D_F64 b = bestImageToStitch.locationsOfFeaturePoints.get(match.dst);

				pairs.add(new AssociatedPair(a, b, false));
			}
			
			matches.reset();
			// find the best fit model to describe the change between these
			// images
			if (!modelMatcher.process(pairs))
				throw new RuntimeException("Model Matcher failed!");
			Homography2D_F64 homografia = modelMatcher.getModelParameters().copy();
			
			connect(mainImage, bestImageToStitch, homografia);
			
		}
		
		return mainImage.colorImage;
	}

	private FastQueue<AssociatedIndex> cloneMatchesFromAssociate() {
		int numberOfMatches = associate.getMatches().getSize();
		FastQueue<AssociatedIndex> matches = new FastQueue<>(numberOfMatches, AssociatedIndex.class, true);
		matches.size = numberOfMatches;
		for (int i = 0; i < associate.getMatches().size; i++) {
			matches.get(i).set(associate.getMatches().get(i));
		}
		return matches;

	}

	private void connect(DescribedImage main, DescribedImage bestImageToConnect, Homography2D_F64 fromAtoB) {
		
		double scale = 1;
		
		Planar<GrayF32> colorA = main.colorImage;
		Planar<GrayF32> colorB = bestImageToConnect.colorImage;
		
		StitchedPictureSize sizeOfOutputColorImage = getSizeOfStitchedImage(main.colorImage, bestImageToConnect.colorImage, fromAtoB);
		// Where the output images are rendered into
		Planar<GrayF32> connectedColor = colorA.createNew(sizeOfOutputColorImage.getWidthOfOutputImage(),
				sizeOfOutputColorImage.getHeightOfOutputImage());
		// Adjust the transform so that the whole image can appear inside of it
		Homography2D_F64 fromAToWorkColor = new Homography2D_F64(scale, 0, sizeOfOutputColorImage.getShiftRight(),0, scale,
				sizeOfOutputColorImage.getShiftDown(), 0, 0, 1);
		Homography2D_F64 fromWorkToAColor = fromAToWorkColor.invert(null);
		// Used to render the results onto an image
		PixelTransformHomography_F32 modelColor = new PixelTransformHomography_F32();
		InterpolatePixelS<GrayF32> interp = FactoryInterpolation.bilinearPixelS(GrayF32.class, BorderType.ZERO);
		ImageDistort<Planar<GrayF32>, Planar<GrayF32>> distortColor = DistortSupport.createDistortPL(GrayF32.class, modelColor,
				interp, false);
		distortColor.setRenderAll(false);
		// Render first image
		modelColor.set(fromWorkToAColor);
		distortColor.apply(colorA, connectedColor);
		// Render second image
		Homography2D_F64 fromWorkToBColor = fromWorkToAColor.concat(fromAtoB, null);
		modelColor.set(fromWorkToBColor);
		distortColor.apply(colorB, connectedColor);
		main.colorImage = connectedColor;
		main.grayImage = new GrayF32(main.colorImage.width, main.colorImage.height);
		ColorRgb.rgbToGray_Weighted_F32(main.colorImage, main.grayImage);
	}

	private StitchedPictureSize getSizeOfStitchedImage(ImageBase image, ImageBase image2, Homography2D_F64 fromBtoA) {
		
		StitchedPictureSize pictureSize = new StitchedPictureSize();
		Homography2D_F64 fromAtoB = new Homography2D_F64();
		fromBtoA.invert(fromAtoB);

		Point2D_I32 corners[] = new Point2D_I32[] {
				// upper right corner of picture A
				new Point2D_I32(image.getWidth(), 0),
				// upper left corner of picture A
				new Point2D_I32(0, 0),
				// bottom right corner of picture A
				new Point2D_I32(image.getWidth(), image.getHeight()),
				// bottom left corner of picture A
				new Point2D_I32(0, image.getHeight()),
				// upper right corner of picture B
				renderPoint(image2.getWidth(), 0, fromAtoB),
				// upper left corner of picture B
				renderPoint(0, 0, fromAtoB),
				// bottom right corner of picture B
				renderPoint(image2.getWidth(), image2.getHeight(), fromAtoB),
				// bottom left corner of picture B
				renderPoint(0, image2.getHeight(), fromAtoB) };

		// initialization of maximum points
		Point2D_I32 maxTop = corners[0];
		Point2D_I32 maxBottom = corners[0];
		Point2D_I32 maxLeft = corners[0];
		Point2D_I32 maxRight = corners[0];
		for (Point2D_I32 point : corners) {
			if (point.y < maxTop.y) {
				maxTop = point;
			}
			if (point.y > maxBottom.y) {
				maxBottom = point;
			}
			if (point.x < maxLeft.x) {
				maxLeft = point;
			}
			if (point.x > maxRight.x) {
				maxRight = point;
			}
		}

		
		pictureSize.setHeightOfOutputImage(maxBottom.y - maxTop.y);
		pictureSize.setWidthOfOutputImage(maxRight.x - maxLeft.x);
		pictureSize.setShiftRight(-maxLeft.x);
		pictureSize.setShiftDown(-maxTop.y);
		return pictureSize;
	}

	private List<DescribedImage> computeDescriptions(List<Planar<GrayF32>> inputImages,  DetectDescribePoint detectDescriptor) {

		List<DescribedImage> descImages = new LinkedList<>();

		for (int i = 0; i< inputImages.size();i++) {
			DescribedImage descImg = new DescribedImage(inputImages.get(i), detectDescriptor);
			descImg.describe();
			descImages.add(descImg);
		}
		return descImages;
	}

	private static Point2D_I32 renderPoint(int x0, int y0, Homography2D_F64 fromBtoWork) {
		Point2D_F64 result = new Point2D_F64();
		HomographyPointOps_F64.transform(fromBtoWork, new Point2D_F64(x0, y0), result);
		return new Point2D_I32((int) result.x, (int) result.y);
	}

	
}
