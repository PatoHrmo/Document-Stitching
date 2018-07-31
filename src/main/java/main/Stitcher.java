package main;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.nn.FactoryNearestNeighbor;
import org.ddogleg.struct.FastQueue;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.AssociateNearestNeighbor;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.associate.WrapAssociateSurfBasic;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.alg.color.ColorRgb;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.distort.impl.DistortSupport;
import boofcv.alg.feature.associate.AssociateSurfBasic;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.core.image.border.BorderType;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.geo.ConfigLMedS;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.transform.homography.HomographyPointOps_F64;

public class Stitcher {
	
	private DetectDescribePoint<GrayF32, BrightFeature> detDesc;
	private AssociateDescription associate;
	private ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher;
    public Stitcher() {
    	this(StitcherConfigurationFactory.surfGreedyBright());
		
    }
    public Stitcher(StitcherConfiguration config) {
    	this.detDesc = config.detDesc;
    	this.associate = config.associate;
    }
    
	public Planar<GrayF32> stitch(List<Planar<GrayF32>> images) {
		//long beginningtime = System.currentTimeMillis();
		modelMatcher = FactoryMultiViewRobust.homographyRansac(null,
				new ConfigRansac(5000, 5));
		DescribedImage mainImage = new DescribedImage(images.remove(0), detDesc);
		List<DescribedImage> imagesToStitch = computeDescriptions(images, detDesc);
		
		int bestNumberOfMatches;
		DescribedImage bestImageToStitch;
		int indexOfBestImageToStitch = 0;
		FastQueue<AssociatedIndex> matches = new FastQueue<>(AssociatedIndex.class, true);
		
		while (!imagesToStitch.isEmpty()) {
			mainImage.describe();
			//System.out.println("description + init grey images ="+ (System.currentTimeMillis()-beginningtime));
			bestNumberOfMatches = 0;
			associate.setSource(mainImage.description);
			for (int i = 0; i < imagesToStitch.size(); i++) {
				//beginningtime = System.currentTimeMillis();
				associate.setDestination(imagesToStitch.get(i).description);
				associate.associate();
				//System.out.println("matching ="+ (System.currentTimeMillis()-beginningtime));
				if (associate.getMatches().size > bestNumberOfMatches) {
					bestNumberOfMatches = associate.getMatches().size;
					indexOfBestImageToStitch = i;
					matches = cloneMatchesFromAssociate();
				}
			}
			

			bestImageToStitch = imagesToStitch.remove(indexOfBestImageToStitch);
			List<AssociatedPair> pairs = new ArrayList<>();
			System.out.println("number of pairs "+matches.size());
			for (int i = 0; i < matches.size(); i++) {
				AssociatedIndex match = matches.get(i);
				Point2D_F64 a = mainImage.locationsOfFeaturePoints.get(match.src);
				Point2D_F64 b = bestImageToStitch.locationsOfFeaturePoints.get(match.dst);

				pairs.add(new AssociatedPair(a, b, false));
			}
			
			matches.reset();
			// find the best fit model to describe the change between these
			// images
			//beginningtime = System.currentTimeMillis();
			if (!modelMatcher.process(pairs))
				throw new RuntimeException("Model Matcher failed!");
			Homography2D_F64 homografy = modelMatcher.getModelParameters().copy();
			//System.out.println("hladanie homografie"+ (System.currentTimeMillis()-beginningtime));
			//beginningtime = System.currentTimeMillis();
			connect(mainImage, bestImageToStitch, homografy);
			//System.out.println("spojenie = "+ (System.currentTimeMillis()-beginningtime));
			
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

	private void connect(DescribedImage main, DescribedImage secondary, Homography2D_F64 fromAtoB) {
		
		double scale = 1;
		
		Planar<GrayF32> colorA = main.colorImage;
		Planar<GrayF32> colorB = secondary.colorImage;
		
		StitchedPictureSize sizeOfOutputImage = getSizeOfStitchedImage(main.colorImage, secondary.colorImage, fromAtoB);
		// Where the output images are rendered into
		Planar<GrayF32> connected = colorA.createNew(sizeOfOutputImage.getWidthOfOutputImage(),
				sizeOfOutputImage.getHeightOfOutputImage());
		Homography2D_F64 fromAToConnected = new Homography2D_F64(scale, 0, sizeOfOutputImage.getShiftRight(),0, scale,
				sizeOfOutputImage.getShiftDown(), 0, 0, 1);
		Homography2D_F64 fromConnectedToA = fromAToConnected.invert(null);
		// Used to render the results onto an image
		PixelTransformHomography_F32 modelColor = new PixelTransformHomography_F32();
		InterpolatePixelS<GrayF32> interp = FactoryInterpolation.bilinearPixelS(GrayF32.class, BorderType.ZERO);
		ImageDistort<Planar<GrayF32>, Planar<GrayF32>> distort = DistortSupport.createDistortPL(GrayF32.class, modelColor,
				interp, false);
		distort.setRenderAll(false);
		// Render first image
		modelColor.set(fromConnectedToA);
		distort.apply(colorA, connected);
		// Render second image
		Homography2D_F64 fromConnectedToB = fromConnectedToA.concat(fromAtoB, null);
		modelColor.set(fromConnectedToB);
		distort.apply(colorB, connected);
		main.colorImage = connected;
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
