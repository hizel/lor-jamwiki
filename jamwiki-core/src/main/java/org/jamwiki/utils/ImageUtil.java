/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, version 2.1, dated February 1999.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the latest version of the GNU Lesser General
 * Public License as published by the Free Software Foundation;
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program (LICENSE.txt); if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.jamwiki.utils;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.sf.ehcache.Element;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.jamwiki.DataAccessException;
import org.jamwiki.Environment;
import org.jamwiki.WikiBase;
import org.jamwiki.WikiException;
import org.jamwiki.WikiMessage;
import org.jamwiki.model.Namespace;
import org.jamwiki.model.Topic;
import org.jamwiki.model.TopicType;
import org.jamwiki.model.TopicVersion;
import org.jamwiki.model.WikiImage;
import org.jamwiki.model.WikiFile;
import org.jamwiki.model.WikiFileVersion;
import org.jamwiki.model.WikiUser;
import org.jamwiki.parser.ParserException;
import org.jamwiki.parser.ParserOutput;
import org.jamwiki.parser.ParserUtil;

/**
 * Utility methods for readding images from disk, saving images to disk,
 * resizing images, and returning information about images such as width and
 * height.
 */
public class ImageUtil {

	private static final WikiLogger logger = WikiLogger.getLogger(ImageUtil.class.getName());
	/** Cache name for the cache of image dimensions. */
	private static final String CACHE_IMAGE_DIMENSIONS = "org.jamwiki.utils.ImageUtil.CACHE_IMAGE_DIMENSIONS";

	static {
		// manually set the ImageIO temp directory so that systems with incorrect defaults won't fail
		// when processing images.
		File directory = WikiUtil.getTempDirectory();
		if (directory.exists()) {
			ImageIO.setCacheDirectory(directory);
		}
	}

	/**
	 *
	 */
	private ImageUtil() {
	}

	/**
	 *
	 */
	private static void addToCache(WikiImage wikiImage, int width, int height) {
		ImageDimensions dimensions = new ImageDimensions(width, height);
		String key = wikiImage.getVirtualWiki() + "/" + wikiImage.getUrl();
		WikiCache.addToCache(CACHE_IMAGE_DIMENSIONS, key, dimensions);
	}

	/**
	 * Utility method for building the URL to an image file (NOT the image topic
	 * page).  If the file does not exist then this method will return
	 * <code>null</code>.
	 *
	 * @param context The current servlet context.
	 * @param virtualWiki The virtual wiki for the URL that is being created.
	 * @param topicName The name of the image for which a link is being created.
	 * @return The URL to an image file (not the image topic) or <code>null</code>
	 *  if the file does not exist.
	 * @throws DataAccessException Thrown if any error occurs while retrieving file info.
	 */
	public static String buildImageFileUrl(String context, String virtualWiki, String topicName) throws DataAccessException {
		WikiFile wikiFile = WikiBase.getDataHandler().lookupWikiFile(virtualWiki, topicName);
		if (wikiFile == null) {
			return null;
		}
		return buildRelativeImageUrl(context, wikiFile.getUrl());
	}

	/**
	 *
	 */
	private static String buildRelativeImageUrl(String context, String filename) {
		String url = FilenameUtils.normalize(Environment.getValue(Environment.PROP_FILE_DIR_RELATIVE_PATH) + "/" + filename);
		return FilenameUtils.separatorsToUnix(url);
	}

	/**
	 * Utility method for building an anchor tag that links to an image page
	 * and includes the HTML image tag to display the image.
	 *
	 * @param context The servlet context for the link that is being created.
	 * @param linkVirtualWiki The virtual wiki to use when looking up the
	 *  image/file, and when linking to the image/file topic page.
	 * @param topicName The name of the image for which a link is being
	 *  created.
	 * @param imageMetadata A container for the image display params, such as
	 *  border, alignment, caption, etc.
	 * @param style The CSS class to use with the img HTML tag.  This value
	 *  can be <code>null</code> or empty if no custom style is used.
	 * @param escapeHtml Set to <code>true</code> if the caption should be
	 *  HTML escaped.  This value should be <code>true</code> in any case
	 *  where the caption is not guaranteed to be free from potentially
	 *  malicious HTML code.
	 * @return The full HTML required to display an image enclosed within an
	 *  HTML anchor tag that links to the image topic page.
	 * @throws DataAccessException Thrown if any error occurs while retrieving image
	 *  information.
	 * @throws IOException Thrown if any error occurs while reading image information.
	 */
	public static String buildImageLinkHtml(String context, String linkVirtualWiki, String topicName, ImageMetadata imageMetadata, String style, boolean escapeHtml) throws DataAccessException, IOException {
		String url = ImageUtil.buildImageFileUrl(context, linkVirtualWiki, topicName);
		if (url == null) {
			return ImageUtil.buildUploadLink(context, linkVirtualWiki, topicName);
		}
		Topic topic = WikiBase.getDataHandler().lookupTopic(linkVirtualWiki, topicName, false, null);
		StringBuilder html = new StringBuilder();
		String caption = imageMetadata.getCaption();
		if (topic.getTopicType() == TopicType.FILE) {
			// file, not an image - use the file name, minus the translated/untranslated namespace
			if (StringUtils.isBlank(caption) && topicName.startsWith(Namespace.namespace(Namespace.FILE_ID).getLabel(topic.getVirtualWiki()))) {
				caption = topicName.substring(Namespace.namespace(Namespace.FILE_ID).getLabel(topic.getVirtualWiki()).length() + 1);
			} else if (StringUtils.isBlank(caption) && topicName.startsWith(Namespace.namespace(Namespace.FILE_ID).getDefaultLabel())) {
				caption = topicName.substring(Namespace.namespace(Namespace.FILE_ID).getDefaultLabel().length() + 1);
			}
			html.append("<a href=\"").append(url).append("\">");
			if (escapeHtml) {
				html.append(StringEscapeUtils.escapeHtml(caption));
			} else {
				html.append(caption);
			}
			html.append("</a>");
			return html.toString();
		}
		WikiFile wikiFile = WikiBase.getDataHandler().lookupWikiFile(topic.getVirtualWiki(), topic.getName());
		WikiImage wikiImage = null;
		try {
			wikiImage = ImageUtil.initializeImage(wikiFile, imageMetadata);
		} catch (FileNotFoundException e) {
			// do not log the full exception as the logs can fill up very for this sort of error, and it is generally due to a bad configuration.  instead log a warning message so that the administrator can try to fix the problem
			logger.warning("File not found while parsing image link for topic: " + topic.getVirtualWiki() + " / " + topicName + ".  Make sure that the following file exists and is readable by the JAMWiki installation: " + e.getMessage());
			return ImageUtil.buildUploadLink(context, topic.getVirtualWiki(), topicName);
		}
		String imageWrapperDiv = ImageUtil.buildImageWrapperDivs(imageMetadata, wikiImage.getWidth());
		if (!StringUtils.isWhitespace(imageMetadata.getLink())) {
			if (imageMetadata.getLink() == null) {
				// no link set, link to the image topic page.  At this point we have validated
				// that the link is an image, so do not perform further validation and link to the
				// CURRENT virtual wiki, even if it is a shared image
				String link = LinkUtil.buildTopicUrl(context, linkVirtualWiki, topicName, false);
				html.append("<a class=\"wikiimg\" href=\"").append(link).append("\">");
			} else {
				try {
					// try to parse as an external link
					String openTag = LinkUtil.buildHtmlLinkOpenTag(imageMetadata.getLink(), "wikiimg");
					html.append(openTag);
				} catch (ParserException e) {
					// not an external link, but an internal link
					WikiLink wikiLink = LinkUtil.parseWikiLink(topic.getVirtualWiki(), imageMetadata.getLink());
					String internalLinkVirtualWiki = ((wikiLink.getVirtualWiki() != null) ? wikiLink.getVirtualWiki().getName() : linkVirtualWiki);
					String link = LinkUtil.buildTopicUrl(context, internalLinkVirtualWiki, wikiLink);
					html.append("<a class=\"wikiimg\" href=\"").append(link).append("\">");
				}
			}
		}
		if (StringUtils.isBlank(style)) {
			style = "wikiimg";
		}
		if (imageMetadata.getBordered()) {
			style += " thumbborder";
		}
		html.append("<img class=\"").append(style).append("\" src=\"");
		html.append(buildRelativeImageUrl(context, wikiImage.getUrl()));
		html.append('\"');
		html.append(" width=\"").append(wikiImage.getWidth()).append('\"');
		html.append(" height=\"").append(wikiImage.getHeight()).append('\"');
		String alt = imageMetadata.getAlt();
		html.append(" alt=\"").append(StringEscapeUtils.escapeHtml(alt)).append('\"');
		if (imageMetadata.getVerticalAlignment() != ImageVerticalAlignmentEnum.NOT_SPECIFIED) {
			html.append(" style=\"vertical-align: ").append(imageMetadata.getVerticalAlignment().toString()).append('\"');
		}
		html.append(" />");
		if (!StringUtils.isWhitespace(imageMetadata.getLink())) {
			html.append("</a>");
		}
		if (!StringUtils.isBlank(caption)) {
			// captions are only displayed for thumbnails and framed images
			html.append("\n<div class=\"thumbcaption\">");
			if (escapeHtml) {
				html.append(StringEscapeUtils.escapeHtml(caption));
			} else {
				html.append(caption);
			}
			html.append("</div>\n");
		}
		return MessageFormat.format(imageWrapperDiv, html.toString());
	}

	/**
	 * Given a file URL and a maximum dimension, return a path for the file.
	 */
	private static String buildImagePath(String currentUrl, int originalWidth, int scaledWidth) {
		if (originalWidth <= scaledWidth) {
			// no resizing necessary, return the original URL
			return currentUrl;
		}
		String path = currentUrl;
		String dimensionInfo = "-" + scaledWidth + "px";
		int pos = path.lastIndexOf('.');
		if (pos != -1) {
			path = path.substring(0, pos) + dimensionInfo + path.substring(pos);
		} else {
			path += dimensionInfo;
		}
		return path;
	}

	/**
	 * Determine the CSS styles to apply to the image wrapper div.
	 */
	private static String buildImageWrapperDivs(ImageMetadata imageMetadata, int width) {
		// CSS and wrappers are processed differently for thumb/frame vs. non-thumb/non-frame
		if (imageMetadata.getBorder() != ImageBorderEnum.THUMB && imageMetadata.getBorder() != ImageBorderEnum.FRAME) {
			if (imageMetadata.getHorizontalAlignment() == ImageHorizontalAlignmentEnum.LEFT) {
				return "<div class=\"floatleft\">{0}</div>";
			} else if (imageMetadata.getHorizontalAlignment() == ImageHorizontalAlignmentEnum.RIGHT) {
				return "<div class=\"floatright\">{0}</div>";
			} else if (imageMetadata.getHorizontalAlignment() == ImageHorizontalAlignmentEnum.CENTER) {
				return "<div class=\"center\">\n<div class=\"floatnone\">{0}</div>\n</div>";
			} else if (imageMetadata.getHorizontalAlignment() == ImageHorizontalAlignmentEnum.NONE) {
				return "<div class=\"floatnone\">{0}</div>";
			} else {
				return "{0}";
			}
		} else {
			// the inner div must specify a width
			String styleWidth = " style=\"width:" + (width + 2) + "px\"";
			if (imageMetadata.getHorizontalAlignment() == ImageHorizontalAlignmentEnum.CENTER) {
				return "<div class=\"center\">\n<div class=\"thumb tnone\">\n<div class=\"thumbinner\"" + styleWidth + ">{0}</div>\n</div>\n</div>";
			} else if (imageMetadata.getHorizontalAlignment() == ImageHorizontalAlignmentEnum.LEFT) {
				return "<div class=\"thumb tleft\">\n<div class=\"thumbinner\"" + styleWidth + ">{0}</div>\n</div>";
			} else {
				return "<div class=\"thumb tright\">\n<div class=\"thumbinner\"" + styleWidth + ">{0}</div>\n</div>";
			}
		}
	}

	/**
	 *
	 */
	private static String buildUploadLink(String context, String virtualWiki, String topicName) throws DataAccessException {
		WikiLink uploadLink = LinkUtil.parseWikiLink(virtualWiki, "Special:Upload?topic=" + topicName);
		return LinkUtil.buildInternalLinkHtml(context, virtualWiki, uploadLink, topicName, "edit", null, true);
	}

	/**
	 *
	 */
	private static int calculateImageIncrement(int dimension) {
		int increment = Environment.getIntValue(Environment.PROP_IMAGE_RESIZE_INCREMENT);
		double result = Math.ceil((double)dimension / (double)increment) * increment;
		return (int)result;
	}

	/**
	 * Determine the scaled dimensions, rounded to an increment for performance reasons,
	 * given a max width and height.  For example, if the original dimensions are 800x400,
	 * the max width height are 200, and the increment is 400, the result is 400x200.
	 */
	private static ImageDimensions calculateIncrementalDimensions(WikiImage wikiImage, ImageDimensions originalDimensions, ImageDimensions scaledDimensions) throws IOException {
		int increment = Environment.getIntValue(Environment.PROP_IMAGE_RESIZE_INCREMENT);
		// use width for incremental resizing
		int incrementalWidth = calculateImageIncrement(scaledDimensions.getWidth());
		if (increment <= 0 || incrementalWidth >= originalDimensions.getWidth()) {
			// let the browser scale the image
			return originalDimensions;
		}
		int incrementalHeight = (int)Math.round(((double)incrementalWidth / (double)originalDimensions.getWidth()) * (double)originalDimensions.getHeight());
		// check to see if an image with the desired dimensions already exists on the filesystem
		String newUrl = buildImagePath(wikiImage.getUrl(), originalDimensions.getWidth(), incrementalWidth);
		File newImageFile = new File(Environment.getValue(Environment.PROP_FILE_DIR_FULL_PATH), newUrl);
		if (newImageFile.exists()) {
			return new ImageDimensions(incrementalWidth, incrementalHeight);
		}
		// otherwise generate a scaled instance
		File imageFile = new File(Environment.getValue(Environment.PROP_FILE_DIR_FULL_PATH), wikiImage.getUrl());
		BufferedImage bufferedImage = ImageUtil.resizeImage(imageFile, incrementalWidth, incrementalHeight);
		newUrl = buildImagePath(wikiImage.getUrl(), originalDimensions.getWidth(), bufferedImage.getWidth());
		newImageFile = new File(Environment.getValue(Environment.PROP_FILE_DIR_FULL_PATH), newUrl);
		ImageUtil.saveImage(bufferedImage, newImageFile);
		return new ImageDimensions(bufferedImage.getWidth(), bufferedImage.getHeight());
	}


	/**
	 * Determine the scaled dimensions, given a max width and height.  For example, if
	 * the original dimensions are 800x400 and the max width height are 200, the result
	 * is 200x100.
	 */
	private static ImageDimensions calculateScaledDimensions(ImageDimensions originalDimensions, int maxWidth, int maxHeight) {
		if (maxWidth <= 0 && maxHeight <=0) {
			return originalDimensions;
		}
		double heightScalingFactor = ((double)maxHeight / (double)originalDimensions.getHeight());
		double widthScalingFactor = ((double)maxWidth / (double)originalDimensions.getWidth());
		// scale by whichever is proportionally smaller
		int width, height;
		if (maxWidth <= 0) {
			width = (int)Math.round(heightScalingFactor * (double)originalDimensions.getWidth());
			height = (int)Math.round(heightScalingFactor * (double)originalDimensions.getHeight());
		} else if (maxHeight <= 0) {
			width = (int)Math.round(widthScalingFactor * (double)originalDimensions.getWidth());
			height = (int)Math.round(widthScalingFactor * (double)originalDimensions.getHeight());
		} else if (heightScalingFactor < widthScalingFactor) {
			width = (int)Math.round(heightScalingFactor * (double)originalDimensions.getWidth());
			height = (int)Math.round(heightScalingFactor * (double)originalDimensions.getHeight());
		} else {
			width = (int)Math.round(widthScalingFactor * (double)originalDimensions.getWidth());
			height = (int)Math.round(widthScalingFactor * (double)originalDimensions.getHeight());
		}
		return new ImageDimensions(width, height);
	}

	/**
	 * Given a filename, generate the URL to use to store the file on the filesystem.
	 */
	public static String generateFileUrl(String virtualWiki, String filename, Date date) throws WikiException {
		if (StringUtils.isBlank(virtualWiki)) {
			throw new WikiException(new WikiMessage("common.exception.novirtualwiki"));
		}
		String url = filename;
		if (StringUtils.isBlank(url)) {
			throw new WikiException(new WikiMessage("upload.error.filename"));
		}
		// file is appended with a timestamp of DDHHMMSS
		GregorianCalendar cal = new GregorianCalendar();
		if (date != null) {
			cal.setTime(date);
		}
		String day = Integer.toString(cal.get(Calendar.DAY_OF_MONTH));
		if (day.length() == 1) {
			day = "0" + day;
		}
		String hour = Integer.toString(cal.get(Calendar.HOUR_OF_DAY));
		if (hour.length() == 1) {
			hour = "0" + hour;
		}
		String minute = Integer.toString(cal.get(Calendar.MINUTE));
		if (minute.length() == 1) {
			minute = "0" + minute;
		}
		String second = Integer.toString(cal.get(Calendar.SECOND));
		if (second.length() == 1) {
			second = "0" + second;
		}
		String suffix = "-" + day + hour + minute + second;
		int pos = url.lastIndexOf('.');
		url = (pos == -1) ? url + suffix : url.substring(0, pos) + suffix + url.substring(pos);
		// now pre-pend the file system directory
		// subdirectory is composed of vwiki/year/month
		String year = Integer.toString(cal.get(Calendar.YEAR));
		String month = Integer.toString(cal.get(Calendar.MONTH) + 1);
		String subdirectory = "/" + virtualWiki + "/" + year + "/" + month;
		File directory = new File(Environment.getValue(Environment.PROP_FILE_DIR_FULL_PATH), subdirectory);
		if (!directory.exists() && !directory.mkdirs()) {
			throw new WikiException(new WikiMessage("upload.error.directorycreate", directory.getAbsolutePath()));
		}
		return subdirectory + "/" + url;
	}

	/**
	 * Given an image file name, generate the appropriate topic name for the image.
	 */
	public static String generateFileTopicName(String virtualWiki, String filename) {
		String topicName = Namespace.namespace(Namespace.FILE_ID).getLabel(virtualWiki) + Namespace.SEPARATOR;
		topicName += Utilities.decodeAndEscapeTopicName(filename, true);
		return topicName;
	}

	/**
	 * Given a virtualWiki and WikiFIle that correspond to an existing image,
	 * return the WikiImage object.  In addition, if the image metadata specifies
	 * a max width or max height greater than zero then a resized version of the
	 * image may be created.
	 *
	 * @param wikiFile Given a WikiFile object, use it to initialize a
	 *  WikiImage object.
	 * @param imageMetadata The maximum width or height for the initialized
	 *  WikiImage object.  Setting this value to 0 or less will cause the
	 *  value to be ignored.
	 * @return An initialized WikiImage object.
	 * @throws IOException Thrown if an error occurs while initializing the
	 *  WikiImage object.
	 */
	private static WikiImage initializeImage(WikiFile wikiFile, ImageMetadata imageMetadata) throws DataAccessException, IOException {
		if (wikiFile == null) {
			throw new IllegalArgumentException("wikiFile may not be null");
		}
		WikiImage wikiImage = new WikiImage(wikiFile);
		// get the size of the original (unresized) image
		ImageDimensions originalDimensions = ImageUtil.retrieveFromCache(wikiImage);
		if (originalDimensions == null) {
			File file = new File(Environment.getValue(Environment.PROP_FILE_DIR_FULL_PATH), wikiImage.getUrl());
			originalDimensions = ImageUtil.retrieveImageDimensions(file);
			if (originalDimensions == null) {
				throw new IllegalArgumentException("Invalid image: " + wikiImage.getUrl());
			}
			addToCache(wikiImage, originalDimensions.getWidth(), originalDimensions.getHeight());
		}
		if (!imageMetadata.getAllowEnlarge() && imageMetadata.getMaxWidth() > originalDimensions.getWidth() && imageMetadata.getMaxHeight() > originalDimensions.getHeight()) {
			imageMetadata.setMaxWidth(originalDimensions.getWidth());
			imageMetadata.setMaxHeight(originalDimensions.getHeight());
		}
		// determine the width & height of scaled image (if needed)
		ImageDimensions scaledDimensions = calculateScaledDimensions(originalDimensions, imageMetadata.getMaxWidth(), imageMetadata.getMaxHeight());
		wikiImage.setWidth(scaledDimensions.getWidth());
		wikiImage.setHeight(scaledDimensions.getHeight());
		// return an appropriate WikiImage object with URL to the scaled image, proper width, and proper height
		ImageDimensions incrementalDimensions = calculateIncrementalDimensions(wikiImage, originalDimensions, scaledDimensions);
		String url = buildImagePath(wikiImage.getUrl(), originalDimensions.getWidth(), incrementalDimensions.getWidth());
		wikiImage.setUrl(url);
		return wikiImage;
	}

	/**
	 * Utility method for determining if a file name corresponds to a file type that is allowed
	 * for this wiki instance.
	 *
	 * @param filename The file name.
	 * @return <code>true</code> if the file type has not been blacklisted and is allowed for upload.
	 */
	public static boolean isFileTypeAllowed(String filename) {
		String extension = FilenameUtils.getExtension(filename);
		int blacklistType = Environment.getIntValue(Environment.PROP_FILE_BLACKLIST_TYPE);
		if (blacklistType == WikiBase.UPLOAD_ALL) {
			return true;
		}
		if (blacklistType == WikiBase.UPLOAD_NONE) {
			return false;
		}
		if (StringUtils.isBlank(extension)) {
			// FIXME - should non-extensions be represented in the whitelist/blacklist?
			return true;
		}
		extension = extension.toLowerCase();
		List list = WikiUtil.retrieveUploadFileList();
		if (blacklistType == WikiBase.UPLOAD_BLACKLIST) {
			return !list.contains(extension);
		}
		if (blacklistType == WikiBase.UPLOAD_WHITELIST) {
			return list.contains(extension);
		}
		return false;
	}

	/**
	 * Given a File object, determine if the file is an image or if it is some
	 * other type of file.
	 *
	 * @param file The File object for the file that is being examined.
	 * @return Returns <code>true</code> if the file is an image object.
	 */
	public static boolean isImage(File file) {
		try {
			return (ImageUtil.retrieveImageDimensions(file) != null);
		} catch (IOException x) {
			return false;
		}
	}

	/**
	 * Given a file that corresponds to an existing image, return a
	 * BufferedImage object.
	 */
	private static BufferedImage loadImage(File file) throws IOException {
		if (!file.exists()) {
			throw new FileNotFoundException("File does not exist: " + file.getAbsolutePath());
		}
		// use a FileInputStream and make sure it gets closed to prevent unclosed file
		// errors on some operating systems
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			BufferedImage image = ImageIO.read(fis);
			if (image == null) {
				throw new IOException("JDK is unable to process image file, possibly indicating file corruption: " + file.getAbsolutePath());
			}
			return image;
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {}
			}
		}
	}

	/**
	 * Convenience method that returns a scaled instance of the provided BufferedImage. Taken
	 * from http://today.java.net/pub/a/today/2007/04/03/perils-of-image-getscaledinstance.html.
	 * This method never resizes by more than 50% since resizing by more than that amount
	 * causes quality issues.
	 *
	 * @param imageFile The file path for the original image to be scaled.
	 * @param targetWidth the desired width of the scaled instance in pixels.
	 * @param targetHeight the desired height of the scaled instance in pixels.
	 * @return a scaled version of the original {@code BufferedImage}
	 */
	public static BufferedImage resizeImage(File imageFile, int targetWidth, int targetHeight) throws IOException {
		long start = System.currentTimeMillis();
		BufferedImage img = ImageUtil.loadImage(imageFile);
		int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
		BufferedImage ret = img;
		BufferedImage tmp;
		Graphics2D g2;
		int w = img.getWidth();
		int h = img.getHeight();
		do {
			if (w > targetWidth) {
				w /= 2;
				if (w < targetWidth) {
					w = targetWidth;
				}
			}
			if (h > targetHeight) {
				h /= 2;
				if (h < targetHeight) {
					h = targetHeight;
				}
			}
			tmp = new BufferedImage(w, h, type);
			g2 = tmp.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g2.drawImage(ret, 0, 0, w, h, null);
			g2.dispose();
			ret = tmp;
		} while (w != targetWidth || h != targetHeight);
		if (logger.isFineEnabled()) {
			long current = System.currentTimeMillis();
			String message = "Image resize time (" + ((current - start) / 1000.000) + " s), dimensions: " + targetWidth + "x" + targetHeight + " for file: " + imageFile.getAbsolutePath();
			logger.fine(message);
		}
		return ret;
	}

	/**
	 * Determine if image information is available in the cache.  If so return it,
	 * otherwise return <code>null</code>.
	 */
	private static ImageDimensions retrieveFromCache(WikiImage wikiImage) throws DataAccessException {
		String key = wikiImage.getVirtualWiki() + "/" + wikiImage.getUrl();
		Element cachedDimensions = WikiCache.retrieveFromCache(CACHE_IMAGE_DIMENSIONS, key);
		return (cachedDimensions != null) ? (ImageDimensions)cachedDimensions.getObjectValue() : null;
	}

	/**
	 * Given a file name that might correspond to an absolute URL, strip any directories
	 * and convert spaces in the name to underscores.
	 *
	 * @param filename The file name (path) to be sanitized.
	 * @return A sanitized version of the file name.
	 */
	public static String sanitizeFilename(String filename) {
		if (StringUtils.isBlank(filename)) {
			return null;
		}
		// some browsers set the full path, so strip to just the file name
		filename = FilenameUtils.getName(filename);
		filename = StringUtils.replace(filename.trim(), " ", "_");
		return filename;
	}

	/**
	 * Retrieve image dimensions.  This method simply reads headers so it should perform
	 * relatively fast.
	 */
	private static ImageDimensions retrieveImageDimensions(File imageFile) throws IOException {
		ImageInputStream iis = null;
		ImageDimensions dimensions = null;
		ImageReader reader = null;
		try {
			iis = ImageIO.createImageInputStream(imageFile);
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if (readers.hasNext()) {
				reader = readers.next();
				reader.setInput(iis, true);
				dimensions = new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
			}
		} finally {
			if (reader != null) {
				reader.dispose();
			}
			if (iis != null) {
				try {
					iis.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
		return dimensions;
	}

	/**
	 * Save an image to a specified file.
	 */
	private static void saveImage(BufferedImage image, File file) throws IOException {
		String filename = file.getName();
		int pos = filename.lastIndexOf('.');
		if (pos == -1 || (pos + 1) >= filename.length()) {
			throw new IOException("Unknown image file type " + filename);
		}
		String imageType = filename.substring(pos + 1);
		File imageFile = new File(file.getParent(), filename);
		// use a FileOutputStream and make sure it gets closed to prevent unclosed file
		// errors on some operating systems
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(imageFile);
			boolean result = ImageIO.write(image, imageType, imageFile);
			if (!result) {
				throw new IOException("No appropriate writer found when writing image: " + filename);
			}
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {}
			}
		}
	}

	/**
	 *
	 */
	public static Topic writeImageTopic(String virtualWiki, String topicName, String contents, WikiUser user, boolean isImage, String ipAddress) throws DataAccessException, ParserException, WikiException {
		Topic topic = WikiBase.getDataHandler().lookupTopic(virtualWiki, topicName, false, null);
		int charactersChanged = 0;
		if (topic == null) {
			topic = new Topic(virtualWiki, topicName);
			topic.setTopicContent(contents);
			charactersChanged = StringUtils.length(contents);
		}
		if (isImage) {
			topic.setTopicType(TopicType.IMAGE);
		} else {
			topic.setTopicType(TopicType.FILE);
		}
		TopicVersion topicVersion = new TopicVersion(user, ipAddress, contents, topic.getTopicContent(), charactersChanged);
		topicVersion.setEditType(TopicVersion.EDIT_UPLOAD);
		ParserOutput parserOutput = ParserUtil.parserOutput(topic.getTopicContent(), virtualWiki, topicName);
		WikiBase.getDataHandler().writeTopic(topic, topicVersion, parserOutput.getCategories(), parserOutput.getLinks());
		return topic;
	}

	/**
	 * Add/Update a WikiFile record, and add a WikiFileVersion record.
	 *
	 * @param topic The Topic record corresponding to this WikiFile.
	 * @param wikiFileVersion A skeleton WikiFileVersion record.  Most of the values of this
	 *  record will be populated from other parameters passed to this method, but fields
	 *  such as uploadComment should be populated prior to calling this method.
	 * @param user The user who is creating the file record, or <code>null</code> if the user
	 *  creating the file record is anonymous.
	 * @param ipAddress The IP address of the user creating the file record.
	 * @param filename The path on the filesystem relative to the file upload root for the
	 *  file version being created.
	 * @param url The relative URL for the file version being created.
	 * @param contentType The MIME type of the file version record being created.  For
	 *  example, "image/jpeg".
	 * @param fileSize The size of the file version record in bytes.
	 * @return The new or updated WikiFile record.
	 */
	public static WikiFile writeWikiFile(Topic topic, WikiFileVersion wikiFileVersion, WikiUser user, String ipAddress, String filename, String url, String contentType, long fileSize) throws DataAccessException, WikiException {
		wikiFileVersion.setAuthorDisplay(ipAddress);
		Integer authorId = null;
		if (user != null && user.getUserId() > 0) {
			authorId = user.getUserId();
		}
		wikiFileVersion.setAuthorId(authorId);
		WikiFile wikiFile = WikiBase.getDataHandler().lookupWikiFile(topic.getVirtualWiki(), topic.getName());
		if (wikiFile == null) {
			wikiFile = new WikiFile();
			wikiFile.setVirtualWiki(topic.getVirtualWiki());
		}
		wikiFile.setFileName(filename);
		wikiFile.setUrl(url);
		wikiFileVersion.setUrl(url);
		wikiFileVersion.setMimeType(contentType);
		wikiFile.setMimeType(contentType);
		wikiFileVersion.setFileSize(fileSize);
		wikiFile.setFileSize(fileSize);
		wikiFile.setTopicId(topic.getTopicId());
		WikiBase.getDataHandler().writeFile(wikiFile, wikiFileVersion);
		return wikiFile;
	}
}
