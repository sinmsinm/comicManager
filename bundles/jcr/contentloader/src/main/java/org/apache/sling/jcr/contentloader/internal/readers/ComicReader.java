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

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
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
 * @since 2.0
 * @author Alexandre Ballesté
 */
public class ComicReader implements ContentReader {

    private static final String NT_FOLDER = "nt:folder";

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




    /** Is this a cbz or cbr ? */
    private final int comicReaderType;

    public ComicReader(int comicReaderType) {
        this.comicReaderType = comicReaderType;
    }

    /**
     * @see org.apache.sling.jcr.contentloader.internal.ContentReader#parse(java.net.URL, org.apache.sling.jcr.contentloader.internal.ContentCreator)
     */
    public void parse(java.net.URL url, ContentCreator creator)
    		throws IOException, RepositoryException {
    	parse(url.openStream(), creator);
    }

	/**
	 * @see org.apache.sling.jcr.contentloader.internal.ContentReader#parse(java.io.InputStream, org.apache.sling.jcr.contentloader.internal.ContentCreator)
	 */
	public void parse(InputStream ins, ContentCreator creator)
			throws IOException, RepositoryException {

		switch (this.comicReaderType){
			case CBR_COMIC_TYPE:
				parseCBR(ins,creator);
				break;
			case CBZ_COMIC_TYPE:
				parseCBZ(ins,creator);
				break;
			default:
				break;

		}	
	}


	private void parseCBZ(InputStream ins, ContentCreator creator)
			throws IOException, RepositoryException {
	        try {
	            creator.createNode(null, NT_FOLDER, null);
	            final ZipInputStream zis = new ZipInputStream(ins);
	            ZipEntry entry;
	            do {
        	        entry = zis.getNextEntry();
                	if ( entry != null ) {
	                    if ( !entry.isDirectory() ) {
	                        String name = entry.getName();
       	                 int pos = name.lastIndexOf('/');
       	                 if ( pos != -1 ) {
                            creator.switchCurrentNode(name.substring(0, pos), NT_FOLDER);
                        }
                        creator.createFileAndResourceNode(name, new CloseShieldInputStream(zis), null, entry.getTime());
                        creator.finishNode();
                        creator.finishNode();
                        if ( pos != -1 ) {
                            creator.finishNode();
                        }
                    }
                    zis.closeEntry();
                }

            } while ( entry != null );
            creator.finishNode();
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
	
	Archive arch=null;

	try {
		creator.createNode(null, NT_FOLDER, null);

		/* Create a temporaly rar file from the inpuyStream in order to descompres with Archive */

		File tempFile = File.createTempFile("tempcbrFile", ".tmp");  
		tempFile.deleteOnExit(); 

		FileOutputStream fout = null;  

		try{  
			fout = new FileOutputStream(tempFile);  
			int c;  
 
			while ((c = ins.read()) != -1) {  
  	      			fout.write(c);  
		        }  

		}finally{  
		            if (ins != null) {  
        		        ins.close();  
		            }  

		       	    if (fout != null) {  
		                fout.close();  
        		    }  
		}
		/* Create a new junrar Archive */
		try {
			arch = new Archive(tempFile);
		} catch (Exception e) {
			logger.error("error",e);
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
					logger.warn("file is encrypted cannot extract: "+ fh.getFileNameString());
					continue;	
				}
			
				logger.info("extracting: " + fh.getFileNameString());
			
				try {
					if (!fh.isDirectory()) {

						String name = null;
						if (fh.isFileHeader() && fh.isUnicode()) {
							name = fh.getFileNameW();
						} else {
							name = fh.getFileNameString();
						}
	
						logger.debug ("name of file" + name);

						name= name.replace("\\","/");

						logger.debug ("name of file after replacing" + name);
	

			                        int pos = name.lastIndexOf('/');
						
						logger.debug ("position ok  " +  pos);
                		
					        if ( pos != -1 ) {
					 	    logger.debug ("before swithcing node " + name.substring(0, pos));
		        	                    creator.switchCurrentNode(name.substring(0, pos), NT_FOLDER);
						    logger.debug ("After switching node");
                			        }
						logger.debug ("before create node" + name);

						InputStream impar = arch.getInputStream (fh);
						
						if (impar!= null){
								logger.debug ("El input stream no es null");
						                creator.createFileAndResourceNode(name, new CloseShieldInputStream(impar), null, (new Date()).getTime());		
							logger.debug ("after create the node");
					
						}else{
							logger.debug("El inpar es null");

						}
								logger.debug ("before finishing node1");
				                        creator.finishNode();

						logger.debug ("before finishing node2");

                			        creator.finishNode();

						logger.debug ("after clossing 1 and 2");
	
					        if ( pos != -1 ) {
							logger.debug ("before finihing node3");
        			               	    creator.finishNode();
							logger.debug ("after close node 3");
	               	 		        }
					}
		                }catch (Exception ex){
					logger.error ("Exception extracting ", ex);
				}
		            } 
			}		
			logger.debug ("Just out the bucle");

        	 creator.finishNode();
			logger.debug ("Just after finish node out while");
	        }catch (Exception ex){
			logger.error ("cached a error out of bucle",ex);
		 }finally {

		}
	}
    
}
