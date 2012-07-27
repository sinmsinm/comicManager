/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sling.jcr.contentloader.internal.readers;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.sling.jcr.contentloader.internal.ContentCreator;
import org.apache.sling.jcr.contentloader.internal.ContentReader;
import org.apache.sling.jcr.contentloader.internal.ImportProvider;
import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>ComicReader</code> TODO
 * 
 * 
 * @since 2.0
 * @author Alexandre Ballesté - alex.balleste at gmail.com
 * 
 */
public class ComicReader implements ContentReader {

	/*
	 * JCR and SLING content types
	 */

	private static final String NT_FOLDER = "nt:folder";
	private static final String SLING_FOLDER = "sling:Folder";
	private static final String SLING_ORDERED_FOLDER = "sling:OrderedFolder";
	private static final String SLING_RESOURCE_TYPE = "sling:resourceType";
	private static final String JCR_NAME = "jcr:name";
	private static final String BIN_FOLDER = "bin";
	private static final String THUMB_FOLDER = "thumb";
	private static final String COMIC_BIN_ISSUE = "comic-bin/issue";
	private static final String COMIC_BIN_PAGE = "comic-bin/page";
	private static final int THUMBNAIL_WITH = 75;
	private static final Map<String, String> supportedMimeTypes = new HashMap<String, String>();

	private static final int CBR_COMIC_TYPE = 0;
	private static final int CBZ_COMIC_TYPE = 1;

	final Logger logger = LoggerFactory.getLogger(getClass());

	public static final ImportProvider CBR_PROVIDER = new ImportProvider() {
		private ComicReader comicReader;

		public ContentReader getReader() {

			if (comicReader == null) {
				comicReader = new ComicReader(CBR_COMIC_TYPE);
			}
			return comicReader;
		}
	};

	public static final ImportProvider CBZ_PROVIDER = new ImportProvider() {
		private ComicReader comicReader;

		public ContentReader getReader() {
			if (comicReader == null) {
				comicReader = new ComicReader(CBZ_COMIC_TYPE);
			}
			return comicReader;
		}
	};

	private final int comicReaderType;

	public ComicReader(int comicReaderType) {
		this.comicReaderType = comicReaderType;

		/* Set up supported mime types for pages */
		supportedMimeTypes.put(".jpeg", "image/jpeg");
		supportedMimeTypes.put(".jpe", "image/jpeg");
		supportedMimeTypes.put(".jpg", "image/jpeg");
		supportedMimeTypes.put(".png", "image/png");
	}

	/**
	 * @see org.apache.sling.jcr.contentloader.internal.ContentReader#parse(java.net.URL,
	 *      org.apache.sling.jcr.contentloader.internal.ContentCreator)
	 */
	public void parse(java.net.URL url, ContentCreator creator)
			throws IOException, RepositoryException {
		parse(url.openStream(), creator);

	}

	/**
	 * @see org.apache.sling.jcr.contentloader.internal.ContentReader#parse(java.io.InputStream,
	 *      org.apache.sling.jcr.contentloader.internal.ContentCreator)
	 */
	public void parse(InputStream ins, ContentCreator creator)
			throws IOException, RepositoryException {

		switch (this.comicReaderType) {
		case CBR_COMIC_TYPE:
			parseCBR(ins, creator);
			break;
		case CBZ_COMIC_TYPE:
			parseCBZ(ins, creator);
			break;
		default:
			break;
		}

	}

	private void parseCBZ(InputStream ins, ContentCreator creator)
			throws IOException, RepositoryException {
		try {
			logger.debug("Parsing a cbz file");

			creator.createNode(null, SLING_FOLDER, null);
			creator.createProperty(SLING_RESOURCE_TYPE, COMIC_BIN_ISSUE);

			final ZipInputStream zis = new ZipInputStream(ins);

			ZipEntry entry;

			do {

				entry = zis.getNextEntry();

				if (entry != null) {
					if (!entry.isDirectory()) {
						String name = entry.getName();
						String extension = null;
						String mimeType = null;

						int pos = name.lastIndexOf('/');

						if (pos == -1) {
							pos = 0;
						}

						name = name.substring(pos);

						int posExt = name.lastIndexOf(".");

						if (posExt > 0) {
							extension = name.substring(posExt);
							mimeType = supportedMimeTypes.get(extension
									.toLowerCase());

							if (mimeType != null) {

								creator.switchCurrentNode(name, SLING_FOLDER);
								creator.createProperty(JCR_NAME, name);
								creator.createProperty(SLING_RESOURCE_TYPE,
										COMIC_BIN_PAGE);

								CloseShieldInputStream csi = new CloseShieldInputStream(
										zis);
								ByteArrayOutputStream baos = new ByteArrayOutputStream();

								int len;
								byte[] buffer = new byte[1024];
								while ((len = csi.read(buffer)) > -1) {
									baos.write(buffer, 0, len);
								}
								baos.flush();

								InputStream largeImages = new ByteArrayInputStream(
										baos.toByteArray());
								InputStream thumbImages = new ByteArrayInputStream(
										baos.toByteArray());

								creator.createFileAndResourceNode(BIN_FOLDER,
										largeImages, mimeType, entry.getTime());

								creator.finishNode();
								creator.finishNode();
								createThumbnail(thumbImages, THUMBNAIL_WITH,
										mimeType, creator, extension);

								creator.finishNode();
							}
						}
					}
					zis.closeEntry();
				}

			} while (entry != null);

			logger.debug("Added all entries");

			creator.finishNode();
		} catch (Exception ex) {
			logger.error("Exception extracting zip file", ex);
		} finally {
			if (ins != null) {
				try {
					ins.close();
				} catch (IOException ignore) {
				}
			}
		}

	}

	private void parseCBR(InputStream ins, ContentCreator creator)
			throws IOException, RepositoryException {

		Archive arch = null;
		File tempFile = null;

		try {
			logger.debug("Parsing a rar file");
			creator.createNode(null, SLING_FOLDER, null);
			creator.createProperty(SLING_RESOURCE_TYPE, COMIC_BIN_ISSUE);

			/*
			 * Create a temporaly rar file from the inpuyStream in order to
			 * descompres with Archive class
			 */

			tempFile = File.createTempFile("tempcbrFile", ".tmp");

			OutputStream fout = null;

			try {
				fout = new BufferedOutputStream(new FileOutputStream(tempFile));
				ins = new BufferedInputStream(ins);

				int len = 8192;
				byte[] bytes = new byte[len];

				while ((len = ins.read(bytes, 0, len)) != -1) {
					fout.write(bytes, 0, len);
				}

				fout.flush();

			} catch (Exception ex) {
				logger.error("Got an error building the temporal cbr file", ex);
			} finally {

				if (fout != null) {
					fout.close();
				}

				if (ins != null) {
					ins.close();
				}

			}
			/* Create a new junrar Archive */

			try {
				logger.debug("Opening as rar archive");
				arch = new Archive(tempFile);
			} catch (Exception e) {
				logger.error("Error opening the rar file", e);
			}

			if (arch != null) {

				if (arch.isEncrypted()) {
					logger.warn("archive is encrypted cannot extreact");
					return;
				}

				FileHeader fh = null;

				while (true) {
					fh = arch.nextFileHeader();

					if (fh == null) {
						break;
					}

					if (fh.isEncrypted()) {
						logger.warn("file is encrypted cannot extract: "
								+ fh.getFileNameString());
						continue;
					}

					logger.info("Extracting: " + fh.getFileNameString());

					try {
						if (!fh.isDirectory()) {

							String name = null;
							String extension = null;
							String mimeType = null;

							if (fh.isFileHeader() && fh.isUnicode()) {
								name = fh.getFileNameW();
							} else {
								name = fh.getFileNameString();
							}

							name = name.replace("\\", "/");
							int pos = name.lastIndexOf('/');

							// if / not found then the name of files are on the
							// root
							if (pos == -1) {
								pos = 0;
							}

							name = name.substring(pos);

							int posExt = name.lastIndexOf(".");

							if (posExt > 0) {
								extension = name.substring(posExt);
								mimeType = supportedMimeTypes.get(extension
										.toLowerCase());

								if (mimeType != null) {

									InputStream impar = arch.getInputStream(fh);

									if (impar != null) {
										creator.switchCurrentNode(name,
												SLING_FOLDER);
										creator.createProperty(JCR_NAME, name);
										creator.createProperty(
												SLING_RESOURCE_TYPE,
												COMIC_BIN_PAGE);

										creator.createFileAndResourceNode(
												BIN_FOLDER,
												new CloseShieldInputStream(
														impar), mimeType,
												(new Date()).getTime());

										creator.finishNode();
										creator.finishNode();

										createThumbnail(
												new CloseShieldInputStream(arch
														.getInputStream(fh)),
												THUMBNAIL_WITH, mimeType,
												creator, extension);
										creator.finishNode();
									}
								}
							}
						}
					} catch (Exception ex) {
						logger.error("Exception extracting rar file", ex);
					}
				}
			}
			creator.finishNode();
		} catch (Exception mex) {
			logger.error("Error", mex);
		} finally {
			// finally delete the temp file
			tempFile.delete();
		}
	}

	private void createThumbnail(InputStream imageStream, int finalWidth,
			String mimeType, ContentCreator creator, String suffix)
			throws Exception {
		final File tmp = File
				.createTempFile(getClass().getSimpleName(), suffix);

		try {
			scale(imageStream, finalWidth, new FileOutputStream(tmp), suffix);

			// Create thumbnail node and set the mandatory properties

			creator.createFileAndResourceNode(THUMB_FOLDER,
					new CloseShieldInputStream(new FileInputStream(tmp)),
					mimeType, (new Date()).getTime());
			creator.finishNode();
			creator.finishNode();

		} catch (Exception ex) {
			logger.debug("error ", ex);
		} finally {
			if (tmp != null) {
				tmp.delete();
			}
		}

	}

	/*
	 * That code was extracted from an sling example ESPBlog
	 */
	private void scale(InputStream inputStream, int width,
			OutputStream outputStream, String suffix) throws IOException {
		if (inputStream == null) {
			throw new IOException("InputStream is null");
		}

		final BufferedImage src = ImageIO.read(inputStream);
		if (src == null) {
			final StringBuffer sb = new StringBuffer();
			for (String fmt : ImageIO.getReaderFormatNames()) {
				sb.append(fmt);
				sb.append(' ');
			}
			throw new IOException("Unable to read image, registered formats: "
					+ sb);
		}

		final double scale = (double) width / src.getWidth();

		int destWidth = width;
		int destHeight = new Double(src.getHeight() * scale).intValue();
		logger.debug("Generating thumbnail, w={}, h={}", destWidth, destHeight);
		BufferedImage dest = new BufferedImage(destWidth, destHeight,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dest.createGraphics();
		AffineTransform at = AffineTransform.getScaleInstance(
				(double) destWidth / src.getWidth(),
				(double) destHeight / src.getHeight());
		g.drawRenderedImage(src, at);
		ImageIO.write(dest, suffix.substring(1), outputStream);
	}

}