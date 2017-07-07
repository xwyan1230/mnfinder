/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.micronuclei.analysis;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.micronuclei.analysisinterface.AnalysisException;
import org.micromanager.micronuclei.analysisinterface.AnalysisModule;
import org.micromanager.micronuclei.analysisinterface.ResultRois;

import net.imagej.ops.OpService;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.Regions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.DoubleType;

import org.json.JSONException;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.micronuclei.analysisinterface.AnalysisProperty;
import org.micromanager.micronuclei.analysisinterface.PropertyException;
import org.scijava.Context;

/**
 *
 * @author nico
 */
public class GreenCellsWithOps extends AnalysisModule {
   private final String UINAME = "Green Cells with Ops";
   private final String DESCRIPTION = 
           "<html>Another simple module that finds positive cells in the 2nd channel, <br>" +
           "and identifies these as hits.  Uses ImageJ Ops.";
   private static final Context CONTEXT = new Context(OpService.class);
   
   private AnalysisProperty maxStdDev_;
   private AnalysisProperty maxMeanIntensity_;
   
   public GreenCellsWithOps() {
      try {
         maxStdDev_ = new AnalysisProperty(this.getClass(),
                 "Maximum Std. Dev. of Nuclear image", 
                 "<html>Std. Dev. of grayscale values of original image<br>" +
                          "Used to exclude images with edges</html>", 12500.0);
         maxMeanIntensity_ = new AnalysisProperty(this.getClass(),
                 "Maximum Mean Int. of Nuclear Image", 
                 "<html>If the average intensity of the image is higher<br>" + 
                          "than this number, the image will be skipped", 20000.0);
         List<AnalysisProperty> apl = new ArrayList<AnalysisProperty>();
         apl.add(maxStdDev_);
         apl.add(maxMeanIntensity_);
         
         setAnalysisProperties(apl);
       
      } catch (PropertyException ex) {
         // todo: handle error}
      }
   }
   
   @Override
   public ResultRois analyze(Studio studio, Image[] imgs, Roi roi, JSONObject parms) throws AnalysisException {
      // Context context = new Context(OpService.class);
      OpService ops = CONTEXT.getService(OpService.class);
      int a = 3;
      int b = 4;
      System.out.println(ops.math().add(a, b));
      
      
       if (imgs.length < 2) {
         throw new AnalysisException ("Need at least two channels to find nuclei in green cells");
      }
      boolean showMasks = false;
      try {
         showMasks = parms.getBoolean(AnalysisModule.SHOWMASKS);
      } catch (JSONException jex) { // do nothing
      }
      
      // First locate Nuclei
      ImageProcessor nuclearImgProcessor = studio.data().ij().createProcessor(imgs[0]);
      Img<ShortType> image = ImageJFunctions.wrap(new ImagePlus("nuc", nuclearImgProcessor));
      double mean = ops.stats().mean(image).getRealDouble();
      double stdDev = ops.stats().stdDev(image).getRealDouble();
      studio.alerts().postAlert("Stats", this.getClass(), "Mean: " + mean + ", stdDev: " + stdDev);
      
      int pos = imgs[0].getCoords().getStagePosition();
      if (stdDev > ((Double) maxStdDev_.get())) {
         studio.alerts().postAlert("Skip image", JustNucleiModule.class,
                 "Std. Dev. of image at position " + pos + " (" + 
                  NumberUtils.doubleToDisplayString(stdDev) +
                  ") is higher than the limit you set: " + ((Double) maxStdDev_.get()));
         return new ResultRois(null, null, null);
      }
      if (mean > (Double) maxMeanIntensity_.get()) {
         studio.alerts().postAlert("Skip image", JustNucleiModule.class,
                 "Mean intensity of image at position " + pos + " (" + 
                  NumberUtils.doubleToDisplayString(mean) +
                  ") is higher than the limit you set: " + (Double) maxMeanIntensity_.get());
         return new ResultRois(null, null, null);
      }
      
      // IJ.run(nuclearImgIp, "Subtract Background...", "rolling=5 sliding");
      // Pre-filter to improve nuclear detection and slightly enlarge the masks
      // IJ.run(nuclearImgIp, "Smooth", "");
      image = (Img<ShortType>) ops.filter().gauss(image, 3.0);
      //RandomAccessibleInterval<BitType> otsu = (RandomAccessibleInterval<BitType>) ops.threshold().otsu(image);
      RandomAccessibleInterval<BitType> otsu = (RandomAccessibleInterval<BitType>) ops.threshold().otsu(image);
      List<Shape> shapes = new ArrayList<Shape>();
      HyperSphereShape shape = new HyperSphereShape(1);
      shapes.add(shape);
      RandomAccessibleInterval<BitType> closed = 
              (RandomAccessibleInterval<BitType>) ops.morphology().close(otsu, shapes);
      ImgLabeling labels = ops.labeling().cca(closed, ConnectedComponents.StructuringElement.FOUR_CONNECTED);
      LabelRegions regions = new LabelRegions(labels);
      Iterator rIterator = regions.iterator();
      int counter = 1;
      while (rIterator.hasNext()) {
         LabelRegion region = (LabelRegion) rIterator.next();
         // now get the size of each object....
         DoubleType size = ops.geom().size(region);
         //System.out.println("size of object is: " + size);
         //System.out.println("center of object is: " + region.getCenterOfMass());
         IterableInterval sample = Regions.sample(region, image);
         double roiSum = ops.stats().sum(sample).getRealDouble();
         double roiMean = ops.stats().mean(sample).getRealDouble();
         //System.out.println("Sum of intensities is: " + roiSum);
         //System.out.println("Mean of this object is: "+ roiMean);
         System.out.println(" " + counter++ + "\t" + roiMean );
      }
      
      Rectangle userRoiBounds = null;
      if (roi != null) {
         nuclearImgProcessor.setRoi(roi);
         nuclearImgProcessor = nuclearImgProcessor.crop();
         userRoiBounds = roi.getBounds();
      }
      ImagePlus nuclearImgIp = (new ImagePlus("tmp", nuclearImgProcessor)).duplicate();
      Calibration calibration = nuclearImgIp.getCalibration();
      calibration.pixelWidth = imgs[0].getMetadata().getPixelSizeUm();
      calibration.pixelHeight = imgs[0].getMetadata().getPixelSizeUm();
      calibration.setUnit("um");

      ResultRois rrs = new ResultRois(null, null, null);
      return rrs;
   }

   @Override
   public void reset() {
   }

   @Override
   public String getName() {
      return UINAME;
   }

   @Override
   public String getDescription() {
      return DESCRIPTION;
   }
   
}
