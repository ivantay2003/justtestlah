package io.github.martinschneider.justtestlah.visual;

import io.github.martinschneider.justtestlah.configuration.JustTestLahConfiguration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.opencv.core.Core;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Template matcher
 *
 * <p>This class provides methods to check whether a given image (template) is part of another one
 * (target). We use a simple (yet effective) way to detect the template image in various sizes by
 * scaling the target up and down to a minimum and maximum size.
 *
 * <p>We return on the first match that exceeds the specific threshold (matching quality). This
 * means that it is not necessarily the best possible match.
 */
public class TemplateMatcher {

  private static final int MIN_IMAGE_WIDTH = 320;
  private static final int MAX_IMAGE_WIDTH = 2048;

  private static final Logger LOG = LoggerFactory.getLogger(TemplateMatcher.class);
  private static final String FILE_EXTENSION = "png";
  private static final String DATE_PATTERN = "yyyy-MM-dd HH.mm.ss";

  private JustTestLahConfiguration configuration;

  /**
   * Check whether the template appears anywhere within the target image.
   *
   * @param targetFile path to the target file
   * @param templateFile path to the template file
   * @param threshold matching threshold
   * @return {@link Match}
   */
  public Match match(String targetFile, String templateFile, double threshold) {
    return match(
        targetFile,
        templateFile,
        threshold,
        LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_PATTERN)));
  }

  /**
   * Check whether the template appears anywhere within the target image.
   *
   * @param targetFile path to the target file
   * @param templateFile path to the template file
   * @param threshold matching threshold
   * @param description of the check
   * @return {@link Match}
   */
  public Match match(String targetFile, String templateFile, double threshold, String description) {
    checkForOpenCv();
    Mat image = Imgcodecs.imread(targetFile);
    Mat templ = Imgcodecs.imread(templateFile);
    MinMaxLocResult bestMatch = new MinMaxLocResult();
    Mat originalImage = image;
    /**
     * This could be improved by combining the two while loops (checking the original size first,
     * then slightly smaller and larger images etc. instead of first scaling all the way down and
     * then scaling up (or by using a framework that performs size-invariant template matching).
     */
    while (image.width() > MIN_IMAGE_WIDTH) {
      int resultCols = image.cols() - templ.cols() + 1;
      int resultRows = image.rows() - templ.rows() + 1;
      Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);
      Imgproc.matchTemplate(image, templ, result, Imgproc.TM_CCOEFF_NORMED);
      MinMaxLocResult match = Core.minMaxLoc(result);
      if (match.maxVal > bestMatch.maxVal) {
        bestMatch = match;
      }
      if (bestMatch.maxVal >= threshold) {
        break;
      }
      // else
      image = scaleImage(image, 0.9);
    }
    if (bestMatch.maxVal < threshold) {
      image = originalImage;
    }
    while (bestMatch.maxVal < threshold && image.width() < MAX_IMAGE_WIDTH) {
      int resultCols = image.cols() - templ.cols() + 1;
      int resultRows = image.rows() - templ.rows() + 1;
      Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);
      Imgproc.matchTemplate(image, templ, result, Imgproc.TM_CCOEFF_NORMED);
      MinMaxLocResult match = Core.minMaxLoc(result);
      if (match.maxVal > bestMatch.maxVal) {
        bestMatch = match;
      }
      if (match.maxVal >= threshold) {
        break;
      }
      // else
      image = scaleImage(image, 1.1);
    }
    if (bestMatch.maxVal >= threshold) {
      LOG.info(
          "Image {} contains image {} with match quality {}",
          targetFile,
          templateFile,
          bestMatch.maxVal);

      Imgproc.rectangle(
          image,
          new Point(bestMatch.maxLoc.x, bestMatch.maxLoc.y),
          new Point(bestMatch.maxLoc.x + templ.cols(), bestMatch.maxLoc.y + templ.rows()),
          new Scalar(255, 0, 0),
          10);

      String fileName =
          System.getProperty("user.dir") + "/target/" + description + "." + FILE_EXTENSION;
      LOG.info("Writing result of template matching to {}", fileName);
      Imgcodecs.imwrite(fileName, image);
      return new Match(
          true,
          (int) Math.round(bestMatch.maxLoc.x + templ.cols() / 2),
          (int) Math.round(bestMatch.maxLoc.y + templ.rows() / 2));
    }
    // else
    LOG.info(
        "Image {} does not contain image {}. The closest match has quality {}",
        targetFile,
        templateFile,
        bestMatch.maxVal);
    return new Match(false);
  }

  @Autowired
  public void setConfiguration(JustTestLahConfiguration configuration) {
    this.configuration = configuration;
  }

  /** Check whether OpenCV is enabled. */
  private void checkForOpenCv() {
    if (!configuration.isOpenCvEnabled()) {
      throw new UnsupportedOperationException(
          "OpenCV is not enabled. Set opencv.enabled=true in justtestlah.properties!");
    }
  }

  private Mat scaleImage(Mat image, double scaleFactor) {
    Mat resizedImage = new Mat();
    Size sz = new Size(image.width() * scaleFactor, image.height() * scaleFactor);
    Imgproc.resize(image, resizedImage, sz);
    return resizedImage;
  }
}