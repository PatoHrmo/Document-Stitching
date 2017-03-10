package util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class test {
	private static final Logger logger = LogManager.getLogger();
	public static void main(String[] args) {
		logger.traceEntry();
		DatasetStitcher s = new DatasetStitcher();
		s.stitchDataset("./multiple");
		logger.traceExit();
	}

}
