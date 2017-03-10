package main;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.struct.FastQueue;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.distort.PointTransformHomography_F64;
import boofcv.alg.distort.impl.DistortSupport;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.core.image.border.BorderType;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.geo.ConfigLMedS;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.gui.image.ShowImages;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F32;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.transform.homography.HomographyPointOps_F64;

public class Stitcher {
	/**
	 * Using abstracted code, find a transform which minimizes the difference between corresponding features
	 * in both images.  This code is completely model independent and is the core algorithms.
	 */
	public static<T extends ImageGray, FD extends TupleDesc> Homography2D_F64
	computeTransform( T imageA , T imageB ,
					  DetectDescribePoint<T,FD> detDesc ,
					  AssociateDescription<FD> associate ,
					  ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher )
	{
		// get the length of the description
		List<Point2D_F64> pointsA = new ArrayList<>();
		FastQueue<FD> descA = UtilFeature.createQueue(detDesc,100);
		List<Point2D_F64> pointsB = new ArrayList<Point2D_F64>();
		FastQueue<FD> descB = UtilFeature.createQueue(detDesc,100);
 
		// extract feature locations and descriptions from each image
		
		describeImage(imageA, detDesc, pointsA, descA);
		
		describeImage(imageB, detDesc, pointsB, descB);
 
		// Associate features between the two images
		
		associate.setSource(descA);
		associate.setDestination(descB);
		associate.associate();
		
		// create a list of AssociatedPairs that tell the model matcher how a feature moved
		FastQueue<AssociatedIndex> matches = associate.getMatches();
		List<AssociatedPair> pairs = new ArrayList<>();
 
		for( int i = 0; i < matches.size(); i++ ) {
			AssociatedIndex match = matches.get(i);
 
			Point2D_F64 a = pointsA.get(match.src);
			Point2D_F64 b = pointsB.get(match.dst);
 
			pairs.add( new AssociatedPair(a,b,false));
		}
 
		// find the best fit model to describe the change between these images
		if( !modelMatcher.process(pairs) )
			throw new RuntimeException("Model Matcher failed!");
 
		// return the found image transform
		return modelMatcher.getModelParameters().copy();
	}
 
	/**
	 * Detects features inside the two images and computes descriptions at those points.
	 */
	private static <T extends ImageGray, FD extends TupleDesc>
	void describeImage(T image,
					   DetectDescribePoint<T,FD> detDesc,
					   List<Point2D_F64> points,
					   FastQueue<FD> listDescs) {
		detDesc.detect(image);
 
		listDescs.reset();
		for( int i = 0; i < detDesc.getNumberOfFeatures(); i++ ) {
			points.add( detDesc.getLocation(i).copy() );
			listDescs.grow().setTo(detDesc.getDescription(i));
		}
	}
 
	/**
	 * Given two input images create and display an image where the two have been overlayed on top of each other.
	 */
	public static <T extends ImageGray>
	BufferedImage stitch( BufferedImage imageA , BufferedImage imageB , Class<T> imageType )
	{
		T inputA = ConvertBufferedImage.convertFromSingle(imageA, null, imageType);
		T inputB = ConvertBufferedImage.convertFromSingle(imageB, null, imageType);
		// Detect using the standard SURF feature descriptor and describer
		DetectDescribePoint<T, BrightFeature> detDesc = FactoryDetectDescribe.surfStable(
				new ConfigFastHessian(1, 2, 2000, 1, 9, 4, 4), null,null, imageType);
		ScoreAssociation<BrightFeature> scorer = FactoryAssociation.scoreEuclidean(BrightFeature.class,true);
		AssociateDescription<BrightFeature> associate = FactoryAssociation.greedy(scorer,1,true);
		// fit the images using a homography.  This works well for rotations and distant objects.
		ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher =
				FactoryMultiViewRobust.homographyRansac(null,new ConfigRansac(1000,0.5));
//		ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher =
//				FactoryMultiViewRobust.homographyLMedS(null, new ConfigLMedS(1000,1000));
		
		Homography2D_F64 H = computeTransform(inputA, inputB, detDesc, associate, modelMatcher);
		
		return renderStitching(imageA,imageB,H);
	}
 
	/**
	 * Renders and displays the stitched together images
	 */
	public static BufferedImage renderStitching( BufferedImage imageA, BufferedImage imageB ,
			Homography2D_F64 fromAtoB )
	{
		long cas = System.currentTimeMillis();
		// specify size of output image
		double scale = 1;
 
		// Convert into a BoofCV color format
		Planar<GrayF32> colorA =
				ConvertBufferedImage.convertFromMulti(imageA, null, true, GrayF32.class);
		Planar<GrayF32> colorB =
				ConvertBufferedImage.convertFromMulti(imageB, null,true, GrayF32.class);
		
        
        int sizeOfOutputImage[] = getSizeOfStitchedImage(imageA, imageB, fromAtoB);
		// Where the output images are rendered into
		Planar<GrayF32> work = colorA.createNew(sizeOfOutputImage[0], sizeOfOutputImage[1]);
		
		// Adjust the transform so that the whole image can appear inside of it
		Homography2D_F64 fromAToWork = new Homography2D_F64(scale,0,sizeOfOutputImage[2],0,scale,sizeOfOutputImage[3],0,0,1);
		Homography2D_F64 fromWorkToA = fromAToWork.invert(null);
 
		// Used to render the results onto an image
		PixelTransformHomography_F32 model = new PixelTransformHomography_F32();
		InterpolatePixelS<GrayF32> interp = FactoryInterpolation.bilinearPixelS(GrayF32.class, BorderType.ZERO);
		ImageDistort<Planar<GrayF32>,Planar<GrayF32>> distort =
				DistortSupport.createDistortPL(GrayF32.class, model, interp, false);
		distort.setRenderAll(false);
		// Render first image
		model.set(fromWorkToA);
		distort.apply(colorA,work);
 
		// Render second image
		Homography2D_F64 fromWorkToB = fromWorkToA.concat(fromAtoB,null);
		model.set(fromWorkToB);
		distort.apply(colorB,work);
 
		// Convert the rendered image into a BufferedImage
		BufferedImage output = new BufferedImage(work.width,work.height,imageA.getType());
		ConvertBufferedImage.convertTo(work,output,true);
 
		Graphics2D g2 = output.createGraphics();
		cas = System.currentTimeMillis()-cas;
		System.out.println(cas);
		return output;
	}
 
	private static Point2D_I32 renderPoint( int x0 , int y0 , Homography2D_F64 fromBtoWork )
	{
		Point2D_F64 result = new Point2D_F64();
		HomographyPointOps_F64.transform(fromBtoWork, new Point2D_F64(x0, y0), result);
		return new Point2D_I32((int)result.x,(int)result.y);
	}
	private static int[] getSizeOfStitchedImage(BufferedImage imageA, BufferedImage imageB ,
			Homography2D_F64 fromBtoA) {
		
		Homography2D_F64 fromAtoB = new Homography2D_F64();
		fromBtoA.invert(fromAtoB);
		// FIXME naozaj?
//		Point2D_I32 upperRightA = new Point2D_I32(imageA.getWidth(),0);
//		Point2D_I32 upperLeftA = new Point2D_I32(0,0);
//		Point2D_I32 bottomRightA = new Point2D_I32(imageA.getWidth(),imageA.getHeight());
//		Point2D_I32 bottomLeftA = new Point2D_I32(0,imageA.getHeight());
		Point2D_I32 corners[]  = new Point2D_I32[]{
				//upper right corner of picture A
				new Point2D_I32(imageA.getWidth(),0),
				//upper left corner of picture A
				new Point2D_I32(0,0),
				//bottom right corner of picture A
				new Point2D_I32(imageA.getWidth(),imageA.getHeight()),
				//bottom left corner of picture A
				new Point2D_I32(0,imageA.getHeight()),
				// upper right corner of picture B
        		renderPoint(imageB.getWidth(),0, fromAtoB),
        		// upper left corner of picture B
        		renderPoint(0,0, fromAtoB),
        		// bottom right corner of picture B
        		renderPoint(imageB.getWidth(),imageB.getHeight(), fromAtoB),
        		// bottom left corner of picture B
        		renderPoint(0,imageB.getHeight(), fromAtoB)
				};

//        System.out.println(upperLeftB.x+" "+upperLeftB.y);
//        System.out.println(upperRightB.x+" "+upperRightB.y);
//        System.out.println(bottomLeftB.x+" "+bottomLeftB.y);
//        System.out.println(bottomRightB.x+" "+bottomRightB.y);
        // initialization of maximum points
        Point2D_I32 maxTop = corners[0];
		Point2D_I32 maxBottom=corners[0];
		Point2D_I32 maxLeft=corners[0];
		Point2D_I32 maxRight=corners[0];
        for(Point2D_I32 point : corners){
        	if(point.y<maxTop.y) {
        		maxTop = point;
        	}
        	if(point.y>maxBottom.y) {
        		maxBottom = point;
        	}
        	if(point.x<maxLeft.x) {
        		maxLeft = point;
        	}
        	if(point.x> maxRight.x) {
        		maxRight = point;
        	}
        }
        /*
		 * dimension[0] = width of output
		 * dimension[1] = height of output image
		 * dimension[2] = shift right in homografy
		 * dimension[3] = shift down in homografy
		 */
		int[] dimension  = new int[4];
		dimension[0] = maxRight.x-maxLeft.x;
		dimension[1] = maxBottom.y - maxTop.y;
        dimension[2] = -maxLeft.x;
        dimension[3] = -maxTop.y;
		return dimension;
	}
}
