package main;

import boofcv.abst.feature.associate.WrapAssociateSurfBasic;
import boofcv.abst.feature.describe.ConfigBrief;
import boofcv.abst.feature.detdesc.ConfigCompleteSift;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.alg.feature.associate.AssociateSurfBasic;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.describe.FactoryDescribeRegionPoint;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.struct.feature.TupleDesc_B;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.image.GrayF32;

public class StitcherConfigurationFactory {
	
	public static StitcherConfiguration surfGreedyBright() {
		StitcherConfiguration config = new StitcherConfiguration();
		config.associate = new WrapAssociateSurfBasic(new AssociateSurfBasic(
				FactoryAssociation.greedy(FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true), 0.04, true)));
		config.detDesc = FactoryDetectDescribe
				.surfStable(new ConfigFastHessian(2, 2, 1400, 1, 9, 4, 4), null, null, GrayF32.class);
		return config;
	}
	public static StitcherConfiguration siftGreedyBright() {
		StitcherConfiguration config = new StitcherConfiguration();
		config.associate = new WrapAssociateSurfBasic(new AssociateSurfBasic(
				FactoryAssociation.greedy(FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true), 0.03, true)));
		config.detDesc = FactoryDetectDescribe
				.sift(new ConfigCompleteSift(0,1,500));
		return config;
	}
	public static StitcherConfiguration briefGreedy() {
		StitcherConfiguration config = new StitcherConfiguration();
		ConfigBrief configBrief = new ConfigBrief(false);
		configBrief.numPoints = 256;
		config.detDesc = FactoryDetectDescribe.fuseTogether(
				FactoryDetectDescribe.sift(new ConfigCompleteSift(0,1,1000)),
				null,
				FactoryDescribeRegionPoint.brief(configBrief, GrayF32.class));
		config.associate = FactoryAssociation.greedy(
				FactoryAssociation.scoreHamming(TupleDesc_B.class),
				35, true);
		return config;
	}
	public static StitcherConfiguration surfKD() {
		StitcherConfiguration config = new StitcherConfiguration();
		config.associate = FactoryAssociation.kdtree(64, 250);
		config.associate.setThreshold(0.15);
		config.detDesc = FactoryDetectDescribe
				.surfStable(new ConfigFastHessian(2, 2, 1250, 1, 9, 3, 3), null, null, GrayF32.class);
		return config;
	}
	public static StitcherConfiguration surfRandomKD() {
		StitcherConfiguration config = new StitcherConfiguration();
		config.associate = FactoryAssociation.kdRandomForest(64, 250, 5, 8, 0);
		config.associate.setThreshold(0.15);
		config.detDesc = FactoryDetectDescribe
				.surfStable(new ConfigFastHessian(2, 2, 1250, 1, 9, 3, 3), null, null, GrayF32.class);
		return config;
	}
	public static StitcherConfiguration surfGreedy() {
		StitcherConfiguration config = new StitcherConfiguration();
		config.associate = FactoryAssociation.greedy(FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true), 0.03, true);
		config.detDesc = FactoryDetectDescribe
				.surfStable(new ConfigFastHessian(2, 2, 1250, 1, 9, 3, 3), null, null, GrayF32.class);
		return config;
	}
	public static StitcherConfiguration siftGreedy() {
		StitcherConfiguration config = new StitcherConfiguration();
		config.associate = FactoryAssociation.greedy(FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true), 0.03, true);
		config.detDesc = FactoryDetectDescribe
				.sift(new ConfigCompleteSift(0,4,125));
		return config;
	}
	
	
}
