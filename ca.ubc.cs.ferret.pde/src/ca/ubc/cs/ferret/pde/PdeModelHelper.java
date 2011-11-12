package ca.ubc.cs.ferret.pde;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IRegistryChangeEvent;
import org.eclipse.core.runtime.IRegistryChangeListener;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.pde.core.IIdentifiable;
import org.eclipse.pde.core.plugin.IFragmentModel;
import org.eclipse.pde.core.plugin.IPluginAttribute;
import org.eclipse.pde.core.plugin.IPluginElement;
import org.eclipse.pde.core.plugin.IPluginExtension;
import org.eclipse.pde.core.plugin.IPluginExtensionPoint;
import org.eclipse.pde.core.plugin.IPluginImport;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.IPluginObject;
import org.eclipse.pde.core.plugin.ISharedPluginModel;
import org.eclipse.pde.internal.core.IPluginModelListener;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.PDEState;
import org.eclipse.pde.internal.core.PluginModelDelta;
import org.eclipse.pde.internal.core.TargetPlatformHelper;
import org.eclipse.pde.internal.core.ibundle.IBundlePluginModelBase;
import org.eclipse.pde.internal.core.natures.PDE;
import org.eclipse.pde.internal.core.plugin.ExternalFragmentModel;
import org.eclipse.pde.internal.core.plugin.ExternalPluginModel;
import org.eclipse.pde.internal.core.plugin.ExternalPluginModelBase;
import org.eclipse.pde.internal.core.plugin.PluginHandler;

import ca.ubc.cs.ferret.FerretFatalError;
import ca.ubc.cs.ferret.FerretPlugin;
import ca.ubc.cs.ferret.references.AbstractReference;
import ca.ubc.cs.ferret.references.FileReference;
import ca.ubc.cs.ferret.references.ZipEntryReference;

public class PdeModelHelper implements IPluginModelListener, IRegistryChangeListener {
	protected static PdeModelHelper singleton;

	public Map<String,IPluginModelBase> models = null;
	public Map<String,MultiMap<IPluginExtension,IPluginObject>> extensions = null;

	protected PdeModelHelper() {
	}

	public static PdeModelHelper getDefault() {
		if(singleton == null) {
			singleton = new PdeModelHelper();
			PDECore.getDefault().getModelManager().addPluginModelListener(singleton);
			Platform.getExtensionRegistry().addRegistryChangeListener(singleton);
		}
		return singleton;
	}

	public static void stop() {
		if(singleton == null) { return; }
		PDECore.getDefault().getModelManager().removePluginModelListener(singleton);
		Platform.getExtensionRegistry().removeRegistryChangeListener(singleton);
		singleton.reset();
		singleton = null;
	}

	public void reset() {
		models = null;
		extensions = null;
	}
	
	public void modelsChanged(PluginModelDelta delta) {
		reset();
	}

	public void registryChanged(IRegistryChangeEvent event) {
		reset();
	}

	/**
	 * Return URLs to projects in the workspace that have a manifest file (MANIFEST.MF
	 * or plugin.xml)
	 * COPIED FROM org.eclipse.pde.internal.core.WorkspacePluginModelManager.getPluginPaths()
	 * @return an array of URLs to workspace plug-ins
	 */
	protected URL[] getWorkspacePluginPaths(PDEState state) {
		ArrayList<URL> list = new ArrayList<URL>();
		for(IPluginModelBase pmb : state.getWorkspaceModels()) {
			try {
				list.add(new File(pmb.getInstallLocation()).toURL());
			} catch (MalformedURLException e) {}
		}
//		IProject[] projects = PDECore.getWorkspace().getRoot().getProjects();
//		for (int i = 0; i < projects.length; i++) {
//			if (WorkspacePluginModelManager.isPluginProject(projects[i])) {			
//				try {
//					IPath path = projects[i].getLocation();
//					if (path != null) {
//						list.add(path.toFile().toURL());
//					}
//				} catch (MalformedURLException e) {
//				}
//			}
//		}
		return (URL[])list.toArray(new URL[list.size()]);
	}

	protected URL[] getTargetPluginPaths(PDEState state) {
		// was: ConfiguratorUtils.getCurrentPlatformConfiguration().getPluginPath(),
		// except that doesn't deal with plugins overridden from the local workspace 
		ArrayList<URL> list = new ArrayList<URL>();
		for(IPluginModelBase pmb : state.getTargetModels()) {
			try {
				list.add(new File(pmb.getInstallLocation()).toURL());
			} catch (MalformedURLException e) {}
		}
		return (URL[])list.toArray(new URL[list.size()]);

	}
	
	protected void verifyModelCaches() {
		// models = null; extensions = null;
		if(models != null && extensions != null) { return; }
		Map<String,IPluginModelBase> models = new HashMap<String, IPluginModelBase>();
		Map<String,MultiMap<IPluginExtension,IPluginObject>> extensions = 
			new HashMap<String,MultiMap<IPluginExtension,IPluginObject>>();

		// We create our own state to ensure that extensions are properly resolved
		// Pieced together from PDECore.findPluginInHost(String), TargetPlatformHelper,
		// WorkspacePluginModelManager, PDEState
//		PDEState state = new PDEState(
//				getWorkspacePluginPaths(TargetPlatformHelper.getPDEState()),
//				getTargetPluginPaths(TargetPlatformHelper.getPDEState()),
//				true, new NullProgressMonitor());
		// Following doesn't work: doesn't resolve extensions
//		PDEState state = TargetPlatformHelper.getPDEState();
		// TargetPlatformHelper.getPDEState().getTargetModels()
		for(IPluginModelBase pluginModel : PDECore.getDefault().getModelManager().getExternalModels()) {
			String pluginId = getPluginId(pluginModel);
			models.put(pluginId, pluginModel);
			MultiMap<IPluginExtension,IPluginObject> xm =
				loadExtensions(pluginId, pluginModel);
//			for(IPluginExtensionPoint extpt : pluginModel.getExtensions().getExtensionPoints()) {
//				if(!extpt.getFullId().startsWith(pluginId)) {
//					System.out.println(pluginId + ": extpt " + extpt.getId() + " has incorrect name?");
//				}
//			}
//			for(IPluginExtension ext : xm.keySet()) {
//				if(xm.get(ext).isEmpty()) {
//					System.out.println(pluginId + ": extension " + ext.getId() + " to " + ext.getPoint() + " has no children!");
//				}
//			}
			extensions.put(pluginId, xm);
		}
		
		for(IPluginModelBase pluginModel : PDECore.getDefault().getModelManager().getWorkspaceModels()) {
			String pluginId = getPluginId(pluginModel);
			models.put(pluginId, pluginModel);
			MultiMap<IPluginExtension,IPluginObject> xm =
				loadExtensions(pluginId, pluginModel);
//			for(IPluginExtensionPoint extpt : pluginModel.getExtensions().getExtensionPoints()) {
//				if(!extpt.getFullId().startsWith(pluginId)) {
//					System.out.println(pluginId + ": extpt " + extpt.getId() + " has incorrect name?");
//				}
//			}
//			for(IPluginExtension ext : xm.keySet()) {
//				if(xm.get(ext).isEmpty()) {
//					System.out.println(pluginId + ": extension " + ext.getId() + " to " + ext.getPoint() + " has no children!");
//				}
//			}
			extensions.put(pluginId, xm);
		}
		this.models = models;
		this.extensions = extensions;
	}
	
	public IPluginModelBase findPluginModel(String pluginId) {
		// return PDECore.getDefault().getModelManager().findModel(pluginId);
		verifyModelCaches();
		return models.get(pluginId);
	}

	public IPluginModelBase findPluginModel(IProject project) {
		try {
			if(!project.hasNature(PDE.PLUGIN_NATURE)) { return null; }
			IPluginModelBase pmb = PDECore.getDefault().getModelManager().findModel(project);
			if(pmb == null) { return null; }
			return findPluginModel(pmb.getPluginBase().getId());
		} catch(CoreException e) {
			return null;
		}
	}

	public IPluginModelBase findPluginModel(IProjectNature object) {
		return findPluginModel(object.getProject());
	}
		

	public Collection<IPluginModelBase> getActiveModels() {
		verifyModelCaches();
		return models.values();
	}

	public IPluginExtensionPoint findExtensionPoint(String pointId) {
		if(pointId == null) { return null; }
		int pointIndex = pointId.lastIndexOf('.');
		if (pointIndex < 0) { return null; }
		IPluginModelBase pmb = findPluginModel(pointId.substring(0, pointIndex));
		if (pmb == null) { return null; }
		for (IPluginExtensionPoint pep : getExtensionPoints(pmb)) {
			if (pointId.equals(getFullId(pep))) {
				return pep;
			}
		}
		return null;
	}

	public MultiMap<IPluginExtension,IPluginObject> getExtensions(IPluginExtensionPoint point) {
		verifyModelCaches();
		String pointId = getFullId(point);
		MultiMap<IPluginExtension,IPluginObject> xts =
			new MultiHashMap<IPluginExtension,IPluginObject>();
		for (MultiMap<IPluginExtension,IPluginObject> exts : extensions.values()) {
			for(IPluginExtension ext : exts.keySet()) {
				if (pointId.equals(ext.getPoint())) {
					xts.putAll(ext, exts.get(ext));
				}
			}
		}
		return xts;
	}

	public MultiMap<IPluginExtension,IPluginObject> getExtensions(IPluginModelBase pluginModel) {
		verifyModelCaches();
		String pluginId = getPluginId(pluginModel);
		if(pluginId == null) { return null; }
		MultiMap<IPluginExtension,IPluginObject> exts = extensions.get(pluginId);
		if(exts == null) { return null; }
		return exts;
	}

	public Collection<IPluginExtensionPoint>  getExtensionPoints(IPluginModelBase pluginModel) {
		verifyModelCaches();
		assert getPluginId(pluginModel) != null;
//		String pluginId = getPluginId(pluginModel);
//		if(pluginId == null) { return null; }
//		IExtensions exts = extensions.get(pluginId);
//		if(exts == null) { return null; }
		Collection<IPluginExtensionPoint> results = new HashSet<IPluginExtensionPoint>();
		for(IPluginExtensionPoint xp : pluginModel.getExtensions().getExtensionPoints()) {
			results.add(xp);
		}
		return results;
	}

	public String getFullId(IPluginExtensionPoint point) {
		String id = point.getFullId();
		if(id.indexOf('.') >= 0) { return id; }
		String pluginId = getPluginId(point.getModel());
		return pluginId + '.' + id;
	}

	public String getPluginId(ISharedPluginModel model) {
		if(model instanceof IIdentifiable) {
			String id = ((IIdentifiable)model).getId();
			if(id != null && id.length() > 0) { return id; }
		}
		if(model instanceof IPluginModelBase) {
			String id = ((IPluginModelBase)model).getPluginBase().getId();
			if(id != null && id.length() > 0) { return id; }
			if(((IPluginModelBase)model).getBundleDescription() != null) {
				id = ((IPluginModelBase)model).getBundleDescription().getSymbolicName();
			}
			if(id != null && id.length() > 0) { return id; }
		}
		IResource resource = model.getUnderlyingResource();
		if(resource != null) {
			IPluginModelBase modelBase = 
				PDECore.getDefault().getModelManager().findModel(resource.getProject());
			String id = modelBase.getPluginBase().getId();
			if(id != null && id.length() > 0) { return id; }
		}
		String installationLocation = model.getInstallLocation();
		for(IPluginModelBase modelBase : PDECore.getDefault().getModelManager().getExternalModels()) {
			if(modelBase.getInstallLocation().equals(installationLocation)) {
				String id = modelBase.getPluginBase().getId();
				if(id != null && id.length() > 0) { return id; }
			}
		}
		throw new FerretFatalError("cannot determine plugin id");
	}
	
	protected MultiMap<IPluginExtension,IPluginObject> loadExtensions(String pluginId, IPluginModelBase pmb) {
		MultiMap<IPluginExtension,IPluginObject> result =
			new MultiHashMap<IPluginExtension,IPluginObject>();
		if(pmb.getExtensions().getExtensions().length == 0) {
			// nothing to do, so don't bother
			return result;
		}
		try {
			InputStream stream = null;
			try {
		        String xmlFileName = pmb.isFragmentModel() ? "fragment.xml" : "plugin.xml";
		        if (pmb instanceof IBundlePluginModelBase
						&& ((IBundlePluginModelBase) pmb).getExtensionsModel() != null) {
					IFile file = (IFile) ((IBundlePluginModelBase)pmb).getExtensionsModel().getUnderlyingResource();
					stream = file.getContents();
				} else if (pmb.getInstallLocation() != null) {
					stream = openFile(pmb.getInstallLocation(), xmlFileName);
				} else {
					throw new FerretFatalError("not sure what to do now!");
				}
				if(stream == null) {
//					System.out.println(getPluginId(pmb) + ": " + xmlFileName + " not found");
					return result;
		        }

				// use ExternalPluginModel: it doesn't care whether we have a fragment or plugin
				ExternalPluginModelBase apmb = pmb.isFragmentModel() ? new ExternalFragmentModel()  
						: new ExternalPluginModel();
				apmb.setInstallLocation(pmb.getInstallLocation());
				// load using standard plugin handler w/o abbreviation
				apmb.load(stream, false, new PluginHandler(false));	// causes reset
//				apmb.load(pmb.getBundleDescription(), TargetPlatformHelper.getPDEState());
				apmb.setBundleDescription(pmb.getBundleDescription());
				IPluginExtension sources[] = pmb.getExtensions().getExtensions();
				IPluginExtension cooked[] = apmb.getExtensions().getExtensions();
				assert sources.length == cooked.length;
				for(int i = 0; i < sources.length; i++) {
					for(IPluginObject child : cooked[i].getChildren()) {
						result.put(sources[i], child);
					}
				}
			} catch (CoreException e) {
				FerretPlugin.log(e);
			} finally {
				if (stream != null) { stream.close(); }
			}
		} catch (IOException e) {
			FerretPlugin.log(e);
		}
		return result;
	}

	public  InputStream openFile(String container, String fileName) throws IOException {
		File containerLocation = new File(container);
		if(!containerLocation.exists()) { return null; }
		if(containerLocation.isDirectory()) {
			File foundFile = new File(containerLocation, fileName);
			return foundFile.exists() ? new FileInputStream(foundFile): null;
		}
		ZipFile file = new ZipFile(container);
		ZipEntry entry = file.getEntry(fileName);
		return entry != null ? file.getInputStream(entry) : null;
	}

	public Object getValidationContext() {
		return this;
	}

	public boolean isValid(Object context) {
		return context == this;
	}

	public Collection<IPluginModelBase> getDependents(IPluginModelBase pluginModel) {
		verifyModelCaches();
		String pluginId = getPluginId(pluginModel);
		LinkedList<IPluginModelBase> dependents = new LinkedList<IPluginModelBase>();
		for(IPluginModelBase pmb : models.values()) {
			IPluginImport[] imports = pmb.getPluginBase().getImports();
			for(IPluginImport dep : imports) {
				if(pluginId.equals(dep.getId())) {
					dependents.add(models.get(getPluginId(dep.getPluginModel())));
				}
			}
		}
		return dependents;
	}

	/**
	 * Generate an AbstractReference for the plugin.xml/fragment.xml
	 * @param pluginModel
	 * @return the reference
	 */
	public AbstractReference generateReference(ISharedPluginModel model) {
		try {
			String manifestName = model instanceof IFragmentModel ? "fragment.xml" : "plugin.xml";

			File location = new File(model.getInstallLocation());

			if(!location.exists()) { return null; }
			if(location.isDirectory()) {
				File foundFile = new File(location, manifestName);
				if(!foundFile.exists()) { return null; }
				IPath p = Path.fromOSString(foundFile.getPath());
				return new FileReference(p);
			}
			// Otherwise assume it's a jar file (XXX: perhaps we should assume it's a zip?) 
			// XXX: Trap ZipException?
			ZipFile file = new ZipFile(location);
			ZipEntry pluginxml = file.getEntry(manifestName);
			return pluginxml != null ? new ZipEntryReference(location.toString(), manifestName) : null;
		} catch(IOException e) {
			FerretPlugin.log(e);
			return null;
		}
	}

	public IDocument readFileContents(IFile file) {
		try {
			InputStream stream = file.getContents();
			try {
				return readFileContents(file.getContents(), file.getCharset());
			} finally {
					stream.close();
			}
		} catch(CoreException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
	}
	
	public IDocument readFileContents(InputStream stream, String charset) {
		IDocument document = new Document();
		try {
			InputStreamReader in = null;
			in = new InputStreamReader(new BufferedInputStream(stream), charset);
			StringBuffer buffer = new StringBuffer();
			char[] readBuffer = new char[8 * 1024];
			int n;
			while ((n = in.read(readBuffer)) > 0) {
				buffer.append(readBuffer, 0, n);
			}
			document.set(buffer.toString());
		} catch (IOException e) {
			/* do nothing */
		}
		return document;
	}

	public IPluginModelBase getDefiningModel(IPluginObject object) {
		IPluginModelBase definer = object.getPluginModel();
		if(definer == null) { return null; }
		return findPluginModel(getPluginId(definer));
	}

	public static String generateXPath(IPluginAttribute attr, IPluginExtension extension) {
			String notableAttributes[] = { "point", "id", "targetId" };
	//		IPluginObject current = attr;
	//		while(current != extension) {
	//			elements.add(current);
	//			current = current.getParent();
	//		}
	//		elements.add(extension);
	//		
			StringBuffer generatedName = new StringBuffer();
			IPluginObject parent = attr.getParent();
			if(parent instanceof IPluginElement) {
				IPluginElement e = (IPluginElement)parent;
				List<String> attributes = new LinkedList<String>();
				generatedName.append('<');
				generatedName.append(parent.getName());
				for(String notableAttribute : notableAttributes) {
					if(e.getAttribute(notableAttribute) != null) {
						attributes.add(notableAttribute + "='" + e.getAttribute(notableAttribute).getValue() + "'");
					}
				}
				if(!attributes.isEmpty()) {
					for (Iterator<String> iterator = attributes.iterator(); iterator.hasNext();) {
						generatedName.append(' ');
						generatedName.append(iterator.next());
					}
				}
				generatedName.append('>');
			}
		
			return generatedName.toString();
		}

}