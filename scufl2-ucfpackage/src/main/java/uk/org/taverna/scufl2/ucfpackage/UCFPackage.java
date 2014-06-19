package uk.org.taverna.scufl2.ucfpackage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.purl.wf4ever.robundle.Bundle;
import org.purl.wf4ever.robundle.Bundles;
import org.purl.wf4ever.robundle.manifest.Manifest;
import org.purl.wf4ever.robundle.manifest.PathMetadata;
import org.purl.wf4ever.robundle.utils.RecursiveDeleteVisitor;
import org.purl.wf4ever.robundle.utils.TemporaryFiles;
import org.w3c.dom.Document;

public class UCFPackage implements Cloneable {
	private static Logger logger = Logger.getLogger(UCFPackage.class.getName());
	private static final URI VERSION_BASE = URI.create("http://ns.taverna.org.uk/2010/scufl2/");
	private static final String CONTAINER_XML = "META-INF/container.xml";
	public static final String MIME_BINARY = "application/octet-stream";
	public static final String MIME_TEXT_PLAIN = "text/plain";
	public static final String MIME_TEXT_XML = "text/xml";
	public static final String MIME_RDF = "application/rdf+xml";
	public static final String MIME_EPUB = "application/epub+zip";
	public static final String MIME_WORKFLOW_BUNDLE = "application/vnd.taverna.workflow-bundle";	

    private Bundle bundle;
    

	public UCFPackage() throws IOException {
		try {
		    bundle = Bundles.createBundle();
			//odfPackage = OdfPackage.create();
		    bundle.getManifest().populateFromBundle();
		    bundle.getManifest().writeAsODFManifest();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("Could not create empty UCF Package", e);
		}
		Bundles.setMimeType(bundle, MIME_EPUB);
		// odfPackage.setMediaType(MIME_EPUB);
	}

	public UCFPackage(File containerFile) throws IOException {
		open(containerFile);
	}

	protected void open(File containerFile) throws IOException {
	    bundle = Bundles.openBundleReadOnly(containerFile.toPath());
	}

	public UCFPackage(InputStream inputStream) throws IOException {
		open(inputStream);
	}

	protected UCFPackage(Bundle bundle) throws IOException {
	    this.bundle = bundle;
    }

    protected void open(InputStream inputStream) throws IOException {
		try {
		    Path bundlePath = TemporaryFiles.temporaryBundle();
		    Files.copy(inputStream, bundlePath);
		    bundle = Bundles.openBundle(bundlePath);
		    bundle.setDeleteOnClose(true);
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(
					"Could not load UCF Package from input stream", e);
		}
	}

	public String getPackageMediaType() {
	    try {
            return Bundles.getMimeType(bundle);
        } catch (IOException e) {
            return MIME_WORKFLOW_BUNDLE;
        }
	}

	public void setPackageMediaType(String mediaType) {
	    try {
            Bundles.setMimeType(bundle, mediaType);
        } catch (IOException e) {
            throw new RuntimeException("Can't set media type", e);
        }
	}

	public void save(File packageFile) throws IOException {
	    Path source = bundle.getSource();	    
	    boolean deleteOnClose = bundle.isDeleteOnClose();	 
	    bundle.setDeleteOnClose(false);
	    Bundles.closeAndSaveBundle(bundle, packageFile.toPath());
	    // Re-open the original source (usually a tmpfile)
	    bundle = Bundles.openBundle(source);
	    bundle.setDeleteOnClose(deleteOnClose);
	}


	public void addResource(String stringValue, String path, String mediaType)
			throws IOException {	    
	    Path bundlePath = writableBundlePath(path);
	    Bundles.setStringValue(bundlePath, stringValue);
	    Manifest manifest = bundle.getManifest();
	    manifest.getAggregation(bundlePath).setMediatype(mediaType);
	}

	public void addResource(byte[] bytesValue, String path, String mediaType)
			throws IOException {
		
	    Path bundlePath = writableBundlePath(path);
	    Files.write(bundlePath, bytesValue);
        Manifest manifest = bundle.getManifest();
        manifest.getAggregation(bundlePath).setMediatype(mediaType);
	}

    private Path writableBundlePath(String path) {
        Path bundlePath = bundle.getRoot().resolve(path);
        if (bundlePath.getParent() != null) {
            try {
                Files.createDirectories(bundlePath.getParent());
            } catch (IOException e) {
                throw new RuntimeException("Could not create parent directories of " + path, e);
            }
        }
        return bundlePath;
    }

	@Deprecated
	public void addResource(Document document, String path, String mediaType)
			throws IOException {

        Path bundlePath = writableBundlePath(path);
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer;
        try {
            transformer = tFactory.newTransformer();
        } catch (TransformerConfigurationException e) {
            throw new IOException("Can't create XML transformer to save "
                    + path, e);
        }

        DOMSource source = new DOMSource(document);
        try (OutputStream outStream = Files.newOutputStream(bundlePath)) {
            StreamResult result = new StreamResult(outStream);
            transformer.transform(source, result);
        } catch (TransformerException e) {
            throw new IOException("Can't save XML to " + path, e);
        }
	}

	public void addResource(InputStream inputStream, String path,
			String mediaType) throws IOException {
	    Path bundlePath = writableBundlePath(path);
	    Files.copy(inputStream, bundlePath);
        Manifest manifest = bundle.getManifest();
        manifest.getAggregation(bundlePath).setMediatype(mediaType);
	}

	public void addResource(URI uri, String path, String mediaType)
			throws IOException {	   
	    Path bundlePath = writableBundlePath(path);
        Bundles.setReference(bundlePath, uri);
        Manifest manifest = bundle.getManifest();
        manifest.getAggregation(bundlePath).setMediatype(mediaType);
	}

	public String getResourceAsString(String path) throws IOException {
	    Path bundlePath = bundle.getRoot().resolve(path);
	    if (! Files.exists(bundlePath)) { 
	    	throw new FileNotFoundException("Can't find: " + bundlePath);
	    }
	    if (! Files.isRegularFile(bundlePath)) { 
	    	throw new IOException("Not a regular file: " + bundlePath);
	    }
	    return Bundles.getStringValue(bundlePath);
	}

	public byte[] getResourceAsBytes(String path) throws IOException {
	    Path bundlePath = bundle.getRoot().resolve(path);
        return Files.readAllBytes(bundlePath);
	}

	public InputStream getResourceAsInputStream(String path) throws IOException {
	    Path bundlePath = bundle.getRoot().resolve(path);
	    if (! Files.isReadable(bundlePath)) { 
	        throw new IOException("Can't read " + bundlePath);
	    }
	    return Files.newInputStream(bundlePath);
	}

	public Map<String, ResourceEntry> listResources() {
		return listResources("", false);
	}

	public Map<String, ResourceEntry> listResources(String folderPath) {
		return listResources(folderPath, false);
	}

	protected Map<String, ResourceEntry> listResources(String folderPath,
			final boolean recursive) {
	    final Path bundlePath = bundle.getRoot().resolve(folderPath);
	    final List<Path> reserved = Arrays.asList(bundle.getRoot().resolve("META-INF/"), 
	            bundle.getRoot().resolve(".ro/"),
	            bundle.getRoot().resolve("mimetype")
	            );
	    
	    final HashMap<String, ResourceEntry> content = new HashMap<String, ResourceEntry>();
	    FileVisitor<Path> visitor = new FileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir,
					BasicFileAttributes attrs) throws IOException {
				if (reserved.contains(dir)) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				if (! dir.equals(bundlePath)) {
					storePath(dir);					
				}
				return recursive ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
			}

			private void storePath(Path path) {
				String pathStr = bundlePath.relativize(path).toString();
	            content.put(pathStr, new ResourceEntry(path));
			}

			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				if (reserved.contains(file)) {
					return FileVisitResult.CONTINUE;
				}
				storePath(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc)
					throws IOException {
				logger.log(Level.WARNING, "Could not visit " + file, exc);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
					throws IOException {
				return FileVisitResult.CONTINUE;
			}
		}; 

		try {
			Files.walkFileTree(bundlePath, visitor);
	    } catch (IOException e) {
            throw new RuntimeException("Can't list resources of " + folderPath, e);
        }
		return content;
	}

	public void removeResource(String path) {
	    Path bundlePath = bundle.getRoot().resolve(path);
	    try {
            RecursiveDeleteVisitor.deleteRecursively(bundlePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not delete " + path + " or its children", e);
        }
	}

	public class ResourceEntry {
        
        private Path path;

		public ResourceEntry(Path path) {
            this.path = path;           
        }

        public String getPath() {
			return path.toString().replaceFirst("^/", "");
		}

		public long getSize() {
			try {
                return Files.size(path);
            } catch (IOException e) {
               throw new RuntimeException("Can't determine size of " + path, e);
            }
		}

		public String getMediaType() {
			try {
                return bundle.getManifest().getAggregation(path).getMediatype();
            } catch (IOException e) {
                throw new RuntimeException("Can't get media type for " + path, e);
            }
		}

		public boolean isFolder() {
			return Files.isDirectory(path);
		}

		public UCFPackage getUcfPackage() {
			return UCFPackage.this;
		}
		
		@Override
		public String toString() {
		    return getPath();
		};
		
		@Override
		public boolean equals(Object obj) {
			if (! (obj instanceof ResourceEntry)) {
				return false;
			}
			ResourceEntry other = (ResourceEntry) obj;
			
			if (! getUcfPackage().equals(other.getUcfPackage())) {
				return false;
			}
			return path.equals(other.path);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getUcfPackage().hashCode();
			result = prime * result + ((path == null) ? 0 : path.hashCode());
			return result;
		}

        public String getVersion() {
            URI conformsTo;
            try {
                conformsTo = bundle.getManifest().getAggregation(path).getConformsTo();
            } catch (IOException e) {
                throw new RuntimeException("Can't look up version for " + path, e);
            }
            if (conformsTo != null) {                
                URI version = VERSION_BASE.relativize(conformsTo);
                if (!version.isAbsolute()) {
                    return version.toString();
                }
            }
            return null;
            
        }
	}

	public Map<String, ResourceEntry> listAllResources() {
		return listResources("/", true);
	}
	
	public void setRootFile(String path) {
		bundle.addRootFile(bundle.getPath(path));
	}

	@SuppressWarnings("rawtypes")
	public void setRootFile(String path, String version) {
		setRootFile(path);
		if (version == null) {
			return;
		}
		try {
			PathMetadata metadata = bundle.getManifest().getAggregation(bundle.getPath(path));
			metadata.setConformsTo(VERSION_BASE.resolve(version));
		} catch (IOException e) {
			throw new RuntimeException("Could not set root file: "+ path, e);
		}		
	}

	public List<ResourceEntry> getRootFiles() {
		List<ResourceEntry> files = new ArrayList<>();
		for (Path path : bundle.getRootFiles()) {
			if (Files.exists(path)) { 
				files.add(new ResourceEntry(path));
			}
		}
		return files;
	}
	

	public ResourceEntry getResourceEntry(String path) {
	    Path bundlePath = bundle.getRoot().resolve(path);
	    if (Files.exists(bundlePath)) {
	    	return new ResourceEntry(bundlePath);
	    }
	    return null;
	}

	@SuppressWarnings("rawtypes")
	public void unsetRootFile(String path) {
		bundle.removeAsRootFile(bundle.getPath(path));
	}

	public void save(OutputStream output) throws IOException {
        Path source = bundle.getSource();
        boolean deleteOnClose = bundle.isDeleteOnClose();
        bundle.setDeleteOnClose(false);
        Bundles.closeBundle(bundle);        
        Files.copy(source, output);
        
        // Re-open the original source (usually a tmpfile)
        bundle = Bundles.openBundle(source);
        bundle.setDeleteOnClose(deleteOnClose);
	}

	public OutputStream addResourceUsingOutputStream(String path,
			String mediaType) throws IOException {
		if (path.equals(CONTAINER_XML)) {
			throw new IllegalArgumentException("Can't add " + CONTAINER_XML
					+ " using OutputStream");
			// as we need to parse it after insertion
		}
		Path bundlePath = writableBundlePath(path);
	    return Files.newOutputStream(bundlePath);			
	}

	@Override
	public UCFPackage clone() {	    
	        Path source = bundle.getSource();       
	        boolean deleteOnClose = bundle.isDeleteOnClose();       
	        bundle.setDeleteOnClose(false);
	        try {
                Bundles.closeBundle(bundle);
            } catch (IOException e) {
                throw new RuntimeException("Could not save bundle to " + source, e);
            }
	        
	        Bundle clonedBundle;
            try {
                clonedBundle = Bundles.openBundleReadOnly(source);
            } catch (IOException e) {
                throw new RuntimeException("Could not copy bundle from " + source, e);
            }
	        
	        // Re-open the original source (usually a tmpfile)
	        try {
                bundle = Bundles.openBundle(source);
                bundle.setDeleteOnClose(deleteOnClose);
                return new UCFPackage(clonedBundle);
            } catch (IOException e) {
                throw new RuntimeException("Could not re-open from " + source, e);
            }
	}

	private PipedInputStream copyToOutputStream(
			final PipedOutputStream outputStream) throws IOException {	    
		PipedInputStream inputStream = new PipedInputStream(outputStream);
		new Thread("Cloning " + this) {
			@Override
			public void run() {
				try {
					try {
						save(outputStream);
					} finally {
						outputStream.close();
					}
				} catch (IOException e) {
					logger.log(Level.INFO, "Could not save/close UCF package while cloning", e);
				}
			}
		}.start();
		return inputStream;
	}

    public String getRootFileVersion(String rootFile) {
        return getResourceEntry(rootFile).getVersion();
    }

}
