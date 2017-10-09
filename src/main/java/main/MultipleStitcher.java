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

public class MultipleStitcher<T extends ImageGray, FD extends TupleDesc> {

	public BufferedImage stitch(List<BufferedImage> images, int cielovaVelkostPismaVPixloch) {
		
		// zmen velkost obrazkov tak, aby na nich boli priblizne rovnako velke pismenka
		List<BufferedImage> resizedImages = resize(images, cielovaVelkostPismaVPixloch);
		
		
		// Detect using the standard SURF feature descriptor and describer
		DetectDescribePoint<GrayF32, BrightFeature> detDesc = FactoryDetectDescribe
				.surfStable(new ConfigFastHessian(1, 2, 1500, 1, 9, 4, 4), null, null, GrayF32.class);
//		DetectDescribePoint<T, BrightFeature> detDesc = FactoryDetectDescribe
//			.sift(new ConfigCompleteSift(-1, 5, 5000));
		ScoreAssociation<BrightFeature> scorer = FactoryAssociation.scoreEuclidean(BrightFeature.class, true);
		AssociateDescription<BrightFeature> associate = FactoryAssociation.greedy(scorer, Double.MAX_VALUE, true);

		// fit the images using a homography. This works well for rotations and
		// distant objects.
		ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher = FactoryMultiViewRobust.homographyRansac(null,
				new ConfigRansac(3000, 1));
//		 ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher =
//		 FactoryMultiViewRobust.homographyLMedS(null, new
//		 ConfigLMedS(1000,3000));
		

		List<DescribedImage> describedImages = computeDescriptions(resizedImages,resizedImages, detDesc);
		
		DescribedImage main = describedImages.remove(0);

		int bestNumberOfMatches = 0;
		DescribedImage bestImageToConnect;
		FastQueue<AssociatedIndex> matches = new FastQueue<>(1000, AssociatedIndex.class, true);
		int indexOfBestImageToConnect = 0;
		while (!describedImages.isEmpty()) {

			bestNumberOfMatches = 0;
			for (int i = 0; i < describedImages.size(); i++) {
				associate.setSource(main.desc);
				associate.setDestination(describedImages.get(i).desc);
				associate.associate();

				if (associate.getMatches().size > bestNumberOfMatches) {
					bestNumberOfMatches = associate.getMatches().size;
					indexOfBestImageToConnect = i;
					matches = cloneMatchesFromAssociater(associate);
				}
			}
			

			bestImageToConnect = describedImages.remove(indexOfBestImageToConnect);
			List<AssociatedPair> pairs = new ArrayList<>();

			for (int i = 0; i < matches.size(); i++) {
				AssociatedIndex match = matches.get(i);
				Point2D_F64 a = main.points.get(match.src);
				Point2D_F64 b = bestImageToConnect.points.get(match.dst);

				pairs.add(new AssociatedPair(a, b, false));
			}
			
			matches.reset();
			// find the best fit model to describe the change between these
			// images
			if (!modelMatcher.process(pairs))
				throw new RuntimeException("Model Matcher failed!");
			Homography2D_F64 homografia = modelMatcher.getModelParameters().copy();
			
			connect(main, bestImageToConnect, homografia);
			
		}
		BufferedImage output = new BufferedImage(main.colorImage.width, main.colorImage.height,BufferedImage.TYPE_INT_RGB);
		ConvertBufferedImage.convertTo(main.colorImage, output, true);
		return output;
	}

	private List<BufferedImage> resize(List<BufferedImage> images, int cielovaVelkostPismaVPixloch) {
		List<BufferedImage> resiznuteObrazky = new ArrayList<>();
		for(BufferedImage image : images) {
			int sizeOfFont = calculateSizeOfFont(image);
			double percentoZvascenia = ((double)cielovaVelkostPismaVPixloch)/sizeOfFont;
			try {
				BufferedImage res = Thumbnails.of(image)
				        .scale(percentoZvascenia)
				        .asBufferedImage();
				
				resiznuteObrazky.add(res);
			} catch (IOException e) {
				
				e.printStackTrace();
			}
		}
		return resiznuteObrazky;
		
	}

	private int calculateSizeOfFont(BufferedImage image) {
		// TODO Auto-generated method stub
		return 20;
	}

	private FastQueue<AssociatedIndex> cloneMatchesFromAssociater(AssociateDescription<BrightFeature> associate) {
		int numberOfMatches = associate.getMatches().getSize();
		FastQueue<AssociatedIndex> matches = new FastQueue<>(numberOfMatches, AssociatedIndex.class, true);
		matches.size = numberOfMatches;
		for (int i = 0; i < associate.getMatches().size; i++) {
			matches.get(i).set(associate.getMatches().get(i));
		}
		return matches;

	}

	private void connect(DescribedImage main, DescribedImage bestImageToConnect, Homography2D_F64 fromAtoB) {
		// specify size of output image
		double scale = 1;
		// BufferedImage bufA = ConvertBufferedImage.convertTo(main.image, null,
		// true);
		// BufferedImage bufB =
		// ConvertBufferedImage.convertTo(bestImageToConnect.image, null, true);
		// Convert into a BoofCV color format
		Planar<GrayF32> colorA = main.colorImage;
		Planar<GrayF32> colorB = bestImageToConnect.colorImage;
		GrayF32 greyA = main.image;
		GrayF32 greyB = bestImageToConnect.image;
		// for meaning of numbers in this array - check function
		// getSizeOfStitchedImage
		int sizeOfOutputColorImage[] = getSizeOfStitchedImage(main.colorImage, bestImageToConnect.colorImage, fromAtoB);
		int sizeOfOutputGrayImage[] = getSizeOfStitchedImage(main.image, bestImageToConnect.image, fromAtoB);
		// Where the output images are rendered into
		Planar<GrayF32> connectedColor = colorA.createNew(sizeOfOutputColorImage[0], sizeOfOutputColorImage[1]);
		GrayF32 connectedGrey = greyA.createNew(sizeOfOutputGrayImage[0], sizeOfOutputGrayImage[1]);
		// Adjust the transform so that the whole image can appear inside of it
		Homography2D_F64 fromAToWorkColor = new Homography2D_F64(scale, 0, sizeOfOutputColorImage[2], 0, scale,
				sizeOfOutputColorImage[3], 0, 0, 1);
		Homography2D_F64 fromWorkToAColor = fromAToWorkColor.invert(null);
		
		Homography2D_F64 fromAToWorkGray = new Homography2D_F64(scale, 0, sizeOfOutputGrayImage[2], 0, scale,
				sizeOfOutputGrayImage[3], 0, 0, 1);
		Homography2D_F64 fromWorkToAGray = fromAToWorkGray.invert(null);
		// Used to render the results onto an image
		PixelTransformHomography_F32 modelGray = new PixelTransformHomography_F32();
		PixelTransformHomography_F32 modelColor = new PixelTransformHomography_F32();
		InterpolatePixelS<GrayF32> interp = FactoryInterpolation.bilinearPixelS(GrayF32.class, BorderType.ZERO);
		InterpolatePixelS<GrayF32> interq = FactoryInterpolation.bilinearPixelS(GrayF32.class, BorderType.ZERO);
		ImageDistort<Planar<GrayF32>, Planar<GrayF32>> distortColor = DistortSupport.createDistortPL(GrayF32.class, modelColor,
				interp, false);
		// asi nie dobre
		ImageDistort<GrayF32, GrayF32> distortGray = FactoryDistort.distortSB(false, interq, GrayF32.class);
		distortGray.setModel(modelGray);
		
		
		
		distortColor.setRenderAll(false);
		distortGray.setRenderAll(false);
		// Render first image
		
		modelColor.set(fromWorkToAGray);
		distortColor.apply(colorA, connectedColor);
		
		modelGray.set(fromWorkToAGray);
		distortGray.apply(greyA, connectedGrey);
		// Render second image
		Homography2D_F64 fromWorkToBGray = fromWorkToAGray.concat(fromAtoB, null);
		
		Homography2D_F64 fromWorkToBColor = fromWorkToAColor.concat(fromAtoB, null);
		modelColor.set(fromWorkToBGray);
		distortColor.apply(colorB, connectedColor);
		modelGray.set(fromWorkToBGray);
		System.out.println(fromWorkToBColor.toString()+System.lineSeparator()+fromWorkToBGray.toString());
		distortGray.apply(greyB, connectedGrey);
		// Convert the rendered image into a BufferedImage
		//BufferedImage output = new BufferedImage(connectedColor.width, connectedColor.height,BufferedImage.TYPE_INT_RGB);
		//ConvertBufferedImage.convertTo(connectedColor, output, true);
		//GrayF32 stitchedImage = ConvertBufferedImage.convertFromSingle(output, null, GrayF32.class);
		main.colorImage = connectedColor.clone();
		main.image = new GrayF32(main.colorImage.width, main.colorImage.height);
		ColorRgb.rgbToGray_Weighted_F32(main.colorImage, main.image);
		main.describe();
	}

	private int[] getSizeOfStitchedImage(ImageBase image, ImageBase image2, Homography2D_F64 fromBtoA) {
		/*
		 * dimension[0] = width of output
 		 * dimension[1] = height of output image
		 * dimension[2] = shift right in homografy
         * dimension[3] = shift down in homografy
		 */
		int[] dimension = new int[4];
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

		dimension[0] = maxRight.x - maxLeft.x;
		dimension[1] = maxBottom.y - maxTop.y;
		dimension[2] = -maxLeft.x;
		dimension[3] = -maxTop.y;
		return dimension;
	}

	private List<DescribedImage> computeDescriptions(List<BufferedImage> inputImages, List<BufferedImage> resizedImages, DetectDescribePoint detDesc) {

		List<DescribedImage> descImages = new LinkedList<>();

		for (int i = 0; i< inputImages.size();i++) {
			DescribedImage descImg = new DescribedImage(inputImages.get(i),resizedImages.get(i), detDesc);
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

	private class DescribedImage {
		Planar<GrayF32> colorImage;
		GrayF32 image;
		FastQueue<BrightFeature> desc;
		List<Point2D_F64> points;
		DetectDescribePoint<GrayF32, BrightFeature> detDesc;
		
		public DescribedImage(BufferedImage image, BufferedImage reducedImage, DetectDescribePoint<GrayF32, BrightFeature> detDesc) {
			this.image = ConvertBufferedImage.convertFromSingle(reducedImage, null, GrayF32.class);
			this.desc = UtilFeature.createQueue(detDesc, 100);
			this.points = new ArrayList<Point2D_F64>();
			this.detDesc = detDesc;
			this.colorImage = ConvertBufferedImage.convertFromMulti(image, null, true, GrayF32.class);
		}

		/**
		 * describes image in this instance using detect descriptor
		 */
		public void describe() {
			detDesc.detect(image);
			desc.reset();
			points.clear();
			for (int i = 0; i < detDesc.getNumberOfFeatures(); i++) {
				points.add(detDesc.getLocation(i).copy());
				desc.grow().setTo(detDesc.getDescription(i));
			}
		}
	}
}
